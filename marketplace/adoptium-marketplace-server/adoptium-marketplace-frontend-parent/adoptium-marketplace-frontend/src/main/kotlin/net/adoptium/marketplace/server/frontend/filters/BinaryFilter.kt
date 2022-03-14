package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.marketplace.schema.*
import net.adoptium.marketplace.server.frontend.models.APIDateTime
import java.util.function.Predicate

class BinaryFilter : Predicate<Binary> {

    private var cLib: CLib?
    private val os: OperatingSystem?
    private val arch: Architecture?
    private val imageType: ImageType?
    private val jvmImpl: JvmImpl?
    private val before: APIDateTime?

    constructor(
        os: OperatingSystem? = null,
        arch: Architecture? = null,
        imageType: ImageType? = null,
        jvmImpl: JvmImpl? = null,
        before: APIDateTime? = null,
        cLib: CLib? = null,
    ) {
        this.os = os
        this.arch = arch
        this.imageType = imageType
        this.jvmImpl = jvmImpl
        this.before = before
        this.cLib = cLib
    }

    override fun test(binary: Binary): Boolean {
        return (os == null || binary.os == os) &&
            (arch == null || binary.architecture == arch) &&
            (imageType == null || binary.imageType == imageType) &&
            (jvmImpl == null || binary.jvmImpl == jvmImpl) &&
            (before == null || binary.timestamp.toInstant().isBefore(before.dateTime.toInstant())) &&
            (cLib == null || binary.cLib == cLib)
    }
}
