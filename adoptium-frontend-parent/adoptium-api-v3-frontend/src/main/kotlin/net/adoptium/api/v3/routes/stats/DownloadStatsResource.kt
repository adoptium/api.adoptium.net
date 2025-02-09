package net.adoptium.api.v3.routes.stats

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.adoptium.api.v3.DownloadStatsInterface
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.StatsSource
import net.adoptium.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import net.adoptium.api.v3.Pagination.defaultPageSize

@Path("/v3/stats/downloads")
@Schema(hidden = true)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class DownloadStatsResource {
    @Schema(hidden = true)
    private val apiDataStore: APIDataStore

    @Schema(hidden = true)
    private val downloadStatsInterface: DownloadStatsInterface

    // Dont convert to primary constructor, @Schema(hidden = true) is not processed correctly on primary constructor
    @Inject
    constructor(
        apiDataStore: APIDataStore,
        downloadStatsInterface: DownloadStatsInterface
    ) {
        this.apiDataStore = apiDataStore
        this.downloadStatsInterface = downloadStatsInterface
    }

    @GET
    @Schema(hidden = true)
    @Path("/total")
    @Operation(summary = "Get download stats", description = "stats", hidden = true)
    fun getTotalDownloadStats(): CompletionStage<Response> {
        return runAsync {
            return@runAsync downloadStatsInterface.getTotalDownloadStats()
        }
    }

    @GET
    @Schema(hidden = true)
    @Path("/total/{feature_version}")
    @Operation(summary = "Get download stats for feature version", description = "stats", hidden = true)
    fun getTotalDownloadStats(
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...)", required = true)
        @PathParam("feature_version")
        featureVersion: Int,

        @Parameter(name = "release_types", description = "List of release types to include in computation (i.e &release_types=ga&release_types=ea)", required = false)
        @QueryParam("release_types")
        @DefaultValue("ga")
        releaseTypes: List<ReleaseType>?
    ): Map<String, Long> {
        val release = apiDataStore.getAdoptRepos().getFeatureRelease(featureVersion)
            ?: throw BadRequestException("Unable to find version $featureVersion")

        return getAdoptReleases(release, releaseTypes, null)
            .map { grouped ->
                Pair(
                    grouped.release_name,
                    grouped.binaries.sumOf {
                        it.download_count
                    }
                )
            }
            .toMap()
    }

    @Throws(BadRequestException::class)
    @GET
    @Schema(hidden = true)
    @Path("/total/{feature_version}/{release_name}")
    @Operation(summary = "Get download stats for feature version", description = "stats", hidden = true)
    fun getTotalDownloadStatsForTag(
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...)", required = true)
        @PathParam("feature_version")
        featureVersion: Int,

        @Parameter(name = "release_name", description = "Release Name i.e jdk-11.0.4+11", required = true)
        @PathParam("release_name")
        releaseName: String,

        @Parameter(name = "release_types", description = "List of release types to include in computation (i.e &release_types=ga&release_types=ea)", required = false)
        @QueryParam("release_types")
        @DefaultValue("ga")
        releaseTypes: List<ReleaseType>?
    ): Map<String, Long> {
        val release = apiDataStore.getAdoptRepos().getFeatureRelease(featureVersion)
            ?: throw BadRequestException("Unable to find version $featureVersion")

        return getAdoptReleases(release, releaseTypes, releaseName)
            .flatMap { it.binaries.asSequence() }
            .flatMap {
                val archive = Pair(it.`package`.name, it.download_count)
                if (it.installer != null) {
                    sequenceOf(archive, Pair(it.installer!!.name, it.installer!!.download_count))
                } else {
                    sequenceOf(archive)
                }
            }
            .toMap()
    }

    private fun getAdoptReleases(release: FeatureRelease, releaseTypes: List<ReleaseType>?, releaseName: String?): Sequence<Release> {
        var releases = release
            .releases
            .getReleases()
            .filter { it.vendor == Vendor.getDefault() }

        if(!releaseTypes.isNullOrEmpty()) {
            releases = releases.filter { releaseTypes.contains(it.release_type) }
        }

        if(releaseName != null) {
            releases = releases.filter { it.release_name == releaseName }
        }

        return releases
    }

    @GET
    @Schema(hidden = true)
    @Path("/tracking")
    @Operation(summary = "Get download stats for feature version", description = "stats", hidden = true)
    fun tracking(
        @Parameter(name = "days", description = "Number of days to display, if used in conjunction with from/to then this will limit the request to x days before the end of the given period", schema = Schema(defaultValue = "30", type = SchemaType.INTEGER), required = false)
        @QueryParam("days")
        @DefaultValue("30")
        days: Int?,
        @Parameter(name = "source", description = "Stats data source", schema = Schema(defaultValue = "all"), required = false)
        @QueryParam("source")
        @DefaultValue("all")
        source: StatsSource?,
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...). Does not use official docker repo stats", required = false)
        @QueryParam("feature_version")
        featureVersion: Int?,
        @Parameter(name = "docker_repo", description = "Docker repo to filter stats by", required = false)
        @QueryParam("docker_repo")
        dockerRepo: String?,
        @Parameter(name = "jvm_impl", description = "JVM Implementation to filter stats by. Does not use official docker repo stats", required = false)
        @QueryParam("jvm_impl")
        jvmImplStr: String?,
        @Parameter(name = "from", description = "Date from which to calculate stats (inclusive)", schema = Schema(example = "YYYY-MM-dd"), required = false)
        @QueryParam("from")
        from: String?,
        @Parameter(name = "to", description = "Date upto which to calculate stats (inclusive)", schema = Schema(example = "YYYY-MM-dd"), required = false)
        @QueryParam("to")
        to: String?
    ): CompletionStage<Response> {
        return runAsync {
            if (dockerRepo != null && source != StatsSource.dockerhub) {
                throw BadRequestException("docker_repo can only be used with source=dockerhub")
            }

            val jvmImpl = parseJvmImpl(jvmImplStr)
            val fromDate = parseDate(from)?.atStartOfDay()?.atZone(TimeSource.ZONE)
            val toDate = parseDate(to)?.plusDays(1)?.atStartOfDay()?.atZone(TimeSource.ZONE)

            return@runAsync downloadStatsInterface.getTrackingStats(days, fromDate, toDate, source, featureVersion, dockerRepo, jvmImpl)
        }
    }

    @GET
    @Schema(hidden = true)
    @Path("/monthly")
    @Operation(summary = "Get download stats for feature version", description = "stats", hidden = true)
    fun monthly(
        @Parameter(name = "source", description = "Stats data source", schema = Schema(defaultValue = "all"), required = false)
        @QueryParam("source")
        @DefaultValue("all")
        source: StatsSource?,
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...). Does not use official docker repo stats", required = false)
        @QueryParam("feature_version")
        featureVersion: Int?,
        @Parameter(name = "docker_repo", description = "Docker repo to filter stats by", required = false)
        @QueryParam("docker_repo")
        dockerRepo: String?,
        @Parameter(name = "jvm_impl", description = "JVM Implementation to filter stats by. Does not use official docker repo stats", required = false)
        @QueryParam("jvm_impl")
        jvmImplStr: String?,
        @Parameter(name = "to", description = "Month from which to calculate stats (inclusive)", schema = Schema(example = "YYYY-MM-dd"), required = false)
        @QueryParam("to")
        to: String?
    ): CompletionStage<Response> {
        return runAsync {
            if (dockerRepo != null && source != StatsSource.dockerhub) {
                throw BadRequestException("docker_repo can only be used with source=dockerhub")
            }

            val jvmImpl = parseJvmImpl(jvmImplStr)
            val toDate = parseDate(to)?.withDayOfMonth(1)?.plusMonths(1)?.atStartOfDay()?.atZone(TimeSource.ZONE)

            return@runAsync downloadStatsInterface.getMonthlyTrackingStats(toDate, source, featureVersion, dockerRepo, jvmImpl)
        }
    }

    private fun parseDate(date: String?): LocalDate? {
        return if (date == null) {
            null
        } else {
            try {
                LocalDate.parse(date)
            } catch (e: Exception) {
                throw BadRequestException("Cannot parse date $date")
            }
        }
    }

    private fun parseJvmImpl(jvmImpl: String?): JvmImpl? {
        if (jvmImpl == null) return null

        try {
            return JvmImpl.valueOf(jvmImpl)
        } catch (e: Exception) {
            throw BadRequestException("jvm_impl not recognized.")
        }
    }

    private inline fun <reified T> runAsync(crossinline doIt: suspend () -> T): CompletionStage<Response> {
        val future = CompletableFuture<Response>()
        GlobalScope.launch {
            try {
                future.complete(Response.ok(doIt()).build())
            } catch (e: BadRequestException) {
                future.complete(Response.status(400).entity(e.message).build())
            } catch (e: Exception) {
                future.complete(Response.status(500).entity("Internal error").build())
            }
        }

        return future
    }
}
