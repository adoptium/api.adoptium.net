package net.adoptium.marketplace.client.signature;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.PublicKeyVerify;
import com.google.crypto.tink.signature.SignatureConfig;
import com.google.crypto.tink.subtle.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class TinkSignatureVerify implements SignatureVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(TinkSignatureVerify.class.getName());

    private final PublicKeyVerify verifier;

    public TinkSignatureVerify(PublicKeyVerify verifier) {
        this.verifier = verifier;
    }

    public static TinkSignatureVerify build(String key) {
        try {
            SignatureConfig.register();
            KeysetHandle handle = CleartextKeysetHandle.read(JsonKeysetReader.withString(key));
            PublicKeyVerify verifier = handle.getPrimitive(PublicKeyVerify.class);
            return new TinkSignatureVerify(verifier);
        } catch (GeneralSecurityException | IOException ex) {
            LOGGER.error("Failed to read key", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean verifySignature(String data, String signatureStr) {
        byte[] signature = Hex.decode(signatureStr);

        try {
            verifier.verify(signature, data.getBytes());
            return true;
        } catch (GeneralSecurityException ex) {
            LOGGER.error("Signature verification failed.");
            return false;
        }
    }

    @Override
    public String signatureSuffix() {
        return "tink";
    }
}
