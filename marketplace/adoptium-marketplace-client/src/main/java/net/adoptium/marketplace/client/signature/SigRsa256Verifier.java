package net.adoptium.marketplace.client.signature;

import java.security.PublicKey;

public class SigRsa256Verifier extends Rsa256SignatureVerify {
    public SigRsa256Verifier(PublicKey publicKey) {
        super(publicKey);
    }

    public static Rsa256SignatureVerify build(String publicKey) {
        PublicKey key;
        key = formPublicKey(publicKey);
        return new SigRsa256Verifier(key);
    }

    @Override
    protected byte[] extractSignature(byte[] signature) {
        return signature;
    }

    @Override
    public String signatureSuffix() {
        return SignatureType.SIG.getFileExtension();
    }
}
