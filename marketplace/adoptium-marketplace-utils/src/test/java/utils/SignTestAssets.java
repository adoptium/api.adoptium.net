package utils;

import org.bouncycastle.openpgp.PGPException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * Signs files in our test assets directory using our test key.
 */
public class SignTestAssets {


    //Files with intentionally bad signatures...dont sign them
    public static final List<String> BAD_SIGNATURES_TO_IGNORE = Arrays.asList(
        "repositoryWithBadSignatures/11/index.json",
        "repositoryWithBadSignatures/8/1.8.0_312-b07.json",
        "tink/public.json",
        "tink/private.json"
    );

    public static boolean isNotExcluded(String path) {
        return BAD_SIGNATURES_TO_IGNORE
            .stream()
            .noneMatch(path::endsWith);
    }


    public static void main(String[] args) throws GeneralSecurityException, IOException, PGPException {
        PgpSignTestAssets.sign("marketplace/exampleRepositories/");
        TinkSignTestAssets.sign("marketplace/exampleRepositories/");
    }
}
