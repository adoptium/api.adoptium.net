package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.FailedToValidateSignatureException;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MarketplaceHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceHttpClient.class.getName());

    private final HttpClient httpClient;
    private final SignatureVerifier signatureVerifier;

    public MarketplaceHttpClient(HttpClient httpClient, SignatureVerifier signatureVerifier) {
        this.httpClient = httpClient;
        this.signatureVerifier = signatureVerifier;
    }

    public static MarketplaceHttpClient build(SignatureVerifier signatureVerifier) throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        return new MarketplaceHttpClient(httpClient, signatureVerifier);
    }

    public String pullAndVerify(String url) throws FailedToPullDataException {
        try {
            String body = httpClient.GET(URI.create(url)).getContentAsString();
            String signature = httpClient.GET(URI.create(url + "." + signatureVerifier.signatureSuffix())).getContentAsString();

            if (!signatureVerifier.verifySignature(body, signature)) {
                throw new FailedToValidateSignatureException();
            }

            return body;
        } catch (FailedToValidateSignatureException | InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.warn("Failed to read and verify file", e);
            throw new FailedToPullDataException(e);
        }
    }
}
