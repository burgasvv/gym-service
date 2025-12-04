package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.burgas.config.DatabaseFactory
import org.burgas.model.Identity
import org.burgas.model.IdentityFullResponse
import org.burgas.model.IdentityRequest
import org.burgas.model.IdentityShortResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.util.*

fun Identity.insert(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: throw IllegalArgumentException("Authority is null")
    this.email = identityRequest.email ?: throw IllegalArgumentException("Email is null")
    this.password = BCrypt.hashpw(
        identityRequest.password ?: throw IllegalArgumentException("Password is null"),
        BCrypt.gensalt()
    )
    this.firstname = identityRequest.firstname ?: throw IllegalArgumentException("Firstname is null")
    this.lastname = identityRequest.lastname ?: throw IllegalArgumentException("Lastname is null")
    this.patronymic = identityRequest.patronymic ?: throw IllegalArgumentException("Patronymic is null")
}

fun Identity.update(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: this.authority
    this.email = identityRequest.email ?: this.email
    this.firstname = identityRequest.firstname ?: this.firstname
    this.lastname = identityRequest.lastname ?: this.lastname
    this.patronymic = identityRequest.patronymic ?: this.patronymic
}

fun Identity.toIdentityShortResponse(): IdentityShortResponse {
    return IdentityShortResponse(
        id = this.id.value,
        authority = this.authority,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        isActive = this.isActive
    )
}

fun Identity.toIdentityFullResponse(): IdentityFullResponse {
    return IdentityFullResponse(
        id = this.id.value,
        authority = this.authority,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        isActive = this.isActive,
        employee = this.employee.singleOrNull()?.toEmployeeNoIdentityResponse()
    )
}

class IdentityService {

    suspend fun create(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Identity.new { insert(identityRequest) }
        }
    }

    suspend fun findById(identityId: UUID) = withContext(Dispatchers.Default) {
        val jedis = DatabaseFactory.jedis
        val json = Json { ignoreUnknownKeys = true }
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            json.decodeFromString<IdentityFullResponse>(identityFullResponseString)
        } else {
            transaction(db = DatabaseFactory.postgres, readOnly = true) {
                val identityFullResponse =
                    (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                        .toIdentityFullResponse()
                val identityFullResponseString = json.encodeToString(identityFullResponse)
                jedis.set("identityFullResponse:${identityFullResponse.id}", identityFullResponseString)
                identityFullResponse
            }
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            Identity.all().map { identity -> identity.toIdentityShortResponse() }
        }
    }

    suspend fun update(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity id is null")
        val jedis = DatabaseFactory.jedis
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            jedis.del("identityFullResponse:$identityId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .apply { update(identityRequest) }
        }
    }

    suspend fun delete(identityId: UUID) = withContext(Dispatchers.Default) {
        val jedis = DatabaseFactory.jedis
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            jedis.del("identityFullResponse:$identityId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .delete()
        }
    }

    suspend fun changePassword(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity id is null")
        val newPassword = identityRequest.password ?: throw IllegalArgumentException("Identity password is null")
        val jedis = DatabaseFactory.jedis
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            jedis.del("identityFullResponse:$identityId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
            if (BCrypt.checkpw(newPassword, identity.password)) {
                throw IllegalArgumentException("Passwords matched")
            }
            identity.apply {
                this.password = BCrypt.hashpw(newPassword, BCrypt.gensalt())
            }
        }
    }

    suspend fun changeStatus(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity id is null")
        val status = identityRequest.isActive ?: throw IllegalArgumentException("Is active is null")
        val jedis = DatabaseFactory.jedis
        val identityFullResponseString = jedis.get("identityFullResponse:$identityId")
        if (identityFullResponseString != null) {
            jedis.del("identityFullResponse:$identityId")
        }
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            val identity = Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found")
            if (identity.isActive == status) {
                throw IllegalArgumentException("Identity status matched")
            }
            identity.apply {
                this.isActive = status
            }
        }
    }
}

fun Application.configureIdentityRouter() {

    val identityService = IdentityService()

    routing {

        route("/api/v1/identities") {

            post("/create") {
                val identityRequest = call.receive(IdentityRequest::class)
                identityService.create(identityRequest)
                call.respond(HttpStatusCode.Created)
            }

            authenticate("basic-admin-authenticated") {

                get {
                    call.respond(HttpStatusCode.OK, identityService.findAll())
                }

                put("/change-status") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changeStatus(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }

            authenticate("basic-all-authenticated") {

                get("/by-id") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    call.respond(HttpStatusCode.OK, identityService.findById(identityId))
                }


                put("/update") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.update(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/delete") {
                    val identityId = UUID.fromString(call.parameters["identityId"])
                    identityService.delete(identityId)
                    call.respond(HttpStatusCode.OK)
                }

                put("/change-password") {
                    val identityRequest = call.receive(IdentityRequest::class)
                    identityService.changePassword(identityRequest)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}