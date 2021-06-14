package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.V3Updater
import org.awaitility.Awaitility
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.Ignore
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@Ignore("For manual execution")
@AddPackages(value = [V3Updater::class])
class UpdateRunner : BaseTest() {

    @Test
    @Ignore("For manual execution")
    fun run(updater: V3Updater) {
        System.clearProperty("GITHUB_TOKEN")
        updater.run(false)
        Awaitility.await().atMost(Long.MAX_VALUE, TimeUnit.NANOSECONDS).until({ 4 == 5 })
    }
}
