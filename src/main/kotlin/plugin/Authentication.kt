package org.burgas.plugin

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.burgas.config.DatabaseFactory
import org.burgas.model.Authority
import org.burgas.model.Identities
import org.burgas.model.Identity
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mindrot.jbcrypt.BCrypt

fun Application.configureAuthentication() {

    authentication {

        oauth("oauth-github-authentication") {
            client = HttpClient(CIO)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "github",
                    authorizeUrl = "https://github.com/login/oauth/authorize",
                    accessTokenUrl = "https://github.com/login/oauth/access_token",
                    requestMethod = HttpMethod.Post,
                    clientId = "Ov23lisKO3HqyMyGSah0",
                    clientSecret = "5a32e2687e55a9b3ab15282e94ab56fc89ebc2f3",
                    defaultScopes = listOf("user:email", "read:user"),
                    passParamsInURL = true
                )
            }
            urlProvider = { "http://localhost:9000/api/v1/security/oauth/callback" }
        }

        basic(name = "basic-all-authenticated") {
            validate { credentials ->
                transaction(db = DatabaseFactory.postgres, readOnly = true) {
                    val identity = Identity.find { Identities.email eq credentials.name }.singleOrNull()
                    if (
                        identity != null &&
                        BCrypt.checkpw(credentials.password, identity.password) &&
                        identity.isActive
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)
                    } else {
                        null
                    }
                }
            }
        }

        basic(name = "basic-admin-authenticated") {
            validate { credentials ->
                transaction(db = DatabaseFactory.postgres, readOnly = true) {
                    val identity = Identity.find { Identities.email eq credentials.name }.singleOrNull()
                    if (
                        identity != null &&
                        BCrypt.checkpw(credentials.password, identity.password) &&
                        identity.authority == Authority.ADMIN &&
                        identity.isActive
                    ) {
                        UserPasswordCredential(credentials.name, credentials.password)
                    } else {
                        null
                    }
                }

            }
        }
    }
}