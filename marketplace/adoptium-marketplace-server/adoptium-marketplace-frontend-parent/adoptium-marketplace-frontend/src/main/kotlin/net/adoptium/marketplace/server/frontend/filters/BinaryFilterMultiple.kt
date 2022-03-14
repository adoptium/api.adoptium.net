package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.marketplace.schema.*
import net.adoptium.marketplace.server.frontend.models.APIDateTime
import java.util.function.Predicate

class BinaryFilterMultiple : Predicate<Binary> {

    private var cLib: List<CLib>?
    private val os: List<OperatingSystem>?
    private val arch: List<Architecture>?
    private val imageType: List<ImageType>?
    private val jvmImpl: List<JvmImpl>?
    private val before: APIDateTime?

    constructor(
            os: List<OperatingSystem>? = null,
            arch: List<Architecture>? = null,
            imageType: List<ImageType>? = null,
            jvmImpl: List<JvmImpl>? = null,
            before: APIDateTime? = null,
            cLib: List<CLib>? = null,
    ) {
        this.os = os
        this.arch = arch
        this.imageType = imageType
        this.jvmImpl = jvmImpl
        this.before = before
        this.cLib = cLib
    }

    override fun test(binary: Binary): Boolean {
        return test(binary.os, os) &&
            test(binary.architecture, arch) &&
            test(binary.imageType, imageType) &&
            test(binary.jvmImpl, jvmImpl) &&
            (before == null || binary.timestamp.toInstant().isBefore(before.dateTime.toInstant())) &&
            test(binary.cLib, cLib)
    }

    fun <T> test(value: T, matcher: List<T>?): Boolean {
        return matcher == null || matcher.isEmpty() || matcher.contains(value)
    }
}
