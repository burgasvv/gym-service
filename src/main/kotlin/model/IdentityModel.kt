package org.burgas.model

import kotlinx.serialization.Serializable
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import java.util.UUID

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object Identities : UUIDTable("identities") {
    val authority = enumerationByName<Authority>("authority", 255)
    val email = varchar("email", 255).uniqueIndex()
    val password = varchar("password", 255)
    val firstname = varchar("firstname", 255)
    val lastname = varchar("lastname", 255)
    val patronymic = varchar("patronymic", 255)
    val isActive = bool("is_active").default(true)
}

class Identity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Identity>(Identities)
    var authority by Identities.authority
    var email by Identities.email
    var password by Identities.password
    var firstname by Identities.firstname
    var lastname by Identities.lastname
    var patronymic by Identities.patronymic
    var isActive by Identities.isActive
    val employee by Employee referrersOn Employees.identity
}

@Serializable
data class IdentityRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val password: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class IdentityShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class IdentityFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val authority: Authority? = null,
    val email: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val patronymic: String? = null,
    val isActive: Boolean? = null,
    val employee: EmployeeNoIdentityResponse? = null
)