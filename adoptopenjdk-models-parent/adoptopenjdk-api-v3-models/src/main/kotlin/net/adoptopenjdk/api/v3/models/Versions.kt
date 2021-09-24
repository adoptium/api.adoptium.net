package net.adoptopenjdk.api.v3.models

object Versions {

    private const val DEFAULT_LATEST_JAVA_VERSION = 17
    private const val LATEST_JAVA_VERSION_PROPERTY = "LATEST_JAVA_VERSION"

    private val latestJavaVersion: Int
    val versions: Array<Int>
    val ltsVersions: Array<Int> = arrayOf(8, 11, 17)

    init {
        latestJavaVersion = Integer.parseInt(System.getProperty(LATEST_JAVA_VERSION_PROPERTY, DEFAULT_LATEST_JAVA_VERSION.toString()))
        versions = (8..latestJavaVersion).toList().toTypedArray()
    }
}
