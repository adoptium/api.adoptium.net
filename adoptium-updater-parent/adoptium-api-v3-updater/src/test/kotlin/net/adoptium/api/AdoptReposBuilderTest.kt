package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.AdoptRepositoryStub
import net.adoptium.api.testDoubles.UpdatableVersionSupplierStub
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.models.GHReleaseMetadata
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@EnableAutoWeld
class AdoptReposBuilderTest : BaseTest() {

    companion object {
        private val stub = AdoptRepositoryStub()
        private val adoptReposBuilder: AdoptReposBuilder = AdoptReposBuilder(stub, UpdatableVersionSupplierStub())
        private var before: AdoptRepos = stub.repo
        private var updated: AdoptRepos = runBlocking {
            adoptReposBuilder.incrementalUpdate(emptySet(), before) { null }
        }
    }

    @Test
    fun addReleaseIsUpdatedExplicitly() {
        runBlocking {
            val adoptRepository = AdoptRepositoryStub()
            val adoptRepo = spyk(adoptRepository)
            val adoptReposBuilder = AdoptReposBuilder(adoptRepo, UpdatableVersionSupplierStub())

            adoptReposBuilder.incrementalUpdate(setOf(before.repos[8]?.releases?.nodeList?.first()?.release_name!!), before) { null }

            val toUpdate = updated.getFeatureRelease(8)!!.releases.nodes.values.take(AdoptRepositoryStub.unchangedIndex).last()

            updated = adoptReposBuilder.incrementalUpdate(setOf(toUpdate.release_name), before) { null }
            coVerify(exactly = 1) { adoptRepo.getReleaseById(match { it.id == toUpdate.id }) }
        }
    }

    @Test
    fun removedReleaseIsRemovedWhenUpdated() {
        runBlocking {
            assertTrue { before.getFeatureRelease(8)!!.releases.hasReleaseId(GitHubId(stub.toRemove.id)) }
            assertTrue { !updated.getFeatureRelease(8)!!.releases.hasReleaseId(GitHubId(stub.toRemove.id)) }
            assertTrue { updated != before }
        }
    }

    @Test
    fun addReleaseIsAddWhenUpdated() {
        runBlocking {
            assertTrue { !before.getFeatureRelease(8)!!.releases.hasReleaseId(GitHubId("foo")) }
            assertTrue { updated.getFeatureRelease(8)!!.releases.getReleases().contains(AdoptRepositoryStub.toAdd) }
            assertTrue { updated != before }
        }
    }

    @Test
    fun releaseLessThan10MinOldIsNotUpdated() {
        runBlocking {
            assertTrue { !updated.getFeatureRelease(8)!!.releases.hasReleaseId(GitHubId("young")) }
        }
    }

    @Test
    fun updatedReleaseIsUpdatedWhenUpdated() {
        runBlocking {
            assertTrue { before.getFeatureRelease(8)!!.releases.getReleases().contains(stub.originalToUpdate) }
            assertTrue { !updated.getFeatureRelease(8)!!.releases.getReleases().contains(stub.originalToUpdate) }
            assertTrue { updated.getFeatureRelease(8)!!.releases.getReleases().contains(stub.toUpdate) }
            assertTrue { updated != before }
        }
    }

    @Test
    fun updatedReleaseIsNotUpdatedWhenThingsDontChange() {
        runBlocking {

            val updated2 = adoptReposBuilder.incrementalUpdate(emptySet(), before) { null }

            val updated3 = adoptReposBuilder.incrementalUpdate(emptySet(), before) { null }

            assertTrue { updated == updated2 }
            assertTrue { updated2 == updated3 }
        }
    }

    @Test
    fun `young releases continue to be pulled`() {
        runBlocking {
            val repo = stub.repo
            val adoptRepository = AdoptRepositoryStub()
            val adoptRepo = spyk(adoptRepository)
            val adoptReposBuilder = AdoptReposBuilder(adoptRepo, UpdatableVersionSupplierStub())

            coEvery { adoptRepo.getReleaseById(GitHubId(AdoptRepositoryStub.toAddSemiYoungRelease.id)) } returns ReleaseResult(listOf(AdoptRepositoryStub.toAddSemiYoungRelease))

            val updatedRepo = adoptReposBuilder.incrementalUpdate(emptySet(), repo) { null }
            adoptReposBuilder.incrementalUpdate(emptySet(), updatedRepo) { null }

            coVerify(exactly = 3) { adoptRepo.getReleaseById(GitHubId(AdoptRepositoryStub.toAddSemiYoungRelease.id)) }
        }
    }

    @Test
    fun `release is updated when binary count changes`() {
        runBlocking {
            val adoptRepository = AdoptRepositoryStub()
            val adoptRepo = spyk(adoptRepository)
            val adoptReposBuilder = AdoptReposBuilder(adoptRepo, UpdatableVersionSupplierStub())

            adoptReposBuilder.incrementalUpdate(emptySet(), before) {
                if (before.repos[11]?.releases?.nodeList?.first()?.id == it.id) {
                    GHReleaseMetadata(
                        99999,
                        it
                    )
                } else {
                    GHReleaseMetadata(
                        before.allReleases.getReleaseById(it.id)?.binaries?.size ?: 0,
                        it
                    )
                }
            }

            coVerify(exactly = 1) { adoptRepo.getReleaseById(GitHubId(before.repos[11]?.releases?.nodeList?.first()?.id!!)) }
        }
    }

    @Test
    fun `release is not updated when binary count does not change`(adoptRepository: AdoptRepository, versionSupplier: VersionSupplier) {
        runBlocking {
            val adoptRepo = spyk(adoptRepository)
            val adoptReposBuilder = AdoptReposBuilder(adoptRepo, versionSupplier)

            adoptReposBuilder.incrementalUpdate(emptySet(), before) {
                GHReleaseMetadata(
                    before.allReleases.getReleaseById(it.id)?.binaries?.size ?: 0,
                    it
                )
            }

            coVerify(exactly = 0) { adoptRepo.getReleaseById(GitHubId(before.repos[11]?.releases?.nodeList?.first()?.id!!)) }
        }
    }
}
