package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PublicationInvariantTest {
    private fun release(id: String, type: ReleaseType, version: Int = 8, vendor: Vendor = Vendor.getDefault()): Release {
        val template = BaseTest.adoptRepos.getFeatureRelease(version)!!.releases.nodeList.first()
        return Release(id, type, template.release_link, template.release_name, template.timestamp,
            template.updated_at, emptyArray(), 50, vendor, template.version_data)
    }

    private fun repos(vararg releases: Release) = AdoptRepos(releases.groupBy { it.version_data.major }
        .map { (version, entries) -> FeatureRelease(version, Releases(entries)) })

    private fun updater(builder: AdoptReposBuilder, database: InMemoryApiPersistence): V3Updater {
        val versions = mockk<UpdatableVersionSupplier>()
        coEvery { versions.updateVersions() } returns Unit
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } answers { secondArg() }
        return V3Updater(builder, mockk(), APIDataStoreImpl(database), database,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), versions)
    }

    @Test
    fun `full replacement preserves excluded-vendor data from persistence rather than startup state`() = runBlocking {
        val preserved = release("latest-excluded", ReleaseType.ea, vendor = Vendor.adoptopenjdk)
        val rebuilt = release("rebuilt", ReleaseType.ga, version = 11, vendor = Vendor.eclipse)
        val latest = repos(preserved)
        val database = InMemoryApiPersistence(latest, AdoptCdxaRepos(emptyList()))
        database.updateAllRepos(latest, V3Updater.calculateChecksum(latest))
        val builder = mockk<AdoptReposBuilder>()
        var builds = 0
        coEvery { builder.build(any()) } answers {
            if (++builds == 1) repos(rebuilt) else error("Only the first full build is under test")
        }
        val previousPolicy = APIConfig.UPDATE_ADOPTOPENJDK
        try {
            APIConfig.UPDATE_ADOPTOPENJDK = false
            updater(builder, database).runUpdate(repos(), AtomicBoolean(true), mockk())
        } finally {
            APIConfig.UPDATE_ADOPTOPENJDK = previousPolicy
        }
        assertEquals(setOf(preserved.id, rebuilt.id), database.repos.allReleases.nodes.keys)
        assertEquals(50L, database.repos.allReleases.nodes.getValue(preserved.id).download_count)
    }

    @ParameterizedTest
    @EnumSource(value = ReleaseType::class, names = ["ga", "ea"])
    fun `guard checks a completely absent version and exempts only omitted EAs`(type: ReleaseType) = runBlocking {
        val before = repos(release("previous", type))
        val database = spyk(InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())))
        val builder = mockk<AdoptReposBuilder>()
        coEvery { builder.build(any()) } returns repos()
        val previousPolicy = APIConfig.UPDATE_ADOPTOPENJDK
        val after = try {
            APIConfig.UPDATE_ADOPTOPENJDK = true
            updater(builder, database).runUpdate(before, AtomicBoolean(true), mockk())
        } finally {
            APIConfig.UPDATE_ADOPTOPENJDK = previousPolicy
        }

        val expected = if (type == ReleaseType.ga) before else repos()
        assertEquals(expected, after)
        assertEquals(expected, database.repos)
        coVerify(exactly = if (type == ReleaseType.ga) 0 else 2) { database.updateAllRepos(any(), any()) }
    }

    @Test
    fun `startup runs an incremental first and scheduled incrementals skip while a full build runs`() = runBlocking {
        val before = repos(release("baseline", ReleaseType.ga))
        val database = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        val builder = mockk<AdoptReposBuilder>()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val incrementals = AtomicInteger()
        val buildStarted = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val updater = updater(builder, database)
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } coAnswers {
            incrementals.incrementAndGet()
            order.add("incremental")
            secondArg()
        }
        coEvery { builder.build(any()) } coAnswers {
            order.add("build")
            buildStarted.countDown()
            check(releaseBuild.await(30, TimeUnit.SECONDS))
            before
        }
        val scheduled = slot<Runnable>()
        val executor = mockk<ScheduledExecutorService>()
        every { executor.scheduleWithFixedDelay(capture(scheduled), any(), any(), any()) } returns mockk(relaxed = true)

        val runner = Thread { updater.runUpdate(before, AtomicBoolean(false), executor) }
        runner.start()
        check(buildStarted.await(30, TimeUnit.SECONDS))
        assertEquals(listOf("incremental", "build"), order.toList())

        val incrementalsBefore = incrementals.get()
        scheduled.captured.run()
        assertEquals(incrementalsBefore, incrementals.get())

        releaseBuild.countDown()
        runner.join(30_000)
        scheduled.captured.run()
        assertEquals(incrementalsBefore + 2, incrementals.get())
    }

    @Test
    fun `startup skips the early incremental when the baseline is empty`() = runBlocking {
        val database = InMemoryApiPersistence(repos(), AdoptCdxaRepos(emptyList()))
        val builder = mockk<AdoptReposBuilder>()
        val updater = updater(builder, database)
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } coAnswers {
            order.add("incremental")
            secondArg()
        }
        coEvery { builder.build(any()) } coAnswers {
            order.add("build")
            repos()
        }
        val executor = mockk<ScheduledExecutorService>()
        every { executor.scheduleWithFixedDelay(any(), any(), any(), any()) } returns mockk(relaxed = true)

        updater.runUpdate(repos(), AtomicBoolean(false), executor)

        assertEquals("build", order.first())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a failed startup incremental continues to the full build only for non-database failures`(database: Boolean) = runBlocking {
        val before = repos(release("baseline", ReleaseType.ga))
        val persistence = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        val builder = mockk<AdoptReposBuilder>()
        val updater = updater(builder, persistence)
        val failure = if (database) com.mongodb.MongoException("down") else IllegalStateException("fetch failed")
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } throws failure
        coEvery { builder.build(any()) } returns before
        val executor = mockk<ScheduledExecutorService>()
        every { executor.scheduleWithFixedDelay(any(), any(), any(), any()) } returns mockk(relaxed = true)

        if (database) {
            assertSame(failure, assertThrows(com.mongodb.MongoException::class.java) {
                updater.runUpdate(before, AtomicBoolean(false), executor)
            })
            coVerify(exactly = 0) { builder.build(any()) }
        } else {
            updater.runUpdate(before, AtomicBoolean(false), executor)
            coVerify(exactly = 2) { builder.build(any()) }
        }
        verify(exactly = 1) { executor.scheduleWithFixedDelay(any(), any(), any(), any()) }
    }
}
