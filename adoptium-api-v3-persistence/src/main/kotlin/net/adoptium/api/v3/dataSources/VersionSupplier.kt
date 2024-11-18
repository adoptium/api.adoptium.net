package net.adoptium.api.v3.dataSources

interface VersionSupplier {
    fun getTipVersion(): Int?
    fun getLtsVersions(): Array<Int>
    fun getAllVersions(): Array<Int>
}
