package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.SignatureType;
import net.adoptium.marketplace.client.signature.SignatureVerifier;
import net.adoptium.marketplace.schema.IndexFile;
import net.adoptium.marketplace.schema.Release;
import net.adoptium.marketplace.schema.ReleaseList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build and read a repository by:
 * <p>
 * var client = MarketplaceClient.build(publicKey);
 * var releases = client.readRepositoryData("https://localhost:8090/repo");
 */
public class MarketplaceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceClient.class.getName());
    private static final String INDEX_FILE = "index.json";

    private final MarketplaceHttpClient marketplaceHttpClient;
    private final String repoAddress;

    public static MarketplaceClient build(String repoAddress, String publicKey) throws Exception {
        return build(repoAddress, SignatureType.getDefault(), publicKey);
    }

    public static MarketplaceClient build(String repoAddress, SignatureType signatureType, String publicKey) throws Exception {
        SignatureVerifier sv = SignatureVerifier.build(signatureType, publicKey);
        return new MarketplaceClient(repoAddress, MarketplaceHttpClient.build(sv));
    }

    public MarketplaceClient(String repoAddress, MarketplaceHttpClient marketplaceHttpClient) {
        this.repoAddress = repoAddress;
        this.marketplaceHttpClient = marketplaceHttpClient;
    }

    public ReleaseList readRepositoryData() throws FailedToPullDataException {
        return new ReleaseList(readRepositoryDataForUrl(repoAddress));
    }

    private List<Release> readRepositoryDataForUrl(String baseUrl) throws FailedToPullDataException {
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
                    return readRepositoryDataForUrl(url).stream();
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
            byte[] data = marketplaceHttpClient.pullAndVerify(url);
            return MarketplaceMapper.repositoryObjectMapper.readValue(data, IndexFile.class);
        } catch (Exception e) {
            throw new FailedToPullDataException(e);
        }
    }

    private List<Release> pullRelease(String url) throws FailedToPullDataException {
        try {
            byte[] data = marketplaceHttpClient.pullAndVerify(url);
            return MarketplaceMapper.repositoryObjectMapper
                .readValue(data, ReleaseList.class)
                .getReleases();
        } catch (Exception e) {
            throw new FailedToPullDataException(e);
        }
    }
}
