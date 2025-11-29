package org.burgas.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalTime
import org.burgas.config.DatabaseFactory
import org.burgas.model.Gym
import org.burgas.model.Location
import org.burgas.model.LocationRequest
import org.burgas.model.LocationFullResponse
import org.burgas.model.LocationShortResponse
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
        close = this.close.format(DateTimeFormatter.ofPattern("hh:mm"))
    )
}

class LocationService {

    suspend fun create(locationRequest: LocationRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Location.new { insert(locationRequest) }
        }
    }

    suspend fun findById(locationId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found")).toLocationFullResponse()
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            Location.all().map { location -> location.toLocationFullResponse() }
        }
    }

    suspend fun update(locationRequest: LocationRequest) = withContext(Dispatchers.Default) {
        val locationId = locationRequest.id ?: throw IllegalArgumentException("Location id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found"))
                .apply { update(locationRequest) }
        }
    }

    suspend fun delete(locationId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Location.findById(locationId) ?: throw IllegalArgumentException("Location not found")).delete()
        }
    }
}

fun Application.configureLocationsRouter() {

    val locationService = LocationService()

    routing {

        route("/api/v1/locations") {

            post("/create") {
                val locationRequest = call.receive(LocationRequest::class)
                locationService.create(locationRequest)
                call.respond(HttpStatusCode.Created)
            }

            get("/by-id") {
                val locationId = UUID.fromString(call.parameters["locationId"])
                call.respond(HttpStatusCode.OK, locationService.findById(locationId))
            }

            get {
                call.respond(HttpStatusCode.OK, locationService.findAll())
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
        }
    }
}