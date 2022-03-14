package net.adoptium.marketplace.server.frontend.models

import net.adoptium.marketplace.schema.OpenjdkVersionData
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

class ReleaseVersionList {

    @Schema(type = SchemaType.ARRAY, implementation = OpenjdkVersionData::class)
    val versions: Array<OpenjdkVersionData>

    constructor(versions: Array<OpenjdkVersionData>) {
        this.versions = versions
    }
}
