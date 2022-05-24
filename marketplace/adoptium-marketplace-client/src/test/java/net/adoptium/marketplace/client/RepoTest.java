package net.adoptium.marketplace.client;

import net.adoptium.marketplace.client.signature.SignatureType;
import net.adoptium.marketplace.schema.ReleaseList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RepoTest {
    @EnabledIfEnvironmentVariable(named = "VALIDATE_REPO", matches = ".*")
    @Test
    public void validateRepo() throws IOException {
        String publicKey = Files.readString(Path.of("../exampleRepositories/keys/public.pem"));

        String repoUrl = "http://localhost:8000/";

        try {
            MarketplaceClient client = MarketplaceClient.build(repoUrl, SignatureType.BASE64_ENCODED, publicKey);
            ReleaseList releaseList = client.readRepositoryData();

            System.out.println("Found: " + releaseList.getReleases().size() + " releases");
            System.out.println("REPO VALID");
        } catch (Exception e) {
            System.err.println("Validation failed");
            e.printStackTrace();
            Assertions.fail();
        }
    }
}
