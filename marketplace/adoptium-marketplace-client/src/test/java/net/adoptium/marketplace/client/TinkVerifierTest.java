package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.SignatureVerifier;
import net.adoptium.marketplace.client.signature.TinkSignatureVerify;

import java.io.FileInputStream;
import java.io.IOException;

public class TinkVerifierTest extends FullRepoTest {

    private static final String KEY;

    static {
        String key;

        try {
            key = new String(new FileInputStream("../exampleRepositories/keys/tink/public.json").readAllBytes());
        } catch (IOException e) {
            key = null;
            System.out.println("Failed to read key");
            e.printStackTrace();
        }
        KEY = key;
    }

    @Override
    public MarketplaceClient getMarketplaceClient() throws Exception {
        SignatureVerifier sv = TinkSignatureVerify.build(KEY);
        return new MarketplaceClient(MarketplaceHttpClient.build(sv));
    }
}
