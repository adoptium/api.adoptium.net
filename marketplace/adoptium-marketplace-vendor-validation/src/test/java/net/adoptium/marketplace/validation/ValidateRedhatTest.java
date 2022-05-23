package net.adoptium.marketplace.validation;

import net.adoptium.marketplace.client.MarketplaceClient;
import net.adoptium.marketplace.client.signature.SignatureType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public class ValidateRedhatTest {

    @EnabledIfEnvironmentVariable(named = "VALIDATE_REPO", matches = ".*")
    @Test
    public void validateRedhat() throws Exception {

        String publicKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoWlAyGsyYfaE42vvMjr0\n" +
            "7TZqQdHbVx3r6rxPVjRdgmgFM9UpBmF5ei8Kkf6mbUFLUdWLrAz1Y6J2uHP5uMgV\n" +
            "Hnm3D3Oox/AHwt5Ei/U8xkw3VgUUp5PfQY7ZZFJmTbv7QQcKa5LfqJHLz6tXucuc\n" +
            "F7vZdnJGpm3HUBJIBxhVu0rz7SpQD9QcGq/gBQgmIaFN3jIW9N3NH1ox1GuDJxQw\n" +
            "8xAjYBBX7cihk9CM/6HO73Qq2jptPWC/t0SyUrPWb+DOlNNRK6B8J9nCn7BRaXQq\n" +
            "lfpeoGcr7VqvNQtCHIkyp3+tM2AARp8gOH3202Cm4Yp6J6X8Xyp729KSyn/Fx07t\n" +
            "hKMMw1fFyKthTVS4g1nlVLCvVPMgJFOe1kmrkzAt8y2Tw7ouwEjZ+yTqBI1Law0R\n" +
            "4IkS67lF+QPkMo/ashlC+HwPYSP7dj60P3hzzJkbbaJ2JYgZ8iBRAP0adFvotGVy\n" +
            "FO+gyRxKtjoPlLcAhstIr6dJq55w11MradCeOfQ3/FT+X/oy/9SqexnH0uoP8/jE\n" +
            "CHHM4PcFLslt9MqjgrsdoTO9+TplUviMBq7Q3bZvma5THvPn7lvKdPmhfMFQy1cD\n" +
            "1tNswGNVkkkRVXfMYbGCtoI31T4/VFhiXS6mDUjbPG6zcIo/RaRn9l1GBIRN82sw\n" +
            "7KQA0we/i/+5e+hyfifYQP0CAwEAAQ==\n" +
            "-----END PUBLIC KEY-----";

        String repoUrl = "https://raw.githubusercontent.com/rh-openjdk/marketplace/main/index.json";

        MarketplaceClient client = MarketplaceClient.build(repoUrl, SignatureType.BASE64_ENCODED, publicKey);
        Assertions.assertTrue(RepoValidationTest.validateRepo(client));

    }
}
