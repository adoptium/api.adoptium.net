package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.FailedToValidateSignatureException;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

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
        httpClient.setFollowRedirects(true);
        httpClient.start();
        return new MarketplaceHttpClient(httpClient, signatureVerifier);
    }

    public String pullAndVerify(String url) throws FailedToPullDataException, FailedToValidateSignatureException {
        String body = getRequest(url);
        String signature = getRequest(url + "." + signatureVerifier.signatureSuffix());

        if (!signatureVerifier.verifySignature(body, signature)) {
            throw new FailedToValidateSignatureException();
        }

        return body;
    }

    private String getRequest(String url) throws FailedToPullDataException {
        try {
            ContentResponse response = httpClient.GET(URI.create(url));
            if (response.getStatus() == HttpStatus.OK_200) {
                return response.getContentAsString();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get url", e);
        }
        throw new FailedToPullDataException(url);
    }
}
