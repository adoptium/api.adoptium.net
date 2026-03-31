package net.adoptium.api.v3.dataSources

import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.ReleaseInfo

interface APIDataStore {
    fun schedulePeriodicUpdates()
    fun getAdoptRepos(): AdoptRepos
    fun setAdoptRepos(binaryRepos: AdoptRepos)
    fun getAdoptCdxaRepos(): AdoptCdxaRepos
    fun setAdoptCdxaRepos(cdxaRepos: AdoptCdxaRepos)
    fun getReleaseInfo(): ReleaseInfo
    fun loadDataFromDb(forceUpdate: Boolean, logEntries: Boolean = true): AdoptRepos
    fun loadCdxaDataFromDb(forceUpdate: Boolean, logEntries: Boolean = true): AdoptCdxaRepos
    fun getUpdateInfo(): UpdatedInfo
    fun getCdxaUpdateInfo(): UpdatedInfo
    suspend fun isConnectedToDb(): Boolean
}
