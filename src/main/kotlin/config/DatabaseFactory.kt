package org.burgas.config

import org.jetbrains.exposed.v1.jdbc.Database

class DatabaseFactory {

    companion object {
        val postgres = Database.connect(
            driver = "org.postgresql.Driver",
            url = "jdbc:postgresql://localhost:6000/gym_service_db",
            user = "postgres",
            password = "postgres"
        )
    }
}