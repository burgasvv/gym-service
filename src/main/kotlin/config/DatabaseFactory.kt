package org.burgas.config

import io.ktor.server.config.*
import org.jetbrains.exposed.v1.jdbc.Database
import redis.clients.jedis.Jedis

class DatabaseFactory {

    companion object {

        val applicationConfig = ApplicationConfig("application.yaml")

        val postgres = Database.connect(
            driver = applicationConfig.property("ktor.postgres.driver").getString(),
            url = applicationConfig.property("ktor.postgres.url").getString(),
            user = applicationConfig.property("ktor.postgres.user").getString(),
            password = applicationConfig.property("ktor.postgres.password").getString()
        )

        val jedis: Jedis = Jedis("localhost", 6379)
    }
}