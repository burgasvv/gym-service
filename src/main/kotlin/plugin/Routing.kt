package org.burgas.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ExceptionMessage(
    val code: Int,
    val description: String,
    val cause: String
)

@Serializable
data class CsrfToken(
    @Serializable(with = UUIDSerialization::class)
    val token: UUID
)

fun Application.configureRouting() {

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val exceptionMessage = ExceptionMessage(
                code = HttpStatusCode.BadRequest.value,
                description = HttpStatusCode.BadRequest.description,
                cause = cause.localizedMessage
            )
            call.respond(HttpStatusCode.BadRequest, exceptionMessage)
        }
    }

    install(Sessions) {
        cookie<CsrfToken>("MY_CSRF_TOKEN")
    }

    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowOrigins { string -> string.equals("http://localhost:4200", false) }
    }

    routing {

        route("/api/v1/security") {

            get("/csrf-token") {
                val csrfToken = CsrfToken(UUID.randomUUID())
                call.sessions.set(csrfToken)
                call.respond(HttpStatusCode.OK, csrfToken)
            }
        }
    }
}
