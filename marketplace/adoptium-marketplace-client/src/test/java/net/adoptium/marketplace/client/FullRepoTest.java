package net.adoptium.marketplace.client;

import net.adoptium.marketplace.schema.Release;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public abstract class FullRepoTest extends TestServer {

    public abstract MarketplaceClient getMarketplaceClient() throws Exception;

    @Test
    public void pullFullRepository() throws Exception, FailedToPullDataException {
        MarketplaceClient client = getMarketplaceClient();

        List<Release> releases = client.readRepositoryData("http://localhost:8080/workingRepository");
        Assertions.assertFalse(releases.isEmpty());
    }


    @Test
    public void pullFullRepositoryWithBadSignatures() throws Exception, FailedToPullDataException {
        MarketplaceClient client = getMarketplaceClient();

        List<Release> releases = client.readRepositoryData("http://localhost:8080/repositoryWithBadSignatures");

        // jdk8u302-b08 is the only release with a valid signature
        Assertions.assertEquals(1, releases.size());
        Assertions.assertEquals("jdk8u302-b08", releases.get(0).getRelease_name());
    }
}
