package net.adoptium.marketplace.utils;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.signature.SignatureConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class TinkKeyGeneration {

    public static void main(String[] args) throws GeneralSecurityException, IOException {

        SignatureConfig.register();

        KeysetHandle keyset = KeysetHandle.generateNew(KeyTemplates.get("ECDSA_P256"));

        System.out.println("Generating key for test assets");

        ByteArrayOutputStream publicKeyBaos = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(keyset.getPublicKeysetHandle(), JsonKeysetWriter.withOutputStream(publicKeyBaos));
        System.out.println("Public key:");
        System.out.println(publicKeyBaos);

        ByteArrayOutputStream privateKeyBaos = new ByteArrayOutputStream();
        CleartextKeysetHandle.write(keyset, JsonKeysetWriter.withOutputStream(privateKeyBaos));
        System.out.println("Private key:");
        System.out.println(privateKeyBaos);
    }
}
