package net.adoptium.marketplace.validation;

import net.adoptium.marketplace.client.MarketplaceClient;
import net.adoptium.marketplace.schema.ReleaseList;

import java.io.IOException;

public class RepoValidationTest {
    public static boolean validateRepo(MarketplaceClient client) throws IOException {
        try {
            ReleaseList releaseList = client.readRepositoryData();

            if (releaseList == null || releaseList.getReleases().size() == 0) {
                System.err.println("Validation failed, no releases found");
                return false;
            }
            System.out.println("Found: " + releaseList.getReleases().size() + " releases");
            System.out.println("REPO VALID");

            return true;
        } catch (Exception e) {
            System.err.println("Validation failed");
            e.printStackTrace();
        }

        return false;
    }
}
