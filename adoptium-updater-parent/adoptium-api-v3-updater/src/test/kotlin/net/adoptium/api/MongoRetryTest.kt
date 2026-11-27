package net.adoptium.api

import com.mongodb.MongoException
import com.mongodb.MongoNodeIsRecoveringException
import com.mongodb.MongoNotPrimaryException
import com.mongodb.MongoSocketReadException
import com.mongodb.MongoTimeoutException
import com.mongodb.ServerAddress
import com.mongodb.client.model.InsertManyOptions
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.WriteModel
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStoreImpl
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoInterface
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.Release
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MongoRetryTest {
    private val mongo = object : MongoInterface() {}
    private val database by lazy { mockk<MongoDatabase>(relaxed = true) }
    private val client by lazy { mockk<MongoClient>().also { every { it.getDatabase() } returns database } }

    private inline fun <reified T : Any> collection(name: String): MongoCollection<T> =
        mockk<MongoCollection<T>>(relaxed = true).also {
            every { database.getCollection(name, T::class.java) } returns it
        }

    private fun transient(kind: String): MongoException = when (kind) {
        "socket" -> MongoSocketReadException("Connection interrupted", ServerAddress())
        "timeout" -> MongoTimeoutException("Server unavailable")
        "primary" -> MongoNotPrimaryException(BsonDocument.parse("""{"ok":0,"code":10107,"errmsg":"not primary"}"""), ServerAddress())
        "recovering" -> MongoNodeIsRecoveringException(BsonDocument.parse("""{"ok":0,"code":91,"errmsg":"recovering"}"""), ServerAddress())
        else -> MongoException("Retryable write").apply { addLabel("RetryableWriteError") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["socket", "timeout", "primary", "recovering", "labelled"])
    fun `known transient failures can succeed on the third and final attempt`(kind: String) = runBlocking {
        var attempts = 0
        val result = mongo.retryMongoOperation("test operation") {
            if (++attempts < 3) throw transient(kind)
            "saved"
        }
        assertEquals("saved", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `transient exhaustion propagates the final failure after exactly three attempts`() = runBlocking {
        var attempts = 0
        val failures = List(3) { transient("socket") }
        val error = runCatching {
            mongo.retryMongoOperation("test exhaustion") { throw failures[attempts++] }
        }.exceptionOrNull()
        assertSame(failures.last(), error)
        assertEquals(3, attempts)
    }

    @ParameterizedTest
    @ValueSource(ints = [13, 18, 121, 11000, -1])
    fun `authorization authentication validation duplicate and unknown errors are not retried`(code: Int) = runBlocking {
        var attempts = 0
        val failure = MongoException(code, "Permanent or unclassified failure")
        val error = runCatching {
            mongo.retryMongoOperation("test permanent error") {
                attempts++
                throw failure
            }
        }.exceptionOrNull()
        assertSame(failure, error)
        assertEquals(1, attempts)
    }

    @Test
    fun `cancellation during backoff prevents another database attempt`() = runBlocking {
        var attempts = 0
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            mongo.retryMongoOperation("cancel retry") {
                attempts++
                throw transient("timeout")
            }
        }
        task.cancelAndJoin()
        assertEquals(1, attempts)
    }

    @Test
    fun `cancellation exceptions propagate without retry`() = runBlocking {
        val failure = CancellationException("Cancelled")
        var attempts = 0
        val error = runCatching {
            mongo.retryMongoOperation("cancel operation") { attempts++; throw failure }
        }.exceptionOrNull()
        assertSame(failure, error)
        assertEquals(1, attempts)
    }

    @Test
    fun `already cancelled attempts do not perform database work`() = runBlocking {
        var attempts = 0
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            mongo.retryMongoOperation("cancelled before operation") { attempts++ }
        }
        task.join()
        assertEquals(0, attempts)
    }

    @Test
    fun `failed release cursors are restarted without retaining their partial results`() = runBlocking {
        val releases = collection<Release>(MongoApiPersistence.RELEASE_DB)
        val old = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val replacement = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val partial = mockk<FindFlow<Release>>()
        val complete = mockk<FindFlow<Release>>()
        every { releases.find(any<Bson>()) } returnsMany listOf(partial, complete)
        coEvery { partial.collect(any()) } coAnswers {
            firstArg<FlowCollector<Release>>().emit(old)
            throw transient("socket")
        }
        coEvery { complete.collect(any()) } coAnswers { firstArg<FlowCollector<Release>>().emit(replacement) }

        val result = MongoApiPersistence(client).readAllReleaseData()

        assertEquals(setOf(replacement.id), result.allReleases.nodes.keys)
        io.mockk.verify(exactly = 2) { releases.find(any<Bson>()) }
    }

    @Test
    fun `generation reads stop at the database retry budget`() = runBlocking {
        val generations = collection<UpdatedInfo>(MongoApiPersistence.UPDATE_TIME_DB)
        val failure = transient("timeout")
        every { generations.find(any<Bson>()) } throws failure
        val error = runCatching { MongoApiPersistence(client).getUpdatedAt() }.exceptionOrNull()
        assertSame(failure, error)
        io.mockk.verify(exactly = 3) { generations.find(any<Bson>()) }
    }

    @Test
    fun `generation upserts retry the same publication identity`() = runBlocking {
        val generations = collection<UpdatedInfo>(MongoApiPersistence.UPDATE_TIME_DB)
        val replacements = mutableListOf<UpdatedInfo>()
        coEvery { generations.replaceOne(any<Bson>(), capture(replacements), any()) } coAnswers {
            if (replacements.size < 3) throw transient("socket")
            UpdateResult.acknowledged(1, 1, null)
        }

        MongoApiPersistence(client).updateUpdatedTime(TimeSource.now(), "published", 1)

        assertEquals(3, replacements.size)
        assertNotNull(replacements.first().generationId)
        replacements.forEach { assertSame(replacements.first(), it) }
    }

    @Test
    fun `metadata upserts retry without changing their replacement`() = runBlocking {
        val metadata = collection<GHReleaseMetadata>(MongoApiPersistence.GH_RELEASE_METADATA)
        val value = mockk<GHReleaseMetadata>(relaxed = true)
        var attempts = 0
        coEvery { metadata.replaceOne(any<Bson>(), value, any()) } coAnswers {
            if (++attempts < 3) throw transient("primary")
            UpdateResult.acknowledged(1, 1, null)
        }

        MongoApiPersistence(client).setGhReleaseMetadata(value)

        coVerify(exactly = 3) { metadata.replaceOne(any<Bson>(), value, any()) }
    }

    @Test
    fun `release upserts and superseded deletes retry because they are idempotent`() = runBlocking {
        val releases = collection<Release>(MongoApiPersistence.RELEASE_DB)
        var writes = 0
        var deletes = 0
        coEvery { releases.bulkWrite(any<List<WriteModel<Release>>>(), any()) } coAnswers {
            if (++writes < 3) throw transient("socket")
            BulkWriteResult.unacknowledged()
        }
        coEvery { releases.deleteMany(any<Bson>(), any()) } coAnswers {
            if (++deletes < 3) throw transient("socket")
            com.mongodb.client.result.DeleteResult.acknowledged(1)
        }
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val snapshot = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))

        MongoApiPersistence(client).updateAllRepos(snapshot, "target")

        assertEquals(3, writes)
        assertEquals(3, deletes)
    }

    @Test
    fun `exhausted release upserts leave existing releases untouched and unpublished`() = runBlocking {
        val releases = collection<Release>(MongoApiPersistence.RELEASE_DB)
        val generations = collection<UpdatedInfo>(MongoApiPersistence.UPDATE_TIME_DB)
        val failure = transient("socket")
        coEvery { releases.bulkWrite(any<List<WriteModel<Release>>>(), any()) } throws failure
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val snapshot = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        val error = runCatching { MongoApiPersistence(client).updateAllRepos(snapshot, "target") }.exceptionOrNull()

        assertSame(failure, error)
        coVerify(exactly = 3) { releases.bulkWrite(any<List<WriteModel<Release>>>(), any()) }
        coVerify(exactly = 0) { releases.deleteMany(any<Bson>(), any()) }
        coVerify(exactly = 0) { generations.replaceOne(any<Bson>(), any(), any()) }
    }

    @Test
    fun `failed attestation inserts are not retried or followed by a success generation`() = runBlocking {
        val attestations = collection<Cdxa>(MongoApiPersistence.CDXAS_DB)
        val generations = collection<UpdatedInfo>(MongoApiPersistence.CDXAS_UPDATE_TIME_DB)
        val failure = transient("socket")
        coEvery { attestations.insertMany(any<List<Cdxa>>(), any<InsertManyOptions>()) } throws failure
        val entry = mockk<Cdxa>(relaxed = true)
        every { entry.featureVersion } returns 8
        val error = runCatching {
            MongoApiPersistence(client).updateCdxaRepos(AdoptCdxaRepos(listOf(entry)), "target")
        }.exceptionOrNull()

        assertSame(failure, error)
        coVerify(exactly = 1) { attestations.insertMany(any<List<Cdxa>>(), any<InsertManyOptions>()) }
        coVerify(exactly = 0) { generations.replaceOne(any<Bson>(), any<UpdatedInfo>(), any()) }
    }

    @Test
    fun `release note upserts retry without changing their replacement`() = runBlocking {
        val notes = collection<ReleaseNotes>(MongoApiPersistence.GH_RELEASE_NOTES)
        val value = mockk<ReleaseNotes>(relaxed = true)
        var attempts = 0
        coEvery { notes.replaceOne(any<Bson>(), value, any()) } coAnswers {
            if (++attempts < 3) throw transient("socket")
            UpdateResult.acknowledged(1, 1, null)
        }

        MongoApiPersistence(client).putReleaseNote(value)

        assertEquals(3, attempts)
        coVerify(exactly = 3) { notes.replaceOne(any<Bson>(), value, any()) }
        coVerify(exactly = 0) { notes.deleteMany(any<Bson>(), any()) }
    }

    @Test
    fun `exhausted release note upserts propagate their database failure`() = runBlocking {
        val notes = collection<ReleaseNotes>(MongoApiPersistence.GH_RELEASE_NOTES)
        val value = mockk<ReleaseNotes>(relaxed = true)
        val failure = transient("socket")
        coEvery { notes.replaceOne(any<Bson>(), value, any()) } throws failure

        assertSame(failure, runCatching { MongoApiPersistence(client).putReleaseNote(value) }.exceptionOrNull())
        coVerify(exactly = 3) { notes.replaceOne(any<Bson>(), value, any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["github", "docker", "package"])
    fun `append-only statistics are not replayed on an ambiguous transient failure`(source: String) = runBlocking {
        when (source) {
            "github" -> assertInsertNotRetried(collection<GitHubDownloadStatsDbEntry>(MongoApiPersistence.GITHUB_STATS_DB)) {
                it.addGithubDownloadStatsEntries(listOf(mockk()))
            }
            "docker" -> assertInsertNotRetried(collection<DockerDownloadStatsDbEntry>(MongoApiPersistence.DOCKER_STATS_DB)) {
                it.addDockerDownloadStatsEntries(listOf(mockk()))
            }
            else -> assertInsertNotRetried(collection<CloudflarePackageDownloadStatsDbEntry>(MongoApiPersistence.PACKAGE_STATS_DB)) {
                it.addPackageDownloadStatsEntries(listOf(mockk()))
            }
        }
    }

    private suspend fun <T : Any> assertInsertNotRetried(collection: MongoCollection<T>, insert: suspend (MongoApiPersistence) -> Unit) {
        val failure = transient("socket")
        coEvery { collection.insertMany(any<List<T>>(), any<InsertManyOptions>()) } throws failure
        assertSame(failure, runCatching { insert(MongoApiPersistence(client)) }.exceptionOrNull())
        coVerify(exactly = 1) { collection.insertMany(any<List<T>>(), any<InsertManyOptions>()) }
    }

    @Test
    fun `cache writes use the same bounded retry policy inside their awaitable result`() = runBlocking {
        val cache = collection<CacheDbEntry>("web-cache")
        val store = InternalDbStoreImpl(client)
        var attempts = 0
        coEvery { cache.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            if (++attempts < 3) throw transient("socket")
            UpdateResult.acknowledged(1, 1, null)
        }

        store.putCachedWebpage("url", null, TimeSource.now(), "content").await()

        assertEquals(3, attempts)
    }

    @Test
    fun `exhausted cache writes propagate through their awaitable result`() = runBlocking {
        val cache = collection<CacheDbEntry>("web-cache")
        val store = InternalDbStoreImpl(client)
        val failure = transient("socket")
        coEvery { cache.updateOne(any<Bson>(), any<Bson>(), any()) } throws failure

        val error = runCatching {
            store.putCachedWebpage("url", null, TimeSource.now(), "content").await()
        }.exceptionOrNull()

        assertSame(failure, error)
        coVerify(exactly = 3) { cache.updateOne(any<Bson>(), any<Bson>(), any()) }
    }

    @Test
    fun `cache demand touches retry with the original demand timestamp`() = runBlocking {
        val cache = collection<CacheDbEntry>("web-cache")
        val store = InternalDbStoreImpl(client)
        val result = CacheDbEntry("url", null, TimeSource.now(), "content")
        val updates = mutableListOf<Bson>()
        var attempts = 0
        coEvery { cache.findOneAndUpdate(any<Bson>(), capture(updates), any()) } coAnswers {
            if (++attempts < 3) throw transient("socket")
            result
        }

        assertSame(result, store.getCachedWebpage("url"))
        assertEquals(3, attempts)
        assertEquals(1, updates.distinct().size)
        assertTrue(updates.isNotEmpty())
    }
}
