package net.adoptium.api.v3.releaseNotes

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.ReleaseNote
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.ReleaseType
import org.slf4j.LoggerFactory
import jakarta.inject.Inject

@ApplicationScoped
open class AdoptReleaseNotes @Inject constructor(
    private var adoptRepository: AdoptRepository,
    private val database: ApiPersistence,
    private val gitHubHtmlClient: GitHubHtmlClient
) {
    private val releaseNoteListType = UpdaterJsonMapper
        .mapper
        .typeFactory
        .constructCollectionType(List::class.java, ReleaseNote::class.java)

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun updateReleaseNotes(
        adoptRepos: AdoptRepos
    ) {
        adoptRepos
            .allReleases
            .getReleases()
            .filter { it.release_type == ReleaseType.ga }
            .filter {
                runBlocking {
                    !hasRelease(GitHubId(it.id))
                }
            }
            .forEach {
                val id = GitHubId(it.id)
                val releaseNotesFile = adoptRepository.getReleaseFilesForId(id)
                    ?.firstOrNull { ghAsset -> ghAsset.name.contains("release-notes") }

                if (releaseNotesFile != null) {
                    val releaseNotesList = getReleaseNotesFor(releaseNotesFile)
                    if (releaseNotesList != null) {
                        val releaseNotes = ReleaseNotes(
                            it.version_data,
                            it.vendor,
                            id.id,
                            it.release_name,
                            releaseNotesList
                        )

                        LOGGER.info("Adding release info for: " + it.release_name)
                        database.putReleaseNote(releaseNotes)
                    }
                }
            }
    }

    private suspend fun getReleaseNotesFor(releaseNotesFile: GHAsset): List<ReleaseNote>? {
        val releaseNoteContents = gitHubHtmlClient.getUrl(releaseNotesFile.downloadUrl)
        return UpdaterJsonMapper.mapper.readValue(releaseNoteContents, releaseNoteListType)
    }

    private suspend fun hasRelease(gitHubId: GitHubId): Boolean {
        return database.hasReleaseNotesForGithubId(gitHubId)
    }
}
