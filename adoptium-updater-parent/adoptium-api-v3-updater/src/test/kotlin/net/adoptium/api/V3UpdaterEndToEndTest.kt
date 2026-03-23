package net.adoptium.api

import io.mockk.mockk
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.AdoptCdxaReposBuilder
import net.adoptium.api.v3.AdoptCdxaRepository
import net.adoptium.api.v3.AdoptCdxaRepositoryImpl
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryEntry
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryObject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.dataSources.github.graphql.models.Declarations
import net.adoptium.api.v3.dataSources.github.graphql.models.Targets
import net.adoptium.api.v3.dataSources.github.graphql.models.Components
import net.adoptium.api.v3.dataSources.github.graphql.models.Component
import net.adoptium.api.v3.dataSources.github.graphql.models.Assessors
import net.adoptium.api.v3.dataSources.github.graphql.models.Assessor
import net.adoptium.api.v3.dataSources.github.graphql.models.Claims
import net.adoptium.api.v3.dataSources.github.graphql.models.Claim
import net.adoptium.api.v3.dataSources.github.graphql.models.Organization
import net.adoptium.api.v3.dataSources.github.graphql.models.Affirmation
import net.adoptium.api.v3.dataSources.github.graphql.models.Hash
import net.adoptium.api.v3.dataSources.github.graphql.models.Hashes
import net.adoptium.api.v3.dataSources.github.graphql.models.Reference
import net.adoptium.api.v3.dataSources.github.graphql.models.ExternalReferences
import net.adoptium.api.v3.dataSources.github.graphql.models.Property
import net.adoptium.api.v3.dataSources.github.graphql.models.Properties
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.mapping.adopt.AdoptCdxaMapperFactory
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.releaseNotes.AdoptReleaseNotes
import net.adoptium.api.v3.stats.StatsInterface
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import java.time.Instant

class V3UpdaterEndToEndTest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @Test
    fun `old release is copied over`() {

        runBlocking {
            var repo = AdoptReposTestDataGenerator.generate()

            repo = repo.addAll(listOf(Release("11", ReleaseType.ga, "a-link", "jdk-8.0.9999+7", DateTime("2013-02-27T19:35:32Z"), DateTime("2013-02-27T19:35:32Z"), emptyArray(), 1, Vendor.eclipse, VersionData(8, 0, 9999, null, 1, 7, null, ""))))

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }

    @Test
    fun `old snapshot is copied over`() {

        runBlocking {
            var repo = AdoptReposTestDataGenerator.generate()

            repo = repo.addAll(listOf(Release("11", ReleaseType.ea, "a-link", "jdk-8.0.9999+7", DateTime("2013-02-27T19:35:32Z"), DateTime("2013-02-27T19:35:32Z"), emptyArray(), 1, Vendor.eclipse, VersionData(8, 0, 9999, null, 1, 7, null, ""))))

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }

    @Test
    fun `unchanged repo has no updates`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }


    @Test
    fun `new release is added`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { id ->
                if (id.id == "1") {
                    GHRelease(
                        GitHubId("1"),
                        "jdk-11.0.9999+7",
                        true,
                        "2013-02-27T19:35:32Z",
                        "2013-02-27T19:35:32Z",
                        GHAssets(
                            listOf(GHAsset("OpenJDK11U-jdk_x64_linux_hotspot_11.0.2_7.tar.gz", 1L, "the-source-link", 1L, "2013-02-27T19:35:32Z")),
                            PageInfo(false, ""),
                            1
                        ), "1", "/AdoptOpenJDK/openjdk11-binaries/releases/tag/jdk9u-2018-09-27-08-50"
                    )
                } else {
                    null
                }
            }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                var releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!
                )
                )
                ).releases.releases


                if (majorVersion == 11) {
                    releases = releases.plus(GHRelease(GitHubId("1"), "jdk-11.0.9999+7", true, "2013-02-27T19:35:32Z", "2013-02-27T19:35:32Z", GHAssets(listOf(GHAsset("OpenJDK11U-jdk_x64_linux_hotspot_11.0.2_7.tar.gz", 1L, "the-source-link", 1L, "2013-02-27T19:35:32Z"
                    )
                    ), PageInfo(false, ""), 1
                    ), "1", "/AdoptOpenJDK/openjdk11-binaries/releases/tag/jdk9u-2018-09-27-08-50"
                    )
                    )
                }

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)


            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size + 1)
            assertTrue(updatedRepo.repos[11]!!.releases.nodeList.last().release_name == "jdk-11.0.9999+7")
        }
    }

    @Test   
    fun `new cdxa is added`() {
        runBlocking {
            val cdxaRepo = AdoptCdxaReposTestDataGenerator.generate()

            val getCdxaSummary: ((String, String, String) -> GHCdxaRepoSummaryData?) = { org, repo , directory ->
                LOGGER.info("getCdxaSummary: "+org+" "+repo+" "+directory)

                var cdxaSummary = GHCdxaSummaryTestDataGenerator.generateGHCdxaRepoSummary(cdxaRepo, directory)

                // Add a 24/jdk-24.0.2+12 aarch64_linux cdxa file into the summary
                if ( directory == "24/jdk-24.0.2+12" && cdxaSummary?.repository?.att_object?.entries != null ) {
                    cdxaSummary?.repository?.att_object?.entries = (cdxaSummary?.repository?.att_object?.entries ?: mutableListOf<GHCdxaRepoSummaryEntry>()) +
                                                                                     GHCdxaRepoSummaryEntry("jdk_24_0_2_12_aarch64_linux_Adoptium.xml",
                                                                                                                   "blob"
                                                                                                                  )
                }

                LOGGER.info("getCdxaSummary: "+cdxaSummary)

                cdxaSummary
            }
            
            val getCdxaByName: ((String, String, String) -> GHCdxa?) = { org, repo, name ->
                            val existAtt = cdxaRepo.repos.firstOrNull { it.filename == name }
                            if (existAtt != null) {
                                // Construct GHCdxa equivalent of existing Cdxa
                                var propPlatform: Property = Property()
                                propPlatform.name = "platform"
                                propPlatform.value = existAtt.architecture.toString() + "_" + existAtt.os.toString()
                                var propImageType: Property = Property()
                                propImageType.name = "imageType"
                                propImageType.value = existAtt.image_type.toString()
                                var propJvmImpl: Property = Property()
                                propJvmImpl.name = "jvmImpl"
                                propJvmImpl.value = existAtt.jvm_impl.toString()
                                var props: Properties = Properties(listOf(propPlatform, propImageType, propJvmImpl))
                                var hash: Hash = Hash()
                                hash.sha256 = existAtt.target_checksum
                                var hashes: Hashes = Hashes(listOf(hash))
                                var er: Reference = Reference(null, hashes)
                                var erl: ExternalReferences = ExternalReferences(listOf(er))
                                var c: Component = Component("comp", existAtt.release_name, erl, props)
                                var cs: Components = Components(listOf(c))
                                var a: Assessor = Assessor(null, Organization(existAtt.assessor_org))
                                var ass: Assessors = Assessors(listOf(a))
                                var aff: Affirmation = Affirmation(existAtt.assessor_affirmation)
                                var cl: Claim = Claim(null, existAtt.assessor_claim_predicate)
                                var cls: Claims = Claims(listOf(cl))
                                var d: Declarations = Declarations(ass, cls, null, Targets(cs), aff)

                                GHCdxa( GitHubId(existAtt.id), existAtt.filename, existAtt.cdxa_link, existAtt.cdxa_public_signing_key_link, existAtt.committedDate, d, null)
                            } else if (name == "24/jdk-24.0.2+12/jdk_24_0_2_12_aarch64_linux_Adoptium.xml") {
                                // Return the new cdxa
                                var propPlatform: Property = Property()
                                propPlatform.name = "platform"
                                propPlatform.value = "aarch64_linux"
                                var propImageType: Property = Property()
                                propImageType.name = "imageType"
                                propImageType.value = "jdk"
                                var propJvmImpl: Property = Property()
                                propJvmImpl.name = "jvmImpl"
                                propJvmImpl.value = "hotspot"
                                var props: Properties = Properties(listOf(propPlatform, propImageType, propJvmImpl))
                                var hash: Hash = Hash()
                                hash.sha256 = "abcdefg0123456789"
                                var hashes: Hashes = Hashes(listOf(hash))
                                var er: Reference = Reference(null, hashes)
                                var erl: ExternalReferences = ExternalReferences(listOf(er))
                                var c: Component = Component("comp", "jdk-24.0.2+12", erl, props)
                                var cs: Components = Components(listOf(c))
                                var a: Assessor = Assessor(null, Organization("assessor_org_new"))
                                var ass: Assessors = Assessors(listOf(a))
                                var aff: Affirmation = Affirmation("assessor_affirmation_new")
                                var cl: Claim = Claim(null, "assessor_claim_predicate_new")
                                var cls: Claims = Claims(listOf(cl))
                                var d: Declarations = Declarations(ass, cls, null, Targets(cs), aff)

                                GHCdxa( GitHubId("1"), name, "https://github.com/"+org+"/"+repo+"/blob/main/"+name, "https://github.com/"+org+"/"+repo+"/blob/main/"+name+".sign.pub", Instant.now(), d, null)
                            } else {
                                null
                            }
            }

            val updatedRepo = runCdxaUpdateTest(cdxaRepo, getCdxaSummary, getCdxaByName)

            assertTrue(updatedRepo.repos.size == cdxaRepo.repos.size + 1)
            val addedAtt = updatedRepo.repos.firstOrNull { it.filename == "24/jdk-24.0.2+12/jdk_24_0_2_12_aarch64_linux_Adoptium.xml" }
            assertTrue(addedAtt != null)
            assertTrue(updatedRepo != cdxaRepo)
        }
    }

    @Test
    fun `remove cdxa is removed`() {
        runBlocking {
            val cdxaRepo = AdoptCdxaReposTestDataGenerator.generate()

            val getCdxaSummary: ((String, String, String) -> GHCdxaRepoSummaryData?) = { org, repo, directory ->
                LOGGER.info("getCdxaSummary: "+org+" "+repo+" "+directory)

                var cdxaSummary = GHCdxaSummaryTestDataGenerator.generateGHCdxaRepoSummary(cdxaRepo, directory)

                // Remove the jdk-11 cdxa file from the summary
                if ( cdxaSummary?.repository?.att_object?.entries != null ) {
                    var newEntries = cdxaSummary?.repository?.att_object?.entries?.toMutableList()
                    newEntries?.removeIf { it.name == "11" || it.name == "jdk-11.0.21+8" || it.name.startsWith("cdxa_jdk-11.0.21+8") }
                    cdxaSummary?.repository?.att_object?.entries = newEntries
                }

                LOGGER.info("getCdxaSummary: "+cdxaSummary)

                cdxaSummary
            }

            val getCdxaByName: ((String, String, String) -> GHCdxa?) = { org, repo, name ->
                            val existAtt = cdxaRepo.repos.firstOrNull { it.filename == name }
                            if (existAtt != null) {
                                // Construct GHCdxa equivalent of existing Cdxa
                                var propPlatform: Property = Property()
                                propPlatform.name = "platform"
                                propPlatform.value = existAtt.architecture.toString() + "_" + existAtt.os.toString()
                                var propImageType: Property = Property()
                                propImageType.name = "imageType"
                                propImageType.value = existAtt.image_type.toString()
                                var propJvmImpl: Property = Property()
                                propJvmImpl.name = "jvmImpl"
                                propJvmImpl.value = existAtt.jvm_impl.toString()
                                var props: Properties = Properties(listOf(propPlatform, propImageType, propJvmImpl))
                                var hash: Hash = Hash()
                                hash.sha256 = existAtt.target_checksum
                                var hashes: Hashes = Hashes(listOf(hash))
                                var er: Reference = Reference(null, hashes)
                                var erl: ExternalReferences = ExternalReferences(listOf(er))
                                var c: Component = Component("comp", existAtt.release_name, erl, props)
                                var cs: Components = Components(listOf(c))
                                var a: Assessor = Assessor(null, Organization(existAtt.assessor_org))
                                var ass: Assessors = Assessors(listOf(a))
                                var aff: Affirmation = Affirmation(existAtt.assessor_affirmation)
                                var cl: Claim = Claim(null, existAtt.assessor_claim_predicate)
                                var cls: Claims = Claims(listOf(cl))
                                var d: Declarations = Declarations(ass, cls, null, Targets(cs), aff)

                                GHCdxa( GitHubId(existAtt.id), existAtt.filename, existAtt.cdxa_link, existAtt.cdxa_public_signing_key_link, existAtt.committedDate, d, null)
                            } else {
                                null
                            }
            }

            val updatedRepo = runCdxaUpdateTest(cdxaRepo, getCdxaSummary, getCdxaByName)

            assertTrue(updatedRepo.repos.size == cdxaRepo.repos.size - 1)
            val removedAtt = updatedRepo.repos.firstOrNull { it.featureVersion == 11 }
            assertTrue(removedAtt == null)
            assertTrue(updatedRepo != cdxaRepo)
        }
    }

    @Test
    fun `unchanged cdxa repo has no updates`() {
        runBlocking {
            val cdxaRepo = AdoptCdxaReposTestDataGenerator.generate()

            val getCdxaSummary: ((String, String, String) -> GHCdxaRepoSummaryData?) = { org, repo, directory ->
                LOGGER.info("getCdxaSummary: "+org+" "+repo+" "+directory)

                var cdxaSummary = GHCdxaSummaryTestDataGenerator.generateGHCdxaRepoSummary(cdxaRepo, directory)

                LOGGER.info("getCdxaSummary: "+cdxaSummary)

                cdxaSummary
            }

            val getCdxaByName: ((String, String, String) -> GHCdxa?) = { org, repo, name ->
                            val existAtt = cdxaRepo.repos.firstOrNull { it.filename == name }
                            if (existAtt != null) {
                                // Construct GHCdxa equivalent of existing Cdxa
                                var propPlatform: Property = Property()
                                propPlatform.name = "platform"
                                propPlatform.value = existAtt.architecture.toString() + "_" + existAtt.os.toString()
                                var propImageType: Property = Property()
                                propImageType.name = "imageType"
                                propImageType.value = existAtt.image_type.toString()
                                var propJvmImpl: Property = Property()
                                propJvmImpl.name = "jvmImpl"
                                propJvmImpl.value = existAtt.jvm_impl.toString()
                                var props: Properties = Properties(listOf(propPlatform, propImageType, propJvmImpl))
                                var hash: Hash = Hash()
                                hash.sha256 = existAtt.target_checksum
                                var hashes: Hashes = Hashes(listOf(hash))
                                var er: Reference = Reference(null, hashes)
                                var erl: ExternalReferences = ExternalReferences(listOf(er))
                                var c: Component = Component("comp", existAtt.release_name, erl, props)
                                var cs: Components = Components(listOf(c))
                                var a: Assessor = Assessor(null, Organization(existAtt.assessor_org))
                                var ass: Assessors = Assessors(listOf(a))
                                var aff: Affirmation = Affirmation(existAtt.assessor_affirmation)
                                var cl: Claim = Claim(null, existAtt.assessor_claim_predicate)
                                var cls: Claims = Claims(listOf(cl))
                                var d: Declarations = Declarations(ass, cls, null, Targets(cs), aff)

                                GHCdxa( GitHubId(existAtt.id), existAtt.filename, existAtt.cdxa_link, existAtt.cdxa_public_signing_key_link, existAtt.committedDate, d, null)
                            } else {
                                null
                            }
            }

            val updatedRepo = runCdxaUpdateTest(cdxaRepo, getCdxaSummary, getCdxaByName)

            assertTrue(updatedRepo.repos.size == cdxaRepo.repos.size)
            assertTrue(updatedRepo == cdxaRepo)
            assertTrue(updatedRepo !== cdxaRepo)
        }
    }

    @Test
    fun `removed release disappears`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                var releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!
                )
                )
                ).releases.releases

                if (majorVersion == 11) {
                    releases = releases.minus(releases.first())
                }

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            val removed = repo.repos[11]!!.releases.nodeList.first().release_name

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size - 1)
            assertTrue(updatedRepo.allReleases.nodeList.none { it.release_name == removed })
        }
    }

    private fun runUpdateTest(repo: AdoptRepos, getRepository: (String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository, getById: (GitHubId) -> GHRelease?): AdoptRepos {
        val memoryDb = InMemoryApiPersistence(repo, mockk())

        val apiDataStore: APIDataStore = APIDataStoreImpl(memoryDb)

        val vs = object : UpdatableVersionSupplier {
            override suspend fun updateVersions() {
            }

            override fun getTipVersion(): Int? {
                return 25
            }

            override fun getLtsVersions(): Array<Int> {
                return arrayOf(8, 11, 17, 21)
            }

            override fun getAllVersions(): Array<Int> {
                return repo.repos.keys.toTypedArray()
            }
        }

        val ghClient = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? {
                TODO("Not yet implemented")
            }
        }

        val updater = V3Updater(
            AdoptReposBuilder(
                AdoptRepositoryImpl(
                    object : GitHubApi {
                        override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
                            return getRepository(owner, repoName, filter)
                        }

                        override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                            return GHSummaryTestDataGenerator.generateGHRepositorySummary(getRepository(owner, repoName) { _, _ -> true })
                        }

                        override suspend fun getReleaseById(id: GitHubId): GHRelease? {

                            val result = getById(id)
                            if (result != null) {
                                return result
                            }

                            return repo.allReleases.nodeList.firstOrNull { it.id == id.id }?.let {
                                return GHRelease(GitHubId(it.id), it.release_name, it.release_type == ReleaseType.ea, it.timestamp.dateTime.toString(), it.updated_at.dateTime.toString(), GHAssets(it.binaries.map { binary ->
                                    GHAsset(binary.`package`.name, binary.`package`.size, binary.`package`.link, binary.`package`.download_count, binary.updated_at.dateTime.toString()
                                    )
                                }, PageInfo(false, null), it.binaries.size
                                ), it.id, "/AdoptOpenJDK/openjdk${it.version_data.major}-binaries/releases/tag/jdk8u-2020-01-09-03-36"
                                )
                            }
                        }

                        override suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {
                            return null
                        }
                        override suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {
                            return null
                        }
                    },
                    AdoptReleaseMapperFactory(AdoptBinaryMapper(ghClient), ghClient)
                ),
                vs
            ),
            AdoptCdxaReposBuilder(
                AdoptCdxaRepositoryImpl(
                    object : GitHubApi {
                        override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
                            return mockk()
                        }

                        override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                            return mockk()
                        }

                        override suspend fun getReleaseById(id: GitHubId): GHRelease? {
                            return null
                        }

                        override suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {
                            return null
                        }
                        override suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {
                            return null
                        }
                    },
                    AdoptCdxaMapperFactory(ghClient)
                )
            ),
            apiDataStore,
            memoryDb,
            object : StatsInterface(mockk(), object : DockerStatsInterfaceFactory(mockk(), mockk()) {
                override fun getDockerStatsInterface(): StatsInterface {
                    return mockk()
                }
            }) {
                override suspend fun updateStats() {
                }

                override suspend fun update(repos: AdoptRepos) {
                }
            },
            ReleaseVersionResolver(vs),
            object : AdoptReleaseNotes(
                mockk(),
                mockk(),
                mockk(),
            ) {
                override suspend fun updateReleaseNotes(adoptRepos: AdoptRepos) {}
            },
            vs
        )

        val updatedRepo = updater.runUpdate(repo, AtomicBoolean(true), mockk())
        return updatedRepo
    }

    private fun runCdxaUpdateTest(cdxaRepo: AdoptCdxaRepos,
                   getCdxaSummary: (String, String, String) -> GHCdxaRepoSummaryData?,
                   getCdxaByName: (String, String, String) -> GHCdxa? ): AdoptCdxaRepos {

        val adoptRepos: AdoptRepos = mockk()
        coEvery { adoptRepos.getFeatureRelease(any()) } returns null

        val memoryDb = InMemoryApiPersistence(adoptRepos, cdxaRepo)

        val apiDataStore: APIDataStore = APIDataStoreImpl(memoryDb)
        coEvery { apiDataStore.loadDataFromDb(true, false) } returns AdoptRepos(emptyList())
        coEvery { apiDataStore.loadDataFromDb(true, true) } returns AdoptRepos(emptyList())

        val vs = object : UpdatableVersionSupplier {
            override suspend fun updateVersions() {
            }

            override fun getTipVersion(): Int? {
                return 25
            }

            override fun getLtsVersions(): Array<Int> {
                return arrayOf(8, 11, 17, 21)
            }

            override fun getAllVersions(): Array<Int> {
                return arrayOf(8, 11, 17, 21)
            }
        }

        val ghClient = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? {
                TODO("Not yet implemented")
            }
        }

        val updater = V3Updater(
            AdoptReposBuilder(
                AdoptRepositoryImpl(
                    object : GitHubApi {
                        override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
                            return mockk()
                        }

                        override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                            return mockk()
                        }

                        override suspend fun getReleaseById(id: GitHubId): GHRelease? {
                            return null
                        }

                        override suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {
                            return null
                        }
                        override suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {
                            return null
                        }
                    },
                    AdoptReleaseMapperFactory(AdoptBinaryMapper(ghClient), ghClient)
                ),
                vs
            ),
            AdoptCdxaReposBuilder(
                AdoptCdxaRepositoryImpl(
                    object : GitHubApi {
                        override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
                            return mockk()
                        }

                        override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                            return mockk()
                        }

                        override suspend fun getReleaseById(id: GitHubId): GHRelease? {
                            return null
                        }

                        override suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {
                            return getCdxaSummary(org, repo, directory)
                        }

                        override suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {
                            return getCdxaByName(org, repo, name)
                        }
                    },
                    AdoptCdxaMapperFactory(ghClient)
                )
            ),
            apiDataStore,
            memoryDb,
            object : StatsInterface(mockk(), object : DockerStatsInterfaceFactory(mockk(), mockk()) {
                override fun getDockerStatsInterface(): StatsInterface {
                    return mockk()
                }
            }) {
                override suspend fun updateStats() {
                }

                override suspend fun update(repos: AdoptRepos) {
                }
            },
            ReleaseVersionResolver(vs),
            object : AdoptReleaseNotes(
                mockk(),
                mockk(),
                mockk(),
            ) {
                override suspend fun updateReleaseNotes(adoptRepos: AdoptRepos) {}
            },
            vs
        )

        val updatedCdxaRepo = updater.runCdxaUpdate(cdxaRepo, AtomicBoolean(true), mockk())
        return updatedCdxaRepo
    }

}
