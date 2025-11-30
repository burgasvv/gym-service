package org.burgas.model

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.burgas.plugin.UUIDSerialization
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.jetbrains.exposed.v1.javatime.time
import java.util.*

object Locations : UUIDTable("locations") {
    val gym = reference("gym_id", Gyms.id, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val address = text("address")
    val open = time("open")
    val close = time("close")
}

class Location(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Location>(Locations)
    var gym by Gym referencedOn Locations.gym
    var address by Locations.address
    var open by Locations.open
    var close by Locations.close
    val employees by Employee via LocationsEmployees
}

@Serializable
data class LocationRequest(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    @Serializable(with = UUIDSerialization::class)
    val gymId: UUID? = null,
    val address: String? = null,
    val open: LocalTime? = null,
    val close: LocalTime? = null
)

@Serializable
data class LocationShortResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val address: String? = null,
    val open: String? = null,
    val close: String? = null
)

@Serializable
data class LocationWithGymResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val gym: GymShortResponse? = null,
    val address: String? = null,
    val open: String? = null,
    val close: String? = null
)

@Serializable
data class LocationFullResponse(
    @Serializable(with = UUIDSerialization::class)
    val id: UUID? = null,
    val gym: GymShortResponse? = null,
    val address: String? = null,
    val open: String? = null,
    val close: String? = null,
    val employees: List<EmployeeWithIdentityResponse>? = null
)