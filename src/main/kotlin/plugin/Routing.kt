package org.burgas.plugin

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

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

@Serializable
data class UserSession(val token: String)

@Serializable
data class GithubUser(
    val login: String? = null,
    val name: String? = null,
    val email: String? = null
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
        cookie<UserSession>("USER_SESSION")
        cookie<GithubUser>("GITHUB_USER")
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
                var csrfToken: CsrfToken? = call.sessions.get()
                if (csrfToken != null) {
                    call.respond(HttpStatusCode.OK, csrfToken)
                } else {
                    csrfToken = CsrfToken(UUID.randomUUID())
                    call.sessions.set(csrfToken)
                    call.respond(HttpStatusCode.OK, csrfToken)
                }
            }

            authenticate("oauth-github-authentication") {

                get("/oauth/login") {
                    call.respondRedirect("https://github.com/login/oauth/authorize")
                }

                get("/oauth/callback") {
                    val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                    if (principal != null) {
                        call.sessions.set(UserSession(principal.accessToken))
                        call.respondRedirect("/api/v1/security/oauth/authorization")

                    } else {
                        call.respondRedirect("/api/v1/security/oauth/login")
                    }
                }
            }

            get("/oauth/authorization") {
                val userSession: UserSession? = call.sessions.get()
                val httpClient = HttpClient(CIO)

                if (userSession != null) {
                    val findName = call.sessions.findName(UserSession::class)
                    call.sessions.clear(findName)
                    val httpResponse = httpClient.get("https://api.github.com/user") {
                        header(HttpHeaders.Authorization, "token ${userSession.token}")
                    }
                    val json = Json {
                        ignoreUnknownKeys = true
                    }
                    val githubUser: GithubUser = json.decodeFromString(httpResponse.bodyAsText(Charsets.UTF_8))
                    call.sessions.set(githubUser)
                    call.respondText("Github user was set in session")

                } else {
                    call.respondRedirect("/api/v1/security/oauth/login")
                }
            }

            get("/oauth/logout") {
                val name = call.sessions.findName(UserSession::class)
                call.sessions.clear(name)
                call.respond("UserSession was cleared")
            }
        }
    }
}