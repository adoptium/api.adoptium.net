package net.adoptium.marketplace.server.updater

import net.adoptium.marketplace.schema.Vendor
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class VendorInfo(
    private val vendor: Vendor,
    val repoUrl: String,
    private val publicKey: String
) {

    companion object {
        private val KEY_DIR: String?

        init {
            val keyDir = System.getProperty("KEY_DIR")

            KEY_DIR = keyDir
        }
    }

    fun getKey(): String {
        var key = getKey(publicKey)

        if (key == null) {
            key = getKey(vendor.name.uppercase() + "_KEY")
        }

        return key ?: publicKey
    }

    private fun getKey(keyName: String): String? {
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

        return null
    }
}
