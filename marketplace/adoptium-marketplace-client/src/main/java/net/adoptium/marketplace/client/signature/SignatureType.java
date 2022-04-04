package net.adoptium.marketplace.client.signature;

public enum SignatureType {
    BASE64_ENCODED("sha256.sign"),
    SIG("sig");

    private final String fileExtension;

    SignatureType(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public static SignatureType getDefault() {
        return BASE64_ENCODED;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
