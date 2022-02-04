package utils;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


/**
 * PGP Signs files in our test assets directory using our test key.
 */
public class PgpSignTestAssets {

    private static final byte[] TEST_KEY;

    static {
        byte[] key;

        try {
            key = new FileInputStream("./marketplace/exampleRepositories/keys/pgp/private.key").readAllBytes();
        } catch (IOException e) {
            key = null;
            System.out.println("Failed to read key");
            e.printStackTrace();
        }
        TEST_KEY = key;
    }

    public static void main(String[] args) throws IOException, PGPException {
        sign("marketplace/exampleRepositories/");
    }

    public static void sign(String path) throws IOException, PGPException {

        JcaPGPSecretKeyRingCollection pgpSecret;
        try (var inputStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(TEST_KEY))) {
            pgpSecret = new JcaPGPSecretKeyRingCollection(inputStream);
        }

        PGPSecretKey key = pgpSecret.getKeyRings().next().getSecretKey();

        PGPPrivateKey pgpPrivKey =
            key.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().build(new char[0]));

        PGPSignatureGenerator sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(
            key.getPublicKey().getAlgorithm(),
            PGPUtil.SHA256
        ));

        Files.walk(Paths.get(path))
            .filter(Files::isRegularFile)
            .filter(file -> SignTestAssets.isNotExcluded(file.toAbsolutePath().toString()))
            .filter(fileName -> fileName.toFile().getName().endsWith(".json"))
            .forEach(file -> {
                String outFile = file.toAbsolutePath() + ".asc";

                try (FileInputStream fis = new FileInputStream(file.toFile());
                     ArmoredOutputStream fos = new ArmoredOutputStream(new FileOutputStream(outFile))) {

                    sGen.init(PGPSignature.BINARY_DOCUMENT, pgpPrivKey);
                    sGen.update(fis.readAllBytes());

                    PGPSignature sig = sGen.generate();
                    fos.write(sig.getEncoded());
                } catch (IOException | PGPException e) {
                    throw new RuntimeException(e);
                }
            });
    }

}
