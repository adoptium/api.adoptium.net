package utils;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.subtle.Hex;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tink Signs files in our test assets directory using our test key.
 */
public class TinkSignTestAssets {

    private static final String TEST_KEY;

    static {
        String key;

        try {
            key = new String(new FileInputStream("./marketplace/exampleRepositories/keys/tink/private.json").readAllBytes());
        } catch (IOException e) {
            key = null;
            System.out.println("Failed to read key");
            e.printStackTrace();
        }
        TEST_KEY = key;
    }

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        sign("marketplace/exampleRepositories/workingRepository");
    }

    public static void sign(String path) throws GeneralSecurityException, IOException {

        SignatureConfig.register();
        KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(TEST_KEY));

        PublicKeySign signer = handle.getPrimitive(PublicKeySign.class);

        Files.walk(Paths.get(path))
            .filter(Files::isRegularFile)
            .filter(file -> SignTestAssets.isNotExcluded(file.toAbsolutePath().toString()))
            .filter(fileName -> fileName.toFile().getName().endsWith(".json"))
            .forEach(file -> {
                String sig = file.toFile().getAbsolutePath() + ".tink";
                try (FileInputStream fis = new FileInputStream(file.toFile());
                     FileOutputStream fos = new FileOutputStream(sig)) {

                    byte[] data = fis.readAllBytes();

                    byte[] signature = signer.sign(data);
                    fos.write(Hex.encode(signature).getBytes(UTF_8));
                } catch (IOException | GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
