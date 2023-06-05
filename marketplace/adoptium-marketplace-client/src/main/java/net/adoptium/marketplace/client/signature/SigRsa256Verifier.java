package net.adoptium.marketplace.client.signature;

import java.security.PublicKey;
import java.util.List;

public class SigRsa256Verifier extends Rsa256SignatureVerify {
    public SigRsa256Verifier(List<PublicKey> publicKeys) {
        super(publicKeys);
    }

    public static Rsa256SignatureVerify build(List<String> publicKeys) {
        List<PublicKey> keys;
        keys = formPublicKey(publicKeys);
        return new SigRsa256Verifier(keys);
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
