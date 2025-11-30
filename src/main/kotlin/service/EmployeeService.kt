package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.burgas.config.DatabaseFactory
import org.burgas.model.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*

fun Employee.create(employeeRequest: EmployeeRequest) {
    this.identity = Identity.findById(employeeRequest.identityId ?: throw IllegalArgumentException("Identity id is null"))
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
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            (Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found"))
                .toEmployeeFullResponse()
        }
    }

    suspend fun update(employeeRequest: EmployeeRequest) = withContext(Dispatchers.Default) {
        val employeeId = employeeRequest.id ?: throw IllegalArgumentException("Employee id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found"))
                .apply { update(employeeRequest) }
        }
    }

    suspend fun delete(employeeId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Employee.findById(employeeId) ?: throw IllegalArgumentException("Employee not found")).delete()
        }
    }
}

fun Application.configureEmployeeRouter() {

    val employeeService = EmployeeService()

    routing {

        route("/api/v1/employees") {

            post("/create") {
                val employeeRequest = call.receive(EmployeeRequest::class)
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
                val employeeRequest = call.receive(EmployeeRequest::class)
                employeeService.update(employeeRequest)
                call.respond(HttpStatusCode.OK)
            }

            delete("/delete") {
                val employeeId = UUID.fromString(call.parameters["employeeId"])
                employeeService.delete(employeeId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}