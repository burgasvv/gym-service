package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
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
import java.time.format.DateTimeFormatter
import java.util.*

fun Location.insert(locationRequest: LocationRequest) {
    this.gym = Gym.findById(locationRequest.gymId ?: throw IllegalArgumentException("Gym id is null"))
        ?: throw IllegalArgumentException("Gym not found")
    this.address = locationRequest.address ?: throw IllegalArgumentException("Address is null")
    this.open = (locationRequest.open ?: throw IllegalArgumentException("Open is null")).toJavaLocalTime()
    this.close = (locationRequest.close ?: throw IllegalArgumentException("Close is null")).toJavaLocalTime()
}

fun Location.update(locationRequest: LocationRequest) {
    this.gym = Gym.findById(locationRequest.gymId ?: UUID.randomUUID()) ?: this.gym
    this.address = locationRequest.address ?: this.address
    this.open = (locationRequest.open ?: this.open.toKotlinLocalTime()).toJavaLocalTime()
    this.close = (locationRequest.close ?: this.close.toKotlinLocalTime()).toJavaLocalTime()
}

fun Location.toLocationShortResponse(): LocationShortResponse {
    return LocationShortResponse(
        id = this.id.value,
        address = this.address,
        open = this.open.format(DateTimeFormatter.ofPattern("hh:mm")),
        close = this.close.format(DateTimeFormatter.ofPattern("hh:mm"))
    )
}

fun Location.toLocationFullResponse(): LocationFullResponse {
    return LocationFullResponse(
        id = this.id.value,
        gym = this.gym.toGymShortResponse(),
        address = this.address,
        open = this.open.format(DateTimeFormatter.ofPattern("hh:mm")),
        close = this.close.format(DateTimeFormatter.ofPattern("hh:mm")),
        employees = this.employees.map { employee -> employee.toEmployeeWithIdentityResponse() }
    )
}

fun Location.toLocationWithGymResponse(): LocationWithGymResponse {
    return LocationWithGymResponse(
        id = this.id.value,
        gym = this.gym.toGymShortResponse(),
        address = this.address,
        open = this.open.format(DateTimeFormatter.ofPattern("hh:mm")),
        close = this.close.format(DateTimeFormatter.ofPattern("hh:mm"))
    )
}

class LocationService {

    suspend fun create(locationRequest: LocationRequest) = withContext(Dispatchers.Default) {
        val gymId = locationRequest.gymId ?: throw IllegalArgumentException("Location gymId is null")
        val jedis = DatabaseFactory.jedis
        val gymFullResponseString = jedis.get("gymFullResponse:$gymId")
        if (gymFullResponseString != null) {
            jedis.del("gymFullResponse:$gymId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Location.new { insert(locationRequest) }
        }
    }

    suspend fun findById(locationId: UUID) = withContext(Dispatchers.Default) {
        val jedis = DatabaseFactory.jedis
        val locationFullResponse = jedis.get("locationFullResponse:$locationId")
        val json = Json { ignoreUnknownKeys = true }
        if (locationFullResponse != null) {
            json.decodeFromString<LocationFullResponse>(locationFullResponse)
        } else {
            transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
                val locationFullResponse = (Location.findById(locationId)
                    ?: throw IllegalArgumentException("Location not found")).toLocationFullResponse()
                val location = json.encodeToString(locationFullResponse)
                jedis.set("locationFullResponse:${locationFullResponse.id}", location)
                locationFullResponse
            }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            Location.all().map { location -> location.toLocationFullResponse() }
        }
    }

    suspend fun update(locationRequest: LocationRequest) = withContext(Dispatchers.Default) {
        val locationId = locationRequest.id ?: throw IllegalArgumentException("Location id is null")
        checkCacheOfGymAndLocations(locationId)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found"))
                .apply { update(locationRequest) }
        }
    }

    suspend fun delete(locationId: UUID) = withContext(Dispatchers.Default) {
        checkCacheOfGymAndLocations(locationId)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found")).delete()
        }
    }

    private fun checkCacheOfGymAndLocations(locationId: UUID) {
        val jedis = DatabaseFactory.jedis
        val locationFullResponse = jedis.get("locationFullResponse:$locationId")
        if (locationFullResponse != null) {
            jedis.del("locationFullResponse:$locationId")
        }
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            val gymLocation = Gyms.leftJoin(Locations, { Gyms.id }, { Locations.gym })
                .selectAll()
                .where { Locations.id eq locationId }
                .singleOrNull()
            if (gymLocation != null) {
                val key = "locationFullResponse:${gymLocation[Locations.id]}"
                val locationFullResponseString = jedis.get(key)

                if (locationFullResponseString != null) {
                    jedis.del(key)
                }
            }
        }
    }

    suspend fun addEmployees(locationId: UUID, employeeIds: List<UUID>) = withContext(Dispatchers.Default) {
        checkCacheOfLocationAndEmployees(locationId, employeeIds)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val location = Locations.selectAll().where { Locations.id eq locationId }.singleOrNull()
                ?: throw IllegalArgumentException("Location not found")
            val employees = Employees.selectAll().where { Employees.id inList employeeIds }.toList()
            employees.forEach { employee ->
                LocationsEmployees.insert { insertStatement ->
                    insertStatement[LocationsEmployees.location] = location[Locations.id]
                    insertStatement[LocationsEmployees.employee] = employee[Employees.id]
                }
            }
        }
    }

    suspend fun removeEmployees(locationId: UUID, employeeIds: List<UUID>) = withContext(Dispatchers.Default) {
        checkCacheOfLocationAndEmployees(locationId, employeeIds)
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val location = Locations.selectAll().where { Locations.id eq locationId }.singleOrNull()
                ?: throw IllegalArgumentException("Location not found")
            val employees = Employees.selectAll().where { Employees.id inList employeeIds }.toList()
            employees.forEach { employee ->
                LocationsEmployees.deleteWhere {
                    (LocationsEmployees.location eq location[Locations.id]) and (LocationsEmployees.employee eq employee[Employees.id])
                }
            }
        }
    }

    private fun checkCacheOfLocationAndEmployees(locationId: UUID, employeeIds: List<UUID>) {
        val jedis = DatabaseFactory.jedis
        val locationFullResponse = jedis.get("locationFullResponse:$locationId")
        if (locationFullResponse != null) {
            jedis.del("locationFullResponse:$locationId")
        }
        employeeIds.forEach { employeeId ->
            val employeeFullResponse = jedis.get("employeeFullResponse:$employeeId")
            if (employeeFullResponse != null) {
                jedis.del("employeeFullResponse:$employeeId")
            }
        }
    }
}

fun Application.configureLocationsRouter() {

    val locationService = LocationService()

    routing {

        route("/api/v1/locations") {

            get("/by-id") {
                val locationId = UUID.fromString(call.parameters["locationId"])
                call.respond(HttpStatusCode.OK, locationService.findById(locationId))
            }

            get {
                call.respond(HttpStatusCode.OK, locationService.findAll())
            }

            authenticate("basic-all-authenticated") {

                post("/create") {
                    val locationRequest = call.receive(LocationRequest::class)
                    locationService.create(locationRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update") {
                    val locationRequest = call.receive(LocationRequest::class)
                    locationService.update(locationRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val locationId = UUID.fromString(call.parameters["locationId"])
                    call.respond(HttpStatusCode.OK, locationService.delete(locationId))
                }

                post("/add-employees") {
                    val locationId = UUID.fromString(call.parameters["locationId"])
                    val employeeIds = call.parameters.getAll("employeeId")
                        ?.map { UUID.fromString(it) } ?: throw IllegalArgumentException("Employee ids is null")
                    locationService.addEmployees(locationId, employeeIds)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/remove-employees") {
                    val locationId = UUID.fromString(call.parameters["locationId"])
                    val employeeIds = call.parameters.getAll("employeeId")
                        ?.map { UUID.fromString(it) } ?: throw IllegalArgumentException("Employee ids is null")
                    locationService.removeEmployees(locationId, employeeIds)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}