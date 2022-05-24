package net.adoptium.marketplace.client.signature;

public class FailedToValidateSignatureException extends Exception {
    public FailedToValidateSignatureException(String message) {
        super(message);
    }
}
