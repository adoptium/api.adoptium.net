package net.adoptium.api.v3.dataSources

import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.ReleaseInfo

interface APIDataStore {
    fun schedulePeriodicUpdates()
    fun getAdoptRepos(): AdoptRepos
    fun setAdoptRepos(binaryRepos: AdoptRepos)
    fun getReleaseInfo(): ReleaseInfo
    fun loadDataFromDb(forceUpdate: Boolean, logEntries: Boolean = true): AdoptRepos
    fun getUpdateInfo(): UpdatedInfo
    suspend fun isConnectedToDb(): Boolean
}
