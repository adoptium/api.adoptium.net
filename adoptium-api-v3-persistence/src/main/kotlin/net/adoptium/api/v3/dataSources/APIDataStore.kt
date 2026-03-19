package net.adoptium.api.v3.dataSources

import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.ReleaseInfo

interface APIDataStore {
    fun schedulePeriodicUpdates()
    fun getAdoptRepos(): AdoptRepos
    fun setAdoptRepos(binaryRepos: AdoptRepos)
    fun getAdoptAttestationRepos(): AdoptAttestationRepos
    fun setAdoptAttestationRepos(attestationRepos: AdoptAttestationRepos)
    fun getReleaseInfo(): ReleaseInfo
    fun loadDataFromDb(forceUpdate: Boolean, logEntries: Boolean = true): AdoptRepos
    fun loadAttestationDataFromDb(forceUpdate: Boolean, logEntries: Boolean = true): AdoptAttestationRepos
    fun getUpdateInfo(): UpdatedInfo
    fun getAttestationUpdateInfo(): UpdatedInfo
    suspend fun isConnectedToDb(): Boolean
}
