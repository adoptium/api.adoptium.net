package net.adoptium.api

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.ReleaseInfo
import java.time.ZonedDateTime

@Priority(1)
@Alternative
@ApplicationScoped
open class ApiDataStoreStub : APIDataStore {

    open var scheduled: Boolean = false
    private lateinit var adoptRepo: AdoptRepos
    private lateinit var attestationRepo: AdoptAttestationRepos

    constructor() {
        reset()
    }

    constructor(adoptRepo: AdoptRepos) {
        this.adoptRepo = adoptRepo
    }

    open fun reset() {
        BaseTest.startDb()
        this.adoptRepo = AdoptReposTestDataGenerator.generate()
        this.attestationRepo = AdoptAttestationReposTestDataGenerator.generate()
    }

    override fun schedulePeriodicUpdates() {
        // NOP
        this.scheduled = true
    }

    override fun getAdoptRepos(): AdoptRepos {
        return adoptRepo
    }

    override fun setAdoptRepos(adoptRepo: AdoptRepos) {
        this.adoptRepo = adoptRepo
    }

    override fun getAdoptAttestationRepos(): AdoptAttestationRepos {
        return attestationRepo
    }

    override fun setAdoptAttestationRepos(attestationRepo: AdoptAttestationRepos) {
        this.attestationRepo = attestationRepo
    }

    override fun getReleaseInfo(): ReleaseInfo {
        return ReleaseInfo(
            arrayOf(8, 9, 10, 11, 12),
            arrayOf(8, 11),
            11,
            12,
            13,
            15
        )
    }

    override fun loadDataFromDb(forceUpdate: Boolean, log:Boolean): AdoptRepos {
        // nop
        return adoptRepo
    }

    override fun loadAttestationDataFromDb(forceUpdate: Boolean, log:Boolean): AdoptAttestationRepos {
        // nop
        return attestationRepo
    }

    override fun getUpdateInfo(): UpdatedInfo {
        return UpdatedInfo(
            ZonedDateTime.now(),
            "1234567890",
            123
        )
    }

    override fun getAttestationUpdateInfo(): UpdatedInfo {
        return UpdatedInfo(
            ZonedDateTime.now(),
            "1234567890",
            123
        )
    }

    override suspend fun isConnectedToDb(): Boolean {
        return true
    }
}
