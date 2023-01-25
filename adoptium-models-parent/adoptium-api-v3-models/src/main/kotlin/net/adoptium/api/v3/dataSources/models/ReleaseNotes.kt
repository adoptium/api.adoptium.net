package net.adoptium.api.v3.dataSources.models

import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData

class ReleaseNote(
    val id: String,
    val title: String?,
    val priority: String?,
    val component: String?,
    val subcomponent: String?,
    val link: String?,
    val type: String?,
    val backportOf: String?,
)

class ReleaseNotes(
    val version_data: VersionData,
    val vendor: Vendor,
    val id: String,
    val release_name: String,
    val release_notes: List<ReleaseNote>
)
