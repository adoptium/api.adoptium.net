package net.adoptium.marketplace.client.signature;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Rsa256SignatureVerify implements SignatureVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(Rsa256SignatureVerify.class.getName());

    private final List<PublicKey> publicKeys;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public Rsa256SignatureVerify(PublicKey publicKeys) {
        this.publicKeys = Collections.singletonList(publicKeys);
    }

    public Rsa256SignatureVerify(List<PublicKey> publicKeys) {
        this.publicKeys = publicKeys;
    }

    public static Rsa256SignatureVerify build(String publicKey) {
        return build(Collections.singletonList(publicKey));
    }

    public static Rsa256SignatureVerify build(List<String> publicKeys) {
        return new Rsa256SignatureVerify(formPublicKey(publicKeys));
    }

    protected static List<PublicKey> formPublicKey(List<String> publicKeys) {
        return publicKeys
            .stream()
            .map(publicKey -> {
                PublicKey key;
                try (PemReader pemReader = new PemReader(new StringReader(publicKey))) {
                    X509EncodedKeySpec spec = new X509EncodedKeySpec(pemReader.readPemObject().getContent());
                    KeyFactory factory = KeyFactory.getInstance("RSA");
                    key = factory.generatePublic(spec);
                    return key;
                } catch (GeneralSecurityException | IOException ex) {
                    LOGGER.error("Failed to read key", ex);
                } catch (NullPointerException npe) {
                    LOGGER.error("Failed to read key " + publicKey);
                    throw npe;
                }
                throw new RuntimeException("Failed to read key " + publicKey);
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean verifySignature(byte[] data, byte[] signatureStr) {
        return publicKeys
            .stream()
            .map(publicKey -> {
                byte[] signature = extractSignature(signatureStr);

                try {
                    Signature signatureSHA256 = Signature.getInstance("SHA256withRSA");
                    signatureSHA256.initVerify(publicKey);
                    signatureSHA256.update(data);
                    return signatureSHA256.verify(signature);
                } catch (GeneralSecurityException ex) {
                    if (publicKeys.size() > 1) {
                        LOGGER.info("Signature verification failed.");
                    } else {
                        LOGGER.error("Signature verification failed.", ex);
                    }
                    return false;
                }
            })
            .anyMatch(verified -> verified == true);
    }

    protected byte[] extractSignature(byte[] signature) {
        // remove newlines
        signature = new String(signature).replace("\n", "").getBytes();

        return Base64.getDecoder().decode(signature);
    }

    @Override
    public String signatureSuffix() {
        return SignatureType.BASE64_ENCODED.getFileExtension();
    }
}
