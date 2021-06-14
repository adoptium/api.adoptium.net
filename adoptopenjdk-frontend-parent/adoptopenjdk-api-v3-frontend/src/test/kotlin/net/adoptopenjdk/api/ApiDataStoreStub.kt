package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.models.ReleaseInfo
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.inject.Singleton

@Priority(1)
@Alternative
@Singleton
open class ApiDataStoreStub : APIDataStore {

    open var scheduled: Boolean = false
    private lateinit var adoptRepo: AdoptRepos

    constructor() {
        reset()
    }

    constructor(adoptRepo: AdoptRepos) {
        this.adoptRepo = adoptRepo
    }

    open fun reset() {
        BaseTest.startDb()
        this.adoptRepo = AdoptReposTestDataGenerator.generate()
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

    override fun loadDataFromDb(forceUpdate: Boolean): AdoptRepos {
        // nop
        return adoptRepo
    }
}
