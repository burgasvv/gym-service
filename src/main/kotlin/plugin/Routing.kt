package org.burgas.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ExceptionMessage(
    val code: Int,
    val description: String,
    val cause: String
)

fun Application.configureRouting() {
//    install(StatusPages) {
//        exception<Throwable> { call, cause ->
//            val exceptionMessage = ExceptionMessage(
//                code = HttpStatusCode.BadRequest.value,
//                description = HttpStatusCode.BadRequest.description,
//                cause = cause.localizedMessage
//            )
//            call.respond(HttpStatusCode.BadRequest, exceptionMessage)
//        }
//    }
}
