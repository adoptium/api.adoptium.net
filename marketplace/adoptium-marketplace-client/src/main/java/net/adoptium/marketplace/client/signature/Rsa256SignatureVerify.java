package net.adoptium.marketplace.client.signature;

import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Rsa256SignatureVerify implements SignatureVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Rsa256SignatureVerify.class.getName());

    public static final String FILE_SUFFIX = "sha256.sign";

    private final PublicKey publicKey;

    public Rsa256SignatureVerify(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public static Rsa256SignatureVerify build(String publicKey) {
        try (PemReader pemReader = new PemReader(new StringReader(publicKey))) {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent());
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return new Rsa256SignatureVerify(factory.generatePublic(spec));
        } catch (GeneralSecurityException | IOException ex) {
            LOGGER.error("Failed to read key", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean verifySignature(String data, String signatureStr) {
        byte[] signature = Base64.getDecoder().decode(signatureStr);

        try {
            Signature signatureSHA256 = Signature.getInstance("SHA256withRSA");
            signatureSHA256.initVerify(publicKey);
            signatureSHA256.update(data.getBytes());
            signatureSHA256.verify(signature);
            return true;
        } catch (GeneralSecurityException ex) {
            LOGGER.error("Signature verification failed.", ex);
            return false;
        }
    }

    @Override
    public String signatureSuffix() {
        return FILE_SUFFIX;
    }
}
