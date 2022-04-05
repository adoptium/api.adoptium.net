package net.adoptium.marketplace.client.signature;

public interface SignatureVerifier {

    boolean verifySignature(byte[] data, byte[] signature) throws FailedToValidateSignatureException;

    String signatureSuffix();

    static SignatureVerifier build(SignatureType type, String publicKey) {
        switch (type) {
            case SIG -> {
                return SigRsa256Verifier.build(publicKey);
            }
            case BASE64_ENCODED -> {
                return Rsa256SignatureVerify.build(publicKey);
            }
        }

        throw new IllegalArgumentException("Could not build verifier for: " + type.name());
    }
}
