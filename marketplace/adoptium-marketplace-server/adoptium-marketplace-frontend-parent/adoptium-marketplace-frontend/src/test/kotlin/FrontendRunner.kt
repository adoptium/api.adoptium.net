import io.quarkus.test.junit.QuarkusTest
import net.adoptium.marketplace.server.frontend.AdoptiumMarketplace
import org.awaitility.Awaitility
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@QuarkusTest
@Disabled("For manual execution")
class FrontendRunner {

    @Test
    @Inject
    fun run() {
        System.setProperty("MONGODB_TEST_CONNECTION_STRING", "mongodb://localhost:22222")
        AdoptiumMarketplace().run { }
        Awaitility.await().atMost(Long.MAX_VALUE, TimeUnit.NANOSECONDS).until({ 4 === 5 })
    }
}
