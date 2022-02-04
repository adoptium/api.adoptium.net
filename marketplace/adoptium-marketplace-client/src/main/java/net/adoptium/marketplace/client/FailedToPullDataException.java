package net.adoptium.marketplace.client;

public class FailedToPullDataException extends Throwable {
    public FailedToPullDataException(Exception e) {
        super(e);
    }
}
