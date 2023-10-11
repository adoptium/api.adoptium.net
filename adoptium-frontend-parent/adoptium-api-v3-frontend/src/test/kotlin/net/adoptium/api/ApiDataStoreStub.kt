package net.adoptium.api

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.models.ReleaseInfo

@Priority(1)
@Alternative
@ApplicationScoped
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
