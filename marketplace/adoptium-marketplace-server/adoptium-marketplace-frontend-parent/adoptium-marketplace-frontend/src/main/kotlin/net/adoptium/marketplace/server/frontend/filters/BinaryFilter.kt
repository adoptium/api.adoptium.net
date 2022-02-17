package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.marketplace.schema.Architecture
import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.CLib
import net.adoptium.marketplace.schema.ImageType
import net.adoptium.marketplace.schema.JvmImpl
import net.adoptium.marketplace.schema.OperatingSystem
import net.adoptium.marketplace.schema.Project
import net.adoptium.marketplace.server.frontend.models.DateTime
import java.util.function.Predicate

class BinaryFilter : Predicate<Binary> {

    private var cLib: CLib?
    private val os: OperatingSystem?
    private val arch: Architecture?
    private val imageType: ImageType?
    private val jvmImpl: JvmImpl?
    private val project: Project
    private val before: DateTime?

    constructor(
        os: OperatingSystem? = null,
        arch: Architecture? = null,
        imageType: ImageType? = null,
        jvmImpl: JvmImpl? = null,
        project: Project? = null,
        before: DateTime? = null,
        cLib: CLib? = null,
    ) {
        this.os = os
        this.arch = arch
        this.imageType = imageType
        this.jvmImpl = jvmImpl
        this.project = project ?: Project.jdk
        this.before = before
        this.cLib = cLib
    }

    override fun test(binary: Binary): Boolean {
        return (os == null || binary.os == os) &&
            (arch == null || binary.architecture == arch) &&
            (imageType == null || binary.image_type == imageType) &&
            (jvmImpl == null || binary.jvm_impl == jvmImpl) &&
            (binary.project == project) &&
            (before == null || binary.timestamp.toInstant().isBefore(before.dateTime.toInstant())) &&
            (cLib == null || binary.c_lib == cLib)
    }
}
