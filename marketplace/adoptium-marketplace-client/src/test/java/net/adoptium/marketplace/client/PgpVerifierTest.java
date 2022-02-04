package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.PGPSignatureVerify;
import net.adoptium.marketplace.client.signature.SignatureVerifier;

import java.io.FileInputStream;
import java.io.IOException;

public class PgpVerifierTest extends FullRepoTest {

    private static final byte[] TEST_KEY;

    static {
        byte[] key;

        try {
            key = new FileInputStream("../exampleRepositories/keys/pgp/public.key").readAllBytes();
        } catch (IOException e) {
            key = null;
            System.out.println("Failed to read key");
            e.printStackTrace();
        }
        TEST_KEY = key;
    }

    @Override
    public MarketplaceClient getMarketplaceClient() throws Exception {
        SignatureVerifier sv = PGPSignatureVerify.build(new String(TEST_KEY));
        return new MarketplaceClient(MarketplaceHttpClient.build(sv));
    }
}
