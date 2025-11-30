package org.burgas

import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.burgas.config.DatabaseFactory
import org.burgas.model.Employee
import org.burgas.plugin.configureMigrations
import org.burgas.plugin.configureRouting
import org.burgas.plugin.configureSerialization
import org.burgas.service.configureEmployeeRouter
import org.burgas.service.configureGymRouter
import org.burgas.service.configureIdentityRouter
import org.burgas.service.configureLocationsRouter
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import kotlin.time.toKotlinDuration

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
    configureEmployeeRouter()

    launch {
        delay(Duration.ofMinutes(10).toKotlinDuration())
        transaction(db = DatabaseFactory.postgres, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
            Employee.all().forEach { employee ->
                val realAge = Period.between(employee.birthday, LocalDate.now()).years
                if (employee.age != realAge) employee.age = realAge
            }
        }
    }
}
