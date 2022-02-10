package utils;

import net.adoptium.marketplace.client.signature.Rsa256SignatureVerify;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Signs files in our test assets directory using our test key.
 */
public class SignTestAssets {


    //Files with intentionally bad signatures...dont sign them
    public static final List<String> BAD_SIGNATURES_TO_IGNORE = Arrays.asList(
            "repositoryWithBadSignatures/11/index.json",
            "repositoryWithBadSignatures/8/1.8.0_312-b07.json"
    );

    private static final PrivateKey TEST_KEY;

    static {
        PrivateKey key;
        try {
            Security.addProvider(new BouncyCastleProvider());
            PKCS8EncodedKeySpec testKey = getPkcs8EncodedKeySpec(new File("./marketplace/exampleRepositories/keys/private.pem"));
            KeyFactory factory = KeyFactory.getInstance("RSA");
            key = factory.generatePrivate(testKey);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            key = null;
        }
        TEST_KEY = key;
    }

    public static boolean isNotExcluded(String path) {
        return BAD_SIGNATURES_TO_IGNORE
                .stream()
                .noneMatch(path::endsWith);
    }

    public static void main(String[] args) throws Exception {
        sign("marketplace/exampleRepositories/");
    }

    private static PKCS8EncodedKeySpec getPkcs8EncodedKeySpec(File file) throws IOException {
        PKCS8EncodedKeySpec key;
        try (FileReader keyReader = new FileReader(file);
             PemReader pemReader = new PemReader(keyReader)) {
            key = new PKCS8EncodedKeySpec(pemReader.readPemObject().getContent());
        }
        return key;
    }

    public static void sign(String path) throws Exception {
        Signature signatureSHA256Java = Signature.getInstance("SHA256withRSA");

        Files.walk(Paths.get(path))
                .filter(Files::isRegularFile)
                .filter(file -> SignTestAssets.isNotExcluded(file.toAbsolutePath().toString()))
                .filter(fileName -> fileName.toFile().getName().endsWith(".json"))
                .forEach(file -> {
                    String outFile = file.toAbsolutePath() + "." + Rsa256SignatureVerify.FILE_SUFFIX;

                    try (FileInputStream fis = new FileInputStream(file.toFile());
                         FileOutputStream fos = new FileOutputStream(outFile)) {

                        signatureSHA256Java.initSign(TEST_KEY);
                        signatureSHA256Java.update(fis.readAllBytes());
                        byte[] sig = signatureSHA256Java.sign();

                        fos.write(Base64.getEncoder().encode(sig));
                    } catch (IOException | SignatureException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
