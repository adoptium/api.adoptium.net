package net.adoptium.api.v3.dataSources

interface UpdatableVersionSupplier : VersionSupplier {
    suspend fun updateVersions()
}
