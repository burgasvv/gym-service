package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.burgas.config.DatabaseFactory
import org.burgas.model.Gym
import org.burgas.model.GymFullResponse
import org.burgas.model.GymRequest
import org.burgas.model.GymShortResponse
import org.burgas.plugin.GithubUser
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun Gym.insert(gymRequest: GymRequest) {
    this.name = gymRequest.name ?: throw IllegalArgumentException("Name is null")
    this.description = gymRequest.description ?: throw IllegalArgumentException("Description is null")
    this.createdAt = LocalDateTime.now()
}

fun Gym.update(gymRequest: GymRequest) {
    this.name = gymRequest.name ?: this.name
    this.description = gymRequest.description ?: this.description
}

fun Gym.toGymShortResponse(): GymShortResponse {
    return GymShortResponse(
        id = this.id.value,
        name = this.name,
        description = this.description,
        createdAt = this.createdAt.format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm"))
    )
}

fun Gym.toGymFullResponse(): GymFullResponse {
    return GymFullResponse(
        id = this.id.value,
        name = this.name,
        description = this.description,
        createdAt = this.createdAt.format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm")),
        locations = this.locations.map { location -> location.toLocationShortResponse() }
    )
}

class GymService {

    suspend fun create(gymRequest: GymRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Gym.new { insert(gymRequest) }
        }
    }

    suspend fun findById(gymId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            (Gym.findById(gymId) ?: throw IllegalArgumentException("Gym not found")).toGymFullResponse()
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            Gym.all().map { gym -> gym.toGymShortResponse() }.toHashSet()
        }
    }

    suspend fun update(gymRequest: GymRequest) = withContext(Dispatchers.Default) {
        val gymId = gymRequest.id ?: throw IllegalArgumentException("Gym id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Gym.findById(gymId) ?: throw IllegalArgumentException("Gym not found"))
                .apply { update(gymRequest) }
        }
    }

    suspend fun delete(gymId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Gym.findById(gymId) ?: throw IllegalArgumentException("Gym not found")).delete()
        }
    }
}

fun Application.configureGymRouter() {

    val gymService = GymService()

    routing {

        route("/api/v1/gyms") {

            get {
                val githubUser: GithubUser? = call.sessions.get()
                if (githubUser != null) {
                    call.respond(HttpStatusCode.OK, gymService.findAll())
                } else {
                    call.respondRedirect("/api/v1/security/oauth/login")
                }
            }

            get("/by-id") {
                val githubUser: GithubUser? = call.sessions.get()
                if (githubUser != null) {
                    val gymId = UUID.fromString(call.parameters["gymId"])
                    call.respond(HttpStatusCode.OK, gymService.findById(gymId))
                } else {
                    call.respondRedirect("/api/v1/security/oauth/login")
                }
            }

            authenticate("basic-all-authenticated") {

                post("/create") {
                    val gymRequest = call.receive(GymRequest::class)
                    gymService.create(gymRequest)
                    call.respond(HttpStatusCode.Created)
                }

                put("/update") {
                    val gymRequest = call.receive(GymRequest::class)
                    gymService.update(gymRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val gymId = UUID.fromString(call.parameters["gymId"])
                    gymService.delete(gymId)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}