package net.adoptium.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

class SourcePackage(
    name: String,
    link: String,
    size: Long) : FileAsset(name, link, size)

class ReleaseNotesPackage(
    name: String,
    link: String,
    size: Long) : FileAsset(name, link, size)

open class FileAsset {

    @Schema(example = "OpenJDK8U-sources_8u232b09.tar.gz")
    val name: String

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-upstream-binaries/releases/download/jdk8u232-b09/OpenJDK8U-sources_8u232b09.tar.gz")
    val link: String

    @Schema(example = "82573385")
    val size: Long

    constructor(
        name: String,
        link: String,
        size: Long
    ) {
        this.name = name
        this.link = link
        this.size = size
    }

    override fun toString(): String {
        return "FileAsset(name='$name', link='$link', size=$size)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileAsset

        if (name != other.name) return false
        if (link != other.link) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}
