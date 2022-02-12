package net.adoptium.marketplace.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.adoptium.marketplace.client.signature.Rsa256SignatureVerify;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import net.adoptium.marketplace.schema.IndexFile;
import net.adoptium.marketplace.schema.Release;
import net.adoptium.marketplace.schema.ReleaseList;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build and read a repository by:
 * <p>
 * var client = MarketplaceClient.build(publicKey);
 * var releases = client.readRepositoryData("https://localhost:8080/repo");
 */
public class MarketplaceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceClient.class.getName());
    private static final String INDEX_FILE = "index.json";

    private final MarketplaceHttpClient marketplaceHttpClient;
    private final ObjectMapper repositoryObjectMapper = new ObjectMapper();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static MarketplaceClient build(String publicKey) throws Exception {
        SignatureVerifier sv = Rsa256SignatureVerify.build(publicKey);
        return new MarketplaceClient(MarketplaceHttpClient.build(sv));
    }

    public MarketplaceClient(MarketplaceHttpClient marketplaceHttpClient) {
        this.marketplaceHttpClient = marketplaceHttpClient;
    }

    public List<Release> readRepositoryData(String baseUrl) throws FailedToPullDataException {
        baseUrl = removeIndexFileFromPath(baseUrl);

        String indexUrl = appendUrl(baseUrl, INDEX_FILE);

        String finalBaseUrl = baseUrl;

        IndexFile index = pullIndex(indexUrl);

        List<Release> releases = new ArrayList<>();

        if (index.getIndexes() != null) {
            releases.addAll(recursivelyPullReleases(finalBaseUrl, index));
        }

        if (index.getReleases() != null) {
            releases.addAll(pullReleases(finalBaseUrl, index.getReleases()));
        }

        return releases;
    }

    private List<Release> recursivelyPullReleases(String finalBaseUrl, IndexFile index) {
        return index
            .getIndexes()
            .stream()
            .flatMap(indexLink -> {
                String url = appendUrl(finalBaseUrl, indexLink);
                try {
                    return readRepositoryData(url).stream();
                } catch (FailedToPullDataException e) {
                    LOGGER.error("Failed to pull file", e);
                }
                return Stream.empty();
            })
            .collect(Collectors.toList());
    }

    private List<Release> pullReleases(String finalBaseUrl, List<String> paths) {
        return paths
            .stream()
            .flatMap(releaseLink -> {
                String url = appendUrl(finalBaseUrl, releaseLink);
                try {
                    return pullRelease(url).stream();
                } catch (FailedToPullDataException e) {
                    LOGGER.warn("Failed to pull data " + url, e);
                }
                return Stream.empty();
            })
            .collect(Collectors.toList());
    }

    private String removeIndexFileFromPath(String baseUrl) {
        if (baseUrl.endsWith(INDEX_FILE)) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1 - INDEX_FILE.length());
        }
        return baseUrl;
    }

    private String appendUrl(String baseUrl, String prefix) {
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        return baseUrl + "/" + prefix;
    }

    private IndexFile pullIndex(String url) throws FailedToPullDataException {
        try {
            String data = marketplaceHttpClient.pullAndVerify(url);
            return repositoryObjectMapper.readValue(data, IndexFile.class);
        } catch (Exception e) {
            throw new FailedToPullDataException(e);
        }
    }

    private List<Release> pullRelease(String url) throws FailedToPullDataException {
        try {
            String data = marketplaceHttpClient.pullAndVerify(url);
            return repositoryObjectMapper
                .readValue(data, ReleaseList.class)
                .getReleases();
        } catch (Exception e) {
            throw new FailedToPullDataException(e);
        }
    }
}
