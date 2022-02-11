package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.Rsa256SignatureVerify;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import net.adoptium.marketplace.schema.Release;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Rsa256VerifierTest extends TestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Rsa256VerifierTest.class.getName());

    private static final String KEY;

    static {
        String key;

        try {
            key = new String(new FileInputStream("../exampleRepositories/keys/public.pem").readAllBytes());
        } catch (IOException e) {
            key = null;
            LOGGER.error("Failed to read key", e);
        }
        KEY = key;
    }

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

    public MarketplaceClient getMarketplaceClient() throws Exception {
        SignatureVerifier sv = Rsa256SignatureVerify.build(KEY);
        return new MarketplaceClient(MarketplaceHttpClient.build(sv));
    }
}
