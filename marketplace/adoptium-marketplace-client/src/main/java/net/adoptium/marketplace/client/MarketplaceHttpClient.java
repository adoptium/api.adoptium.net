package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.FailedToValidateSignatureException;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;

public class MarketplaceHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceHttpClient.class.getName());

    private final HttpClient httpClient;
    private final SignatureVerifier signatureVerifier;

    private final boolean ALLOW_FILE_BASED_URL = System.getenv().containsKey("ALLOW_FILE_BASED_URL");

    public MarketplaceHttpClient(HttpClient httpClient, SignatureVerifier signatureVerifier) {
        this.httpClient = httpClient;
        this.signatureVerifier = signatureVerifier;
    }

    public static MarketplaceHttpClient build(SignatureVerifier signatureVerifier) throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.setFollowRedirects(true);
        httpClient.start();
        return new MarketplaceHttpClient(httpClient, signatureVerifier);
    }

    public byte[] pullAndVerify(String url) throws FailedToPullDataException, FailedToValidateSignatureException {
        byte[] body = getRequest(url);
        String signatureUrl = url + "." + signatureVerifier.signatureSuffix();
        byte[] signature = getRequest(signatureUrl);

        if (!signatureVerifier.verifySignature(body, signature)) {
            throw new FailedToValidateSignatureException("Failed to verify signature for " + url + " " + signatureUrl);
        }

        return body;
    }

    private byte[] getRequest(String url) throws FailedToPullDataException {
        try {
            if (ALLOW_FILE_BASED_URL && url.startsWith("file:")) {
                File file = new File(URI.create(url));
                return Files.readAllBytes(file.toPath());
            } else {
                ContentResponse response = httpClient.GET(URI.create(url));
                if (response.getStatus() == HttpStatus.OK_200) {
                    return response.getContent();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get url", e);
        }
        throw new FailedToPullDataException(url);
    }
}
