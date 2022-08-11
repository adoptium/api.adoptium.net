package net.adoptium.marketplace.validation;

import net.adoptium.marketplace.client.MarketplaceClient;
import net.adoptium.marketplace.client.signature.SignatureType;
import net.adoptium.marketplace.schema.ReleaseList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class RepoValidationTest {

    @EnabledIfEnvironmentVariable(named = "REPO_URL", matches = ".*")
    @Test
    public void validate() throws Exception {
        String publicKey = System.getenv("REPO_KEY");
        String repoUrl = System.getenv("REPO_URL");

        SignatureType signatureType = SignatureType.BASE64_ENCODED;

        if (System.getenv("REPO_SIGNATURE_TYPE") != null) {
            signatureType = SignatureType.valueOf(System.getenv("REPO_SIGNATURE_TYPE"));
        }

        MarketplaceClient client = MarketplaceClient.build(repoUrl, signatureType, publicKey);
        Assertions.assertTrue(RepoValidationTest.validateRepo(client));
    }

    public static boolean validateRepo(MarketplaceClient client) throws IOException {
        try {
            ReleaseList releaseList = client.readRepositoryData();

            if (releaseList == null || releaseList.getReleases().size() == 0) {
                System.err.println("Validation failed, no releases found");
                return false;
            }
            System.out.println("Found: " + releaseList.getReleases().size() + " releases");
            System.out.println("REPO VALID");

            assertCompositeKeysAreUniq(releaseList);

            return true;
        } catch (Exception e) {
            System.err.println("Validation failed");
            e.printStackTrace();
        }

        return false;
    }

    private static void assertCompositeKeysAreUniq(ReleaseList releaseList) {
        Set<String> keys = new HashSet<>();
        releaseList
            .getReleases()
            .stream()
            .map(it -> it.getReleaseName() + ":" + it.getReleaseLink() + ":" + it.getOpenjdkVersionData())
            .forEach(key -> {
                if (keys.contains(key)) {
                    Assertions.fail("Multiple releases share the same name, link and version ($key)");
                }
                keys.add(key);
            });

    }
}
