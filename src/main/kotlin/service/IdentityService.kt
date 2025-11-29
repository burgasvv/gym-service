package org.burgas.service

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.burgas.config.DatabaseFactory
import org.burgas.model.Identity
import org.burgas.model.IdentityRequest
import org.burgas.model.IdentityResponse
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.*

fun Identity.insert(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: throw IllegalArgumentException("Authority is null")
    this.email = identityRequest.email ?: throw IllegalArgumentException("Email is null")
    this.password = identityRequest.password ?: throw IllegalArgumentException("Password is null")
    this.firstname = identityRequest.firstname ?: throw IllegalArgumentException("Firstname is null")
    this.lastname = identityRequest.lastname ?: throw IllegalArgumentException("Lastname is null")
    this.patronymic = identityRequest.patronymic ?: throw IllegalArgumentException("Patronymic is null")
}

fun Identity.update(identityRequest: IdentityRequest) {
    this.authority = identityRequest.authority ?: this.authority
    this.email = identityRequest.email ?: this.email
    this.password = identityRequest.password ?: this.password
    this.firstname = identityRequest.firstname ?: this.firstname
    this.lastname = identityRequest.lastname ?: this.lastname
    this.patronymic = identityRequest.patronymic ?: this.patronymic
}

fun Identity.toIdentityResponse(): IdentityResponse {
    return IdentityResponse(
        id = this.id.value,
        authority = this.authority,
        email = this.email,
        firstname = this.firstname,
        lastname = this.lastname,
        patronymic = this.patronymic,
        isActive = this.isActive
    )
}

class IdentityService {

    suspend fun create(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Identity.new { insert(identityRequest) }
        }
    }

    suspend fun findById(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .toIdentityResponse()
        }
    }

    suspend fun findAll() = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, readOnly = true) {
            Identity.all().map { identity -> identity.toIdentityResponse() }.toHashSet()
        }
    }

    suspend fun update(identityRequest: IdentityRequest) = withContext(Dispatchers.Default) {
        val identityId = identityRequest.id ?: throw IllegalArgumentException("Identity id is null")
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .apply { update(identityRequest) }
        }
    }

    suspend fun delete(identityId: UUID) = withContext(Dispatchers.Default) {
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            (Identity.findById(identityId) ?: throw IllegalArgumentException("Identity not found"))
                .delete()
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

            get("/by-id") {
                val identityId = UUID.fromString(call.parameters["identityId"])
                call.respond(HttpStatusCode.OK, identityService.findById(identityId))
            }

            get {
                call.respond(HttpStatusCode.OK, identityService.findAll())
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
        }
    }
}