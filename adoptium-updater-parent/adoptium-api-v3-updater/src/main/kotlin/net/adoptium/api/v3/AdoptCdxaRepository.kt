package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryEntry
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.CdxaMapper
import net.adoptium.api.v3.mapping.adopt.AdoptCdxaMapperFactory
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.CdxaRepoVersionSummary
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import org.slf4j.LoggerFactory
import java.time.Instant

interface AdoptCdxaRepository {
    suspend fun getCdxas(): List<Cdxa>
    suspend fun getCdxaByName(vendor: Vendor, owner: String, repoName: String, name: String) : Cdxa?
    suspend fun incrementalUpdate(oldRepo: AdoptCdxaRepos, lastUpdatedAt: UpdatedInfo): List<Cdxa>
}

@ApplicationScoped
open class AdoptCdxaRepositoryImpl @Inject constructor(
    val client: GitHubApi,
    adoptCdxaMapperFactory: AdoptCdxaMapperFactory
) : AdoptCdxaRepository {

    companion object {
        const val ADOPTIUM_ORG = "adoptium"

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val mappers = mapOf(
        ".*/temurin-cdxas/.*".toRegex() to adoptCdxaMapperFactory.get(Vendor.eclipse),
    )

    private fun getMapperForRepo(url: String): CdxaMapper {
        val mapper = mappers
            .entries
            .firstOrNull { url.matches(it.key) }

        if (mapper == null) {
            throw IllegalStateException("No mapper found for repo $url")
        }

        return mapper.value
    }

    override suspend fun getCdxaByName(vendor: Vendor, owner: String, repoName: String, name: String): Cdxa? {
        val cdxa = client.getCdxaByName(owner, repoName, name)

        if ( cdxa != null ) {
            val cdxa_link = owner + "/" + repoName + "/" + name
            LOGGER.info("Retrieved Cdxa for: " + cdxa_link)
            return getMapperForRepo(cdxa_link).toCdxa(vendor, cdxa)
        } else {
            return null
        }
    }

    private fun getRepository(): suspend (Vendor, String, String) -> List<Cdxa> {
        return { vendor: Vendor, owner: String, repoName: String -> getRepository(vendor, owner, repoName) }
    }

    private suspend fun getRepository(vendor: Vendor, owner: String, repoName: String): List<Cdxa> {
        val cdxas = mutableListOf<Cdxa>()

        LOGGER.info("Cdxa getRepository for: " + vendor + " " + owner + "/" + repoName)

        // Repository structure:
        //   <major-version>/<release-tag>/<Cdxas.xml|xml.sign.pub>

        // Get the top major version list
        var majorVersions = mutableListOf<Int>()
        val attSummaryTop = client.getCdxaSummary(owner, repoName, "")
        if ( attSummaryTop != null ) {
          // Determine major versions
          val topDirs = attSummaryTop?.repository?.att_object?.entries //List<GHCdxaRepoSummaryEntry>?
          if ( topDirs != null) {
            for( dir in topDirs ) {
              if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                majorVersions.add(dir.name.toInt())
              }
            }
          }
        }

        // Get the Cdxas for each major version
        for( majorVersion in majorVersions ) {
          cdxas.addAll( getCdxasForVersion( vendor, owner, repoName, majorVersion, null ) )
        }

        return cdxas
    }

    private suspend fun getCdxasForVersion(vendor: Vendor, owner: String, repoName: String, version: Int, afterDate: Instant?): List<Cdxa> {
        LOGGER.info("getCdxasForVersion: "+vendor+" "+owner+" "+repoName+" "+version+" modifiedAfterDate: "+afterDate)
        val cdxas = mutableListOf<GHCdxa>()

        val attSummaryTags = client.getCdxaSummary(owner, repoName, "$version")
        if ( attSummaryTags != null ) {
          val tags = attSummaryTags?.repository?.att_object?.entries //List<GHCdxaRepoSummaryEntry>?
          if ( tags != null) {
            for( tag in tags ) {
              if ( tag.type == "tree" ) {
                val releaseTag = tag.name

                // Get Cdxas for this release tag
                val attEntries = client.getCdxaSummary(owner, repoName, "$version/"+releaseTag)
                if ( attEntries?.repository?.att_object?.entries != null) {
                  // Get last commit update date for this releaseTag
                  var committedDate = instantFromCommittedDate( owner, repoName, attEntries?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate )

                  // Have cdxas for this release being updated?
                  if ( afterDate == null || committedDate == null || committedDate.isAfter(afterDate) ) {
                      for( attXml in attEntries?.repository?.att_object?.entries?: emptyList<GHCdxaRepoSummaryEntry>() ) {
                        // Cdxa documents are .xml blob files
                        if ( attXml.type == "blob" && attXml.name.endsWith(".xml") ) {
                          val cdxa = client.getCdxaByName(owner, repoName, "$version/" + releaseTag + "/" + attXml.name)
                          if ( cdxa != null ) {
                            val cdxa_link = owner + "/" + repoName + "/$version/" + releaseTag + "/" + attXml.name
                            LOGGER.info("Retrieved Cdxa for: " + cdxa_link)
                            cdxas.add(cdxa)
                          }
                        }
                      }
                  }
                }
              }
            }
          }
        }

        return getMapperForRepo(owner + "/" + repoName + "/").toCdxaList(vendor, cdxas)
    }

    private fun getSummaries(): suspend (Vendor, String, String) -> List<CdxaRepoVersionSummary> {
        return { vendor: Vendor, owner: String, repoName: String -> getSummaries(vendor, owner, repoName) }
    }

    private suspend fun getSummaries(vendor: Vendor, owner: String, repoName: String): List<CdxaRepoVersionSummary> {
        val summaries = mutableListOf<CdxaRepoVersionSummary>()

        LOGGER.info("Cdxas getSummaries for: " + vendor + " " + owner + "/" + repoName)

        // Repository structure:
        //   <major-version>/<release-tag>/<Cdxas.xml|xml.sign.pub>

        // Get the top major versions committedDate summary
        val attSummaryTop = client.getCdxaSummary(owner, repoName, "")
        if ( attSummaryTop != null ) {
          // Determine major versions
          val topDirs = attSummaryTop?.repository?.att_object?.entries //List<GHCdxaRepoSummaryEntry>?
          if ( topDirs != null) {
            for( dir in topDirs ) {
              if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                // Get summary of featureVersion so as to get its committedDate
                val attSummaryTags = client.getCdxaSummary(owner, repoName, dir.name)
                val committedDate  = instantFromCommittedDate( owner, repoName, attSummaryTags?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate )
                val releaseTags    = attSummaryTags?.repository?.att_object?.entries?.filter { it.type == "tree" }?.map { it.name } ?: emptyList<String>()

                summaries.add( CdxaRepoVersionSummary(vendor, owner, repoName, dir.name.toInt(), releaseTags, committedDate) )
              }
            }
          }
        }

        return summaries
    }

    private fun instantFromCommittedDate(owner: String, repoName: String, committedDate: String?): Instant? {
        var committedInstant: Instant? = try {
            if ( committedDate != null ) {
                Instant.parse( committedDate )
            } else {
                null
            }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Cannot parse cdxa $owner/$repoName committedDate string: "+committedDate)
            null
        }

        return committedInstant
    }

    override suspend fun incrementalUpdate(
        oldRepo: AdoptCdxaRepos,
        lastUpdatedAt: UpdatedInfo
    ): List<Cdxa> {

        val summaries: List<CdxaRepoVersionSummary> = getDataForEachRepo(
            getSummaries()
        ).await().flatMap { it.toList() }

        var updatedCdxas = oldRepo.repos.toMutableList()
        for( summary in summaries ) {
            if ( summary.committedDate == null || (summary.committedDate as Instant).isAfter( lastUpdatedAt.time.toInstant() ) ) {
                LOGGER.info("incrementalUpdate: version has been updated: "+summary)

                // Get Cdxas for version releaseTags that have been updated after lastUpdatedAt
                val cdxas = getCdxasForVersion( summary.vendor, summary.org, summary.repo, summary.featureVersion, lastUpdatedAt.time.toInstant() )

                val updatedReleases = cdxas.map { it.release_name }.distinct()

                // Remove updated version releases from old repo and replace with updated
                updatedCdxas.removeAll { it.featureVersion == summary.featureVersion && it.release_name in updatedReleases }
                updatedCdxas.addAll( cdxas )

                // Remove any version releases for which there are no longer any cdxas
                updatedCdxas.removeAll { it.featureVersion == summary.featureVersion && it.release_name !in summary.releaseTags }
            }
        }

        // Remove any major versions that have been removed all together..
        val currentVersions = summaries.map { it.featureVersion }.distinct()
        updatedCdxas.removeAll { it.featureVersion !in currentVersions }

        return updatedCdxas
    }

    override suspend fun getCdxas(): List<Cdxa> {
        
        val cdxas: List<Cdxa> = getDataForEachRepo(
            getRepository()
        ).await().flatMap { it.toList() }
                
        return cdxas
    }

    private fun <E> getDataForEachRepo(
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<List<E>> {
        return GlobalScope.async {

            return@async listOf(
                getRepoDataAsync(ADOPTIUM_ORG, Vendor.eclipse, "temurin-cdxas", getFun),
            )
                .map { repo -> repo.await() }
        }
    }

    private fun <E> getRepoDataAsync(
        owner: String,
        vendor: Vendor,
        repoName: String,
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<E> {
        return GlobalScope.async {
            LOGGER.info("getting cdxas for $owner $repoName")
            val cdxas = getFun(vendor, owner, repoName)
            LOGGER.info("Done getting cdxas for $owner $repoName")
            return@async cdxas
        }
    }
}
