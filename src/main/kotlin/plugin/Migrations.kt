package org.burgas.plugin

import io.ktor.server.application.*
import org.burgas.config.DatabaseFactory
import org.burgas.model.*
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureMigrations() {
    transaction(db = DatabaseFactory.postgres) {
        SchemaUtils.create(Identities, Gyms, Locations, Employees, LocationsEmployees)
    }
}
