package net.adoptium.marketplace.client.signature;

import java.util.List;

public interface SignatureVerifier {

    boolean verifySignature(byte[] data, byte[] signature) throws FailedToValidateSignatureException;

    String signatureSuffix();

    static SignatureVerifier build(SignatureType type, List<String> publicKeys) {
        switch (type) {
            case SIG -> {
                return SigRsa256Verifier.build(publicKeys);
            }
            case BASE64_ENCODED -> {
                return Rsa256SignatureVerify.build(publicKeys);
            }
        }

        throw new IllegalArgumentException("Could not build verifier for: " + type.name());
    }
}
