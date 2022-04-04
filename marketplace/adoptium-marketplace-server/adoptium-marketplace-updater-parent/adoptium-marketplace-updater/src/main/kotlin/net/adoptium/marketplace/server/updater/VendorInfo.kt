package net.adoptium.marketplace.server.updater

import net.adoptium.marketplace.client.signature.SignatureType
import net.adoptium.marketplace.schema.Vendor
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class VendorInfo(
    private val vendor: Vendor,
    private val repoUrl: String? = null,
    private val publicKey: String? = null,
    private val signatureType: SignatureType? = null
) {
    companion object {
        private val KEY_DIR: String?

        init {
            val keyDir = System.getProperty("KEY_DIR")

            KEY_DIR = keyDir
        }
    }

    fun valid(): Boolean {
        return getUrl() != null && getKey() != null
    }

    fun getSignatureType(): SignatureType {
        if (signatureType != null) {
            return signatureType
        }

        val signatureTypeConfig = getConfigValue(vendor.name.uppercase() + "_SIGNATURE_TYPE")

        return if (signatureTypeConfig == null) {
            SignatureType.getDefault()
        } else {
            try {
                SignatureType.valueOf(signatureTypeConfig)
            } catch (e: java.lang.IllegalArgumentException) {
                SignatureType.getDefault()
            }
        }
    }

    fun getUrl(): String? {
        var key = getConfigValue(repoUrl)

        if (key == null) {
            key = getConfigValue(vendor.name.uppercase() + "_URL")
        }

        return key ?: repoUrl
    }

    fun getKey(): String? {
        var key = getConfigValue(publicKey)

        if (key == null) {
            key = getConfigValue(vendor.name.uppercase() + "_KEY")

            //look up again as this may point to a file
            if (key != null) {
                key = getConfigValue(key)
            }
        }

        if (key == null || key.isEmpty() || key == vendor.name.uppercase() + "_KEY") {
            return publicKey
        }

        if (!key.contains("BEGIN PUBLIC KEY")) {
            key = "-----BEGIN PUBLIC KEY-----\n" +
                key + "\n" +
                "-----END PUBLIC KEY-----"
        }
        return key
    }

    private fun getConfigValue(keyName: String?): String? {
        if (keyName == null) {
            return null
        }

        if (System.getenv().containsKey(keyName)) {
            return System.getenv()[keyName]!!
        }

        if (System.getProperties().containsKey(keyName)) {
            return System.getProperties()?.getProperty(keyName)!!
        }

        val stream = VendorInfo::class.java.classLoader.getResourceAsStream(keyName)
        if (stream != null) {
            return String(stream.readAllBytes())
        }

        if (KEY_DIR != null && Paths.get(KEY_DIR, keyName).exists()) {
            return Paths.get(KEY_DIR, keyName).readBytes().decodeToString()
        }

        if (Paths.get(keyName).exists()) {
            return Paths.get(keyName).readBytes().decodeToString()
        }

        return keyName
    }
}
