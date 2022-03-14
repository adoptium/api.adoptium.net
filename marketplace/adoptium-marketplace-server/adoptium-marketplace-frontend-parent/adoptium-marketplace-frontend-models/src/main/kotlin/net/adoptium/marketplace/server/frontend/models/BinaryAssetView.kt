package net.adoptium.marketplace.server.frontend.models

import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.schema.OpenjdkVersionData
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema
class BinaryAssetView {

    @Schema(implementation = Binary::class)
    val binary: Binary

    @Schema(example = "jdk8u162-b12_openj9-0.8.0")
    val release_name: String

    @Schema(implementation = Vendor::class)
    val vendor: Vendor

    @Schema(implementation = OpenjdkVersionData::class)
    val version: OpenjdkVersionData

    constructor(
        release_name: String,
        vendor: Vendor,
        binary: Binary,
        version: OpenjdkVersionData
    ) {
        this.release_name = release_name
        this.vendor = vendor
        this.binary = binary
        this.version = version
    }
}
