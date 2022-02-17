package net.adoptium.marketplace.server.frontend.models

import net.adoptium.marketplace.schema.VersionData
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

class ReleaseVersionList {

    @Schema(type = SchemaType.ARRAY, implementation = VersionData::class)
    val versions: Array<VersionData>

    constructor(versions: Array<VersionData>) {
        this.versions = versions
    }
}
