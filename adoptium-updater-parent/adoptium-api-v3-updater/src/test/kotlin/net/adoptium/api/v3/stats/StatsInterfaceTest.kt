package net.adoptium.api.v3.stats

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.stats.cloudflare.CloudflareStatsCalculator
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import net.adoptium.api.v3.stats.dockerstats.DockerStats
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StatsInterfaceTest {

    @Test
    fun `update should continue when cloudflare fails`() = runBlocking {
        val gitHubCalculator = mockk<GitHubDownloadStatsCalculator>()
        val cloudflareCalculator = mockk<CloudflareStatsCalculator>()
        val dockerStats = mockk<DockerStats>()
        val dockerFactory = mockk<DockerStatsInterfaceFactory>()

        coEvery { gitHubCalculator.saveStats(any()) } returns Unit
        coEvery { dockerStats.updateDb() } returns Unit
        coEvery { cloudflareCalculator.updateDb() } throws RuntimeException("Cloudflare API error")
        coEvery { dockerFactory.get() } returns dockerStats

        val statsInterface = StatsInterface(gitHubCalculator, cloudflareCalculator, dockerFactory)
        val repos = mockk<AdoptRepos>()

        statsInterface.update(repos)
        coVerify(exactly = 1) { gitHubCalculator.saveStats(repos) }
        coVerify(exactly = 1) { dockerStats.updateDb() }
        coVerify(exactly = 1) { cloudflareCalculator.updateDb() }
    }

    @Test
    fun `update should continue when docker fails`() = runBlocking {
        val gitHubCalculator = mockk<GitHubDownloadStatsCalculator>()
        val cloudflareCalculator = mockk<CloudflareStatsCalculator>()
        val dockerStats = mockk<DockerStats>()
        val dockerFactory = mockk<DockerStatsInterfaceFactory>()

        coEvery { gitHubCalculator.saveStats(any()) } returns Unit
        coEvery { dockerStats.updateDb() } throws RuntimeException("Docker API error")
        coEvery { cloudflareCalculator.updateDb() } returns Unit
        coEvery { dockerFactory.get() } returns dockerStats

        val statsInterface = StatsInterface(gitHubCalculator, cloudflareCalculator, dockerFactory)
        val repos = mockk<AdoptRepos>()

        statsInterface.update(repos)

        coVerify(exactly = 1) { gitHubCalculator.saveStats(repos) }
        coVerify(exactly = 1) { dockerStats.updateDb() }
        coVerify(exactly = 1) { cloudflareCalculator.updateDb() }
    }

    @Test
    fun `update should continue when github fails`() = runBlocking {

        val gitHubCalculator = mockk<GitHubDownloadStatsCalculator>()
        val cloudflareCalculator = mockk<CloudflareStatsCalculator>()
        val dockerStats = mockk<DockerStats>()
        val dockerFactory = mockk<DockerStatsInterfaceFactory>()

        coEvery { gitHubCalculator.saveStats(any()) } throws RuntimeException("GitHub API error")
        coEvery { dockerStats.updateDb() } returns Unit
        coEvery { cloudflareCalculator.updateDb() } returns Unit
        coEvery { dockerFactory.get() } returns dockerStats

        val statsInterface = StatsInterface(gitHubCalculator, cloudflareCalculator, dockerFactory)
        val repos = mockk<AdoptRepos>()

        statsInterface.update(repos)

        coVerify(exactly = 1) { gitHubCalculator.saveStats(repos) }
        coVerify(exactly = 1) { dockerStats.updateDb() }
        coVerify(exactly = 1) { cloudflareCalculator.updateDb() }
    }

    @Test
    fun `update should call all sources even when multiple fail`() = runBlocking {

        val gitHubCalculator = mockk<GitHubDownloadStatsCalculator>()
        val cloudflareCalculator = mockk<CloudflareStatsCalculator>()
        val dockerStats = mockk<DockerStats>()
        val dockerFactory = mockk<DockerStatsInterfaceFactory>()

        coEvery { gitHubCalculator.saveStats(any()) } throws RuntimeException("GitHub API error")
        coEvery { dockerStats.updateDb() } throws RuntimeException("Docker API error")
        coEvery { cloudflareCalculator.updateDb() } throws RuntimeException("Cloudflare API error")
        coEvery { dockerFactory.get() } returns dockerStats

        val statsInterface = StatsInterface(gitHubCalculator, cloudflareCalculator, dockerFactory)
        val repos = mockk<AdoptRepos>()

        statsInterface.update(repos)

        coVerify(exactly = 1) { gitHubCalculator.saveStats(repos) }
        coVerify(exactly = 1) { dockerStats.updateDb() }
        coVerify(exactly = 1) { cloudflareCalculator.updateDb() }
    }
}
