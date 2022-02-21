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

class BinaryFilterMultiple : Predicate<Binary> {

    private var cLib: List<CLib>?
    private val os: List<OperatingSystem>?
    private val arch: List<Architecture>?
    private val imageType: List<ImageType>?
    private val jvmImpl: List<JvmImpl>?
    private val project: List<Project>?
    private val before: DateTime?

    constructor(
        os: List<OperatingSystem>? = null,
        arch: List<Architecture>? = null,
        imageType: List<ImageType>? = null,
        jvmImpl: List<JvmImpl>? = null,
        project: List<Project>? = null,
        before: DateTime? = null,
        cLib: List<CLib>? = null,
    ) {
        this.os = os
        this.arch = arch
        this.imageType = imageType
        this.jvmImpl = jvmImpl
        this.project = project
        this.before = before
        this.cLib = cLib
    }

    override fun test(binary: Binary): Boolean {
        return test(binary.os, os) &&
            test(binary.architecture, arch) &&
            test(binary.image_type, imageType) &&
            test(binary.jvm_impl, jvmImpl) &&
            test(binary.project, project) &&
            (before == null || binary.timestamp.toInstant().isBefore(before.dateTime.toInstant())) &&
            test(binary.c_lib, cLib)
    }

    fun <T> test(value: T, matcher: List<T>?): Boolean {
        return matcher == null || matcher.isEmpty() || matcher.contains(value)
    }
}
