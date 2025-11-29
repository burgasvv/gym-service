package org.burgas.model

import kotlinx.serialization.Serializable
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.datetime
import java.util.*

object Gyms : UUIDTable("gyms") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").uniqueIndex()
    val createdAt = datetime("create_at")
}

class Gym(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Gym>(Gyms)
    var name by Gyms.name
    var description by Gyms.description
    var createdAt by Gyms.createdAt
    val locations by Location referrersOn Locations.gym
}

@Serializable
data class GymRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null
)

@Serializable
data class GymShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null
)

@Serializable
data class GymFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val locations: List<LocationShortResponse>? = null
)