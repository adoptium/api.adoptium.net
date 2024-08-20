package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.VersionSupplier

@ApplicationScoped
class FrontEndVersionSupplier @Inject constructor(
    val apiDataStore: APIDataStore
) : VersionSupplier {
    override fun getTipVersion(): Int {
        return apiDataStore.getReleaseInfo().tip_version
    }

    override fun getLtsVersions(): Array<Int> {
        return apiDataStore.getReleaseInfo().available_lts_releases
    }

    override fun getAllVersions(): Array<Int> {
        return apiDataStore.getReleaseInfo().available_releases
    }
}
