package net.adoptium.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema
class BinaryAssetView {

    @Schema(implementation = Binary::class)
    val binary: Binary

    @Schema(example = "jdk8u162-b12_openj9-0.8.0")
    val release_name: String

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/tag/jdk8u162-b12_openj9-0.8.0")
    val release_link: String

    @Schema(implementation = Vendor::class)
    val vendor: Vendor

    @Schema(implementation = VersionData::class)
    val version: VersionData

    constructor(
        release_name: String,
        vendor: Vendor,
        binary: Binary,
        version: VersionData,
        release_link: String
    ) {
        this.release_name = release_name
        this.vendor = vendor
        this.binary = binary
        this.version = version
        this.release_link = release_link
    }
}
