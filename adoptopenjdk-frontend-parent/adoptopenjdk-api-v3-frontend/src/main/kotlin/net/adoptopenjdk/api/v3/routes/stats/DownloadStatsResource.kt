package net.adoptopenjdk.api.v3.routes.stats

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.adoptopenjdk.api.v3.DownloadStatsInterface
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.StatsSource
import net.adoptopenjdk.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import javax.ws.rs.PathParam
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.BadRequestException
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
    @Operation(summary = "Get download stats for feature verson", description = "stats", hidden = true)
    fun getTotalDownloadStats(
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...)", required = true)
        @PathParam("feature_version")
        featureVersion: Int
    ): Map<String, Long> {
        val release = apiDataStore.getAdoptRepos().getFeatureRelease(featureVersion)
            ?: throw BadRequestException("Unable to find version $featureVersion")

        return getAdoptReleases(release)
            .filter { it.release_type == ReleaseType.ga }
            .map { grouped ->
                Pair(
                    grouped.release_name,
                    grouped.binaries.map {
                        it.download_count + ((it.installer?.download_count) ?: 0L)
                    }.sum()
                )
            }
            .toMap()
    }

    @Throws(BadRequestException::class)
    @GET
    @Schema(hidden = true)
    @Path("/total/{feature_version}/{release_name}")
    @Operation(summary = "Get download stats for feature verson", description = "stats", hidden = true)
    fun getTotalDownloadStatsForTag(
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...)", required = true)
        @PathParam("feature_version")
        featureVersion: Int,
        @Parameter(name = "release_name", description = "Release Name i.e jdk-11.0.4+11", required = true)
        @PathParam("release_name")
        releaseName: String
    ): Map<String, Long> {
        val release = apiDataStore.getAdoptRepos().getFeatureRelease(featureVersion)
            ?: throw BadRequestException("Unable to find version $featureVersion")

        return getAdoptReleases(release)
            .filter { it.release_name == releaseName }
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

    private fun getAdoptReleases(release: FeatureRelease): Sequence<Release> {
        return release
            .releases
            .getReleases()
            .filter { it.vendor == Vendor.getDefault() }
    }

    @GET
    @Schema(hidden = true)
    @Path("/tracking")
    @Operation(summary = "Get download stats for feature verson", description = "stats", hidden = true)
    fun tracking(
        @Parameter(name = "days", description = "Number of days to display, if used in conjunction with from/to then this will limit the request to x days before the end of the given period", schema = Schema(defaultValue = "30", type = SchemaType.INTEGER), required = false)
        @QueryParam("days")
        days: Int?,
        @Parameter(name = "source", description = "Stats data source", schema = Schema(defaultValue = "all"), required = false)
        @QueryParam("source")
        source: StatsSource?,
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...). Does not use offical docker repo stats", required = false)
        @QueryParam("feature_version")
        featureVersion: Int?,
        @Parameter(name = "docker_repo", description = "Docker repo to filter stats by", required = false)
        @QueryParam("docker_repo")
        dockerRepo: String?,
        @Parameter(name = "jvm_impl", description = "JVM Implementation to filter stats by. Does not use offical docker repo stats", required = false)
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
    @Operation(summary = "Get download stats for feature verson", description = "stats", hidden = true)
    fun monthly(
        @Parameter(name = "source", description = "Stats data source", schema = Schema(defaultValue = "all"), required = false)
        @QueryParam("source")
        source: StatsSource?,
        @Parameter(name = "feature_version", description = "Feature version (i.e 8, 9, 10...). Does not use offical docker repo stats", required = false)
        @QueryParam("feature_version")
        featureVersion: Int?,
        @Parameter(name = "docker_repo", description = "Docker repo to filter stats by", required = false)
        @QueryParam("docker_repo")
        dockerRepo: String?,
        @Parameter(name = "jvm_impl", description = "JVM Implementation to filter stats by. Does not use offical docker repo stats", required = false)
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
