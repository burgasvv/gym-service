package org.burgas.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.date
import java.util.*

@Suppress("unused")
enum class Position {
    DIRECTOR, MANAGER, SERVANT
}

object Employees : UUIDTable("employees") {
    val identity = reference(
        "identity_id",
        Identities.id,
        ReferenceOption.CASCADE,
        ReferenceOption.CASCADE
    ).uniqueIndex()
    val position = enumerationByName<Position>("position", 255)
    val birthday = date("birthday")
    val age = integer("age")
    val address = text("address")
}

class Employee(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Employee>(Employees)
    var identity by Identity referencedOn Employees.identity
    var position by Employees.position
    var birthday by Employees.birthday
    var age by Employees.age
    var address by Employees.address
    val locations by Location via LocationsEmployees
}

@Serializable
data class EmployeeRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val identityId: UUID? = null,
    val position: Position? = null,
    val birthday: LocalDate? = null,
    val address: String? = null
)

@Serializable
data class EmployeeNoIdentityResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val position: Position? = null,
    val birthday: String? = null,
    val age: Int? = null,
    val address: String? = null
)

@Serializable
data class EmployeeWithIdentityResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val identity: IdentityShortResponse? = null,
    val position: Position? = null,
    val birthday: String? = null,
    val age: Int? = null,
    val address: String? = null
)

@Serializable
data class EmployeeFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val identity: IdentityShortResponse? = null,
    val position: Position? = null,
    val birthday: String? = null,
    val age: Int? = null,
    val address: String? = null,
    val locations: List<LocationWithGymResponse>? = null
)