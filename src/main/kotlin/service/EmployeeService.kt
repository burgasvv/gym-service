package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.burgas.config.DatabaseFactory
import org.burgas.model.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*

fun Employee.create(employeeRequest: EmployeeRequest) {
    this.identity = Identity.findById(
        employeeRequest.identityId ?: throw IllegalArgumentException("Identity id is null")
    )
        ?: throw IllegalArgumentException("Identity not found")
    this.position = employeeRequest.position ?: throw IllegalArgumentException("Position is null")
    this.birthday = (employeeRequest.birthday ?: throw IllegalArgumentException("Birthday is null")).toJavaLocalDate()
    this.age = Period.between(this.birthday, LocalDate.now()).years
    this.address = employeeRequest.address ?: throw IllegalArgumentException("Address is null")
}

fun Employee.update(employeeRequest: EmployeeRequest) {
    this.identity = Identity.findById(employeeRequest.identityId ?: UUID.randomUUID()) ?: this.identity
    this.position = employeeRequest.position ?: this.position
    this.birthday = (employeeRequest.birthday ?: this.birthday.toKotlinLocalDate()).toJavaLocalDate()
    this.address = employeeRequest.address ?: this.address
}

fun Employee.toEmployeeFullResponse(): EmployeeFullResponse {
    return EmployeeFullResponse(
        id = this.id.value,
        identity = this.identity.toIdentityShortResponse(),
        position = this.position,
        birthday = this.birthday.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
        age = this.age,
        address = this.address,
        locations = this.locations.map { location -> location.toLocationWithGymResponse() }
    )
}

fun Employee.toEmployeeNoIdentityResponse(): EmployeeNoIdentityResponse {
    return EmployeeNoIdentityResponse(
        id = this.id.value,
        position = this.position,
        birthday = this.birthday.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
        age = this.age,
        address = this.address
    )
}

fun Employee.toEmployeeWithIdentityResponse(): EmployeeWithIdentityResponse {
    return EmployeeWithIdentityResponse(
        id = this.id.value,
        identity = this.identity.toIdentityShortResponse(),
        position = this.position,
        birthday = this.birthday.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
        age = this.age,
        address = this.address
    )
}

class EmployeeService {

    suspend fun create(employeeRequest: EmployeeRequest) = withContext(Dispatchers.Default) {
        val identityId = employeeRequest.identityId ?: throw IllegalArgumentException("Employee Identity id is null")
        val jedis = DatabaseFactory.jedis
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            jedis.del("identityFullResponse:$identityId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Employee.new { create(employeeRequest) }
        }
    }

    suspend fun findByLocation(locationId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found"))
                .employees.map { employee -> employee.toEmployeeWithIdentityResponse() }
        }
    }

    suspend fun findById(employeeId: UUID) = withContext(Dispatchers.Default) {
        val jedis = DatabaseFactory.jedis
        val json = Json { ignoreUnknownKeys = true }
        val employeeFullResponseString = jedis.get("employeeFullResponse:$employeeId")
        if (employeeFullResponseString != null) {
            json.decodeFromString<EmployeeFullResponse>(employeeFullResponseString)
        } else {
            transaction(db = DatabaseFactory.postgres, readOnly = true) {
                val employeeFullResponse =
                    (Employee.findById(employeeId)
                        ?: throw IllegalArgumentException("Employee not found")).toEmployeeFullResponse()
                val employeeFullResponseString = json.encodeToString(employeeFullResponse)
                jedis.set("employeeFullResponse:${employeeFullResponse.id}", employeeFullResponseString)
                employeeFullResponse
            }
        }
    }

    suspend fun update(employeeRequest: EmployeeRequest) = withContext(Dispatchers.Default) {
        val employeeId = employeeRequest.id ?: throw IllegalArgumentException("Employee id is null")
        checkCacheOfIdentityAndEmployee(employeeId)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found"))
                .apply { update(employeeRequest) }
        }
    }

    suspend fun delete(employeeId: UUID) = withContext(Dispatchers.Default) {
        checkCacheOfIdentityAndEmployee(employeeId)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found")).delete()
        }
    }

    private fun checkCacheOfIdentityAndEmployee(employeeId: UUID) {
        val jedis = DatabaseFactory.jedis
        val employeeFullResponseString = jedis.get("employeeFullResponse:$employeeId")
        if (employeeFullResponseString != null) {
            jedis.del("employeeFullResponse:$employeeId")
        }
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            val identityEmployee = Identities
                .leftJoin(Employees, { Identities.id }, { Employees.identity })
                .selectAll()
                .where { Employees.id eq employeeId }
                .singleOrNull()
            if (identityEmployee != null) {
                val key = "identityFullResponse:${identityEmployee[Identities.id]}"
                val identityFullResponseString = jedis.get(key)

                if (identityFullResponseString != null) {
                    jedis.del(key)
                }
            }
        }
    }

    suspend fun addLocations(employeeId: UUID, locationsIds: List<UUID>) = withContext(Dispatchers.Default) {
        checkCacheOfEmployeeAndLocations(employeeId, locationsIds)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val employee = Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found")
            val locations = Location.find { Locations.id inList locationsIds }.toList()
            locations.forEach { location ->
                LocationsEmployees.insert { insertStatement ->
                    insertStatement[LocationsEmployees.location] = location.id
                    insertStatement[LocationsEmployees.employee] = employee.id
                }
            }
        }
    }

    suspend fun removeLocations(employeeId: UUID, locationsIds: List<UUID>) = withContext(Dispatchers.Default) {
        checkCacheOfEmployeeAndLocations(employeeId, locationsIds)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val employee = Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found")
            val locations = Location.find { Locations.id inList locationsIds }.toList()
            locations.forEach { location ->
                LocationsEmployees.deleteWhere {
                    (LocationsEmployees.location eq location.id) and (LocationsEmployees.employee eq employee.id)
                }
            }
        }
    }

    private fun checkCacheOfEmployeeAndLocations(employeeId: UUID, locationsIds: List<UUID>) {
        val jedis = DatabaseFactory.jedis
        val employeeFullResponseString = jedis.get("employeeFullResponse:$employeeId")
        if (employeeFullResponseString != null) {
            jedis.del("employeeFullResponse:$employeeId")
        }
        locationsIds.forEach { locationId ->
            val locationFullResponse = jedis.get("locationFullResponse:$locationId")
            if (locationFullResponse != null) {
                jedis.del("locationFullResponse:$locationId")
            }
        }
    }
}

fun Application.configureEmployeeRouter() {

    val employeeService = EmployeeService()

    routing {

        @Suppress("DEPRECATION")
        intercept(ApplicationCallPipeline.Call) {
            if (
                call.request.path().equals("/api/v1/employees/by-id", false) ||
                call.request.path().equals("/api/v1/employees/delete", false) ||
                call.request.path().equals("/api/v1/employees/add-locations", false) ||
                call.request.path().equals("/api/v1/employees/remove-locations", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Identity principal is null")
                val employeeId = UUID.fromString(call.parameters["employeeId"])

                val employeeIdentity = transaction(db = DatabaseFactory.postgres, readOnly = true) {
                    Employees
                        .leftJoin(Identities, { Employees.identity }, { Identities.id })
                        .selectAll()
                        .where { Identities.email eq principal.name }
                        .singleOrNull() ?: throw IllegalArgumentException("Employee Identity not found")
                }
                if (employeeId == employeeIdentity[Employees.id]) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity-Employee not authorized")
                }

            } else if (
                call.request.path().equals("/api/v1/employees/create", false) ||
                call.request.path().equals("/api/v1/employees/update", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Identity principal is null")
                val employeeRequest = call.receive(EmployeeRequest::class)
                val identityId = employeeRequest.identityId ?: throw IllegalArgumentException("Identity id is null")

                val identity = transaction(db = DatabaseFactory.postgres, readOnly = true) {
                    Identity.find { Identities.email eq principal.name }.singleOrNull()
                        ?: throw IllegalArgumentException("Identity not authenticated")
                }
                if (identity.id == identityId) {
                    call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")] = employeeRequest
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }

            } else if (
                call.request.path().equals("/api/v1/employees/by-location", false)
            ) {
                val principal = call.principal<UserPasswordCredential>()
                    ?: throw IllegalArgumentException("Identity principal is null")
                val locationId = UUID.fromString(call.parameters["locationId"])
                val locationEmployee = transaction(db = DatabaseFactory.postgres, readOnly = true) {
                    Locations
                        .leftJoin(LocationsEmployees, { Locations.id }, { LocationsEmployees.location })
                        .leftJoin(Employees, { Employees.id }, { LocationsEmployees.employee })
                        .leftJoin(Identities, { Identities.id }, { Employees.identity })
                        .selectAll()
                        .where { Locations.id eq locationId }
                        .singleOrNull() ?: throw IllegalArgumentException("Not authenticated with that location")
                }
                if (locationEmployee[Identities.email] == principal.name) {
                    proceed()
                } else {
                    throw IllegalArgumentException("Identity not authorized")
                }
            }
            proceed()
        }

        route("/api/v1/employees") {

            authenticate("basic-all-authenticated") {

                post("/create") {
                    val employeeRequest = call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")]
                    employeeService.create(employeeRequest)
                    call.respond(HttpStatusCode.Created)
                }

                get("/by-location") {
                    val locationId = UUID.fromString(call.parameters["locationId"])
                    call.respond(HttpStatusCode.OK, employeeService.findByLocation(locationId))
                }

                get("/by-id") {
                    val employeeId = UUID.fromString(call.parameters["employeeId"])
                    call.respond(HttpStatusCode.OK, employeeService.findById(employeeId))
                }

                put("/update") {
                    val employeeRequest = call.attributes[AttributeKey<EmployeeRequest>("employeeRequest")]
                    employeeService.update(employeeRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val employeeId = UUID.fromString(call.parameters["employeeId"])
                    employeeService.delete(employeeId)
                    call.respond(HttpStatusCode.OK)
                }

                post("/add-locations") {
                    val employeeId = UUID.fromString(call.parameters["employeeId"])
                    val locationIds = (call.parameters.getAll("locationId")?.map { UUID.fromString(it) }
                        ?: throw IllegalArgumentException("Location ids is null"))
                    employeeService.addLocations(employeeId, locationIds)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-locations") {
                    val employeeId = UUID.fromString(call.parameters["employeeId"])
                    val locationIds = (call.parameters.getAll("locationId")?.map { UUID.fromString(it) }
                        ?: throw IllegalArgumentException("Location ids is null"))
                    employeeService.removeLocations(employeeId, locationIds)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}