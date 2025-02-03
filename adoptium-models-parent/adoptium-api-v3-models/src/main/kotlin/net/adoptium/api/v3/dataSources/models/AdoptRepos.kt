package net.adoptium.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.Release
import java.util.function.Predicate

class AdoptRepos {

    val repos: Map<Int, FeatureRelease>

    @JsonIgnore
    val allReleases: Releases

    @JsonCreator
    constructor(
        @JsonProperty("repos")
        @JsonDeserialize(keyAs = Int::class)
        repos: Map<Int, FeatureRelease>
    ) {
        this.repos = repos

        val releases = repos
            .asSequence()
            .map { it.value.releases }
            .flatMap { it.getReleases() }
            .toList()

        allReleases = Releases(releases)
    }

    fun getFeatureRelease(version: Int): FeatureRelease? {
        return repos.get(version)
    }

    constructor(list: List<FeatureRelease>) : this(
        list.associateBy { it.featureVersion }
    )

    fun getReleases(
        releaseFilter: Predicate<Release>,
        sortOrder: SortOrder,
        sortMethod: SortMethod
    ): Sequence<Release> {
        return allReleases.getReleases(releaseFilter, sortOrder, sortMethod)
    }

    fun getFilteredReleases(
        version: Int,
        releaseFilter: Predicate<Release>,
        binaryFilter: Predicate<Binary>,
        sortOrder: SortOrder,
        sortMethod: SortMethod
    ): Sequence<Release> {
        val featureRelease = getFeatureRelease(version) ?: return emptySequence()

        return getFilteredReleases(featureRelease.releases.getReleases(releaseFilter, sortOrder, sortMethod), binaryFilter)
    }

    fun getFilteredReleases(releaseFilter: Predicate<Release>, binaryFilter: Predicate<Binary>, sortOrder: SortOrder, sortMethod: SortMethod): Sequence<Release> {
        return getFilteredReleases(allReleases.getReleases(releaseFilter, sortOrder, sortMethod), binaryFilter)
    }

    fun getFilteredReleases(releases: Sequence<Release>, binaryFilter: Predicate<Binary>): Sequence<Release> {
        return releases
            .map { release ->
                release.filterBinaries(binaryFilter)
            }
            .filter { it.binaries.isNotEmpty() }
    }

    fun addAll(releases: List<Release>): AdoptRepos {
        if (releases.isEmpty()) {
            return this
        }
        return releases
            .fold(this) { repoAcc, newRelease -> repoAcc.addRelease(newRelease.version_data.major, newRelease) }
    }

    fun addRelease(i: Int, r: Release): AdoptRepos {
        return AdoptRepos(repos.plus(Pair(i, repos.getOrDefault(i, FeatureRelease(i, emptyList())).add(listOf(r)))))
    }

    fun removeRelease(i: Int, r: Release): AdoptRepos {
        return AdoptRepos(repos.plus(Pair(i, repos.getValue(i).remove(r.id))))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdoptRepos

        return repos == other.repos
    }

    override fun hashCode(): Int {
        return repos.hashCode()
    }
}
