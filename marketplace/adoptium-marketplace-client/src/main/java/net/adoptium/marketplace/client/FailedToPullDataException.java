package net.adoptium.marketplace.client;

public class FailedToPullDataException extends Exception {
    public FailedToPullDataException(Exception e) {
        super(e);
    }

    public FailedToPullDataException(String url) {
        super(url);
    }
}
