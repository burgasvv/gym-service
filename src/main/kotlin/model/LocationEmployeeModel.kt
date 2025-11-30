package org.burgas.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object LocationsEmployees : Table("locations_employees") {
    val location = reference(
        "location_id",
        Locations.id,
        ReferenceOption.CASCADE, ReferenceOption.CASCADE
    )
    val employee = reference(
        "employee_id",
        Employees.id,
        ReferenceOption.CASCADE, ReferenceOption.CASCADE
    )

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(location, employee))
}