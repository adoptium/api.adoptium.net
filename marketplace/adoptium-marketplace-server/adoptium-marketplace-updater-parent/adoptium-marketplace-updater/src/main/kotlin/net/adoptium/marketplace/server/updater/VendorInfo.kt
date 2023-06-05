package net.adoptium.marketplace.server.updater

import net.adoptium.marketplace.client.signature.SignatureType
import net.adoptium.marketplace.schema.Vendor
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class VendorInfo(
    private val vendor: Vendor,
    private val repoUrl: String? = null,
    private val publicKeys: List<String>? = null,
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
        val keys = getKeys();
        return getUrl() != null && !keys.isNullOrEmpty()
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

    fun getKeys(): List<String> {
        val keysProvided: List<String>? = publicKeys
            ?.map { publicKey ->
                return@map getConfigValue(publicKey)
            }
            ?.filterNotNull()

        val envKeys: List<String> = keysFromEnvVars();

        val allKeys: Sequence<String> = if (keysProvided != null) {
            envKeys
                .asSequence()
                .plus(keysProvided)
        } else {
            envKeys
                .asSequence()
        }

        return allKeys
            .map {
                //look up again as this may point to a file
                return@map getConfigValue(it)
            }
            .map { key ->
                if (key.isNullOrEmpty() || key.startsWith(vendor.name.uppercase() + "_KEY")) {
                    return@map key
                }

                return@map if (!key.contains("BEGIN PUBLIC KEY")) {
                    "-----BEGIN PUBLIC KEY-----\n" +
                        key + "\n" +
                        "-----END PUBLIC KEY-----"
                } else {
                    key
                }
            }
            .filterNotNull()
            .toList()
    }

    private fun keysFromEnvVars(): List<String> {
        var keys = emptyList<String>()
        val key = getConfigValue("{$vendor.name.uppercase()}_KEY")

        if (key != null && System.getenv(key) != null) {
            keys = keys.plus(key)
        }

        var index = 0
        while (System.getenv("{$vendor.name.uppercase()}_KEY_$index") != null) {
            val key = getConfigValue(System.getenv("{$vendor.name.uppercase()}_KEY_$index"))
            if (key != null) {
                keys = keys.plus(key)
            }
            index++
        }

        return keys
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
