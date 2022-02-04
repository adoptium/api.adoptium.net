package net.adoptium.marketplace.client.signature;

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class PGPSignatureVerify implements SignatureVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(PGPSignatureVerify.class.getName());

    private final PGPPublicKey pkey;

    private final List<Integer> ALLOWED_HASH_ALGORITHMS = Arrays.asList(
        HashAlgorithmTags.SHA256,
        HashAlgorithmTags.SHA384,
        HashAlgorithmTags.SHA512
    );

    public PGPSignatureVerify(PGPPublicKey pkey) {
        this.pkey = pkey;
    }

    public static PGPSignatureVerify build(String key) throws PGPException, IOException {

        JcaPGPPublicKeyRingCollection pgpPub;
        try (var inputStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(key.getBytes()))) {
            pgpPub = new JcaPGPPublicKeyRingCollection(inputStream);
        }

        PGPPublicKey pkey = pgpPub.getKeyRings().next().getPublicKey();

        return new PGPSignatureVerify(pkey);
    }

    @Override
    public boolean verifySignature(String data, String signature) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(signature.getBytes())) {
            PGPObjectFactory pgpFact = new BcPGPObjectFactory(PGPUtil.getDecoderStream(input));

            PGPSignatureList signatureList = (PGPSignatureList) pgpFact.nextObject();
            PGPSignature sig = signatureList.get(0);

            if (!ALLOWED_HASH_ALGORITHMS.contains(sig.getHashAlgorithm())) {
                LOGGER.error("Incorrect signature hash algorithm used: {}", sig.getHashAlgorithm());
                return false;
            }

            sig.init(new BcPGPContentVerifierBuilderProvider(), pkey);
            sig.update(data.getBytes());

            return sig.verify();
        } catch (IOException | PGPException e) {
            LOGGER.error("Failed to verify", e);
            return false;
        }
    }

    @Override
    public String signatureSuffix() {
        return "asc";
    }
}
