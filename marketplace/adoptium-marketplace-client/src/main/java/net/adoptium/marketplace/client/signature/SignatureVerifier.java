package net.adoptium.marketplace.client.signature;

public interface SignatureVerifier {

    boolean verifySignature(String data, String signature) throws FailedToValidateSignatureException;

    String signatureSuffix();
}
