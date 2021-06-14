package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import org.awaitility.Awaitility
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@QuarkusTest
@Ignore("For manual execution")
@AddPackages(value = [ApiPersistence::class])
class TestRunner : BaseTest() {

    companion object {
        @BeforeClass
        @Inject
        fun init(apiPersistence: ApiPersistence) {
            runBlocking {
                val repo = AdoptReposTestDataGenerator.generate()
                // Reset connection
                apiPersistence.updateAllRepos(repo, "")
            }
        }
    }

    @Test
    @Inject
    fun run() {
        Awaitility.await().atMost(Long.MAX_VALUE, TimeUnit.NANOSECONDS).until({ 4 === 5 })
    }
}
