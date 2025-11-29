package org.burgas

import io.ktor.server.application.*
import org.burgas.plugin.configureMigrations
import org.burgas.plugin.configureRouting
import org.burgas.plugin.configureSerialization
import org.burgas.service.configureGymRouter
import org.burgas.service.configureIdentityRouter
import org.burgas.service.configureLocationsRouter

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureMigrations()
    configureRouting()

    configureIdentityRouter()
    configureGymRouter()
    configureLocationsRouter()
}
