package org.burgas.config

import org.jetbrains.exposed.v1.jdbc.Database
import redis.clients.jedis.Jedis

class DatabaseFactory {

    companion object {
        val postgres = Database.connect(
            driver = "org.postgresql.Driver",
            url = "jdbc:postgresql://localhost:6000/gym_service_db",
            user = "postgres",
            password = "postgres"
        )

        val jedis: Jedis = Jedis("localhost", 6379)
    }
}