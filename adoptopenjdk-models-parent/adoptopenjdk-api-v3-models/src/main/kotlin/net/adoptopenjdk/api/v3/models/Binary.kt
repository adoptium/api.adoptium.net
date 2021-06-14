package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema
class Binary {

    val os: OperatingSystem

    val architecture: Architecture

    val image_type: ImageType

    val jvm_impl: JvmImpl

    @Schema(implementation = Package::class)
    val `package`: Package

    @Schema(implementation = Installer::class)
    val installer: Installer?

    val heap_size: HeapSize

    @Schema(example = "3899")
    val download_count: Long

    val updated_at: DateTime

    @Schema(example = "dd28d6d2cde2b931caf94ac2422a2ad082ea62f0beee3bf7057317c53093de93")
    val scm_ref: String?

    val project: Project

    constructor(
        `package`: Package,
        download_count: Long,
        updated_at: DateTime,
        scm_ref: String?,
        installer: Installer?,
        heap_size: HeapSize,
        os: OperatingSystem,
        architecture: Architecture,
        image_type: ImageType,
        jvm_impl: JvmImpl,
        project: Project = Project.jdk
    ) {
        this.`package` = `package`
        this.download_count = download_count
        this.updated_at = updated_at
        this.scm_ref = scm_ref
        this.installer = installer
        this.heap_size = heap_size
        this.os = os
        this.architecture = architecture
        this.image_type = image_type
        this.jvm_impl = jvm_impl
        this.project = project
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Binary

        if (os != other.os) return false
        if (architecture != other.architecture) return false
        if (image_type != other.image_type) return false
        if (jvm_impl != other.jvm_impl) return false
        if (`package` != other.`package`) return false
        if (installer != other.installer) return false
        if (heap_size != other.heap_size) return false
        if (updated_at != other.updated_at) return false
        if (scm_ref != other.scm_ref) return false
        if (project != other.project) return false

        return true
    }

    override fun hashCode(): Int {
        var result = os.hashCode()
        result = 31 * result + architecture.hashCode()
        result = 31 * result + image_type.hashCode()
        result = 31 * result + jvm_impl.hashCode()
        result = 31 * result + `package`.hashCode()
        result = 31 * result + (installer?.hashCode() ?: 0)
        result = 31 * result + heap_size.hashCode()
        result = 31 * result + updated_at.hashCode()
        result = 31 * result + (scm_ref?.hashCode() ?: 0)
        result = 31 * result + project.hashCode()
        return result
    }

    override fun toString(): String {
        return "Binary(os=$os, architecture=$architecture, image_type=$image_type, jvm_impl=$jvm_impl, `package`=$`package`, installer=$installer, heap_size=$heap_size, download_count=$download_count, updated_at=$updated_at, scm_ref=$scm_ref, project=$project)"
    }
}
