import net.adoptium.marketplace.client.signature.SignatureType
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

/**
 * Signs files in our test assets directory using our test key.
 */
object SignTestAssets {
    //Files with intentionally bad signatures...dont sign them
    val BAD_SIGNATURES_TO_IGNORE = listOf(
        "repositoryWithBadSignatures/11/index.json",
        "repositoryWithBadSignatures/8/jdk8u312_b07.json"
    )
    private val TEST_KEY: PrivateKey?

    init {
        if(System.getenv().containsKey("KEY")) {
            TEST_KEY = try {
                Security.addProvider(BouncyCastleProvider())
                val testKey = getPkcs8EncodedKeySpec(System.getenv("KEY"))
                val factory = KeyFactory.getInstance("RSA")
                factory.generatePrivate(testKey)
            } catch (e: IOException) {
                null
            } catch (e: NoSuchAlgorithmException) {
                null
            } catch (e: InvalidKeySpecException) {
                null
            }
        } else {
            TEST_KEY = try {
                Security.addProvider(BouncyCastleProvider())
                val testKey = getPkcs8EncodedKeySpec(File("/tmp/private.pem"))
                val factory = KeyFactory.getInstance("RSA")
                factory.generatePrivate(testKey)
            } catch (e: IOException) {
                null
            } catch (e: NoSuchAlgorithmException) {
                null
            } catch (e: InvalidKeySpecException) {
                null
            }
        }
    }

    fun isNotExcluded(path: String): Boolean {
        return BAD_SIGNATURES_TO_IGNORE
            .stream()
            .noneMatch { suffix: String? -> path.endsWith(suffix!!) }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        sign("marketplace/exampleRepositories/")
    }

    @Throws(IOException::class)
    private fun getPkcs8EncodedKeySpec(file: File): PKCS8EncodedKeySpec {
        var key: PKCS8EncodedKeySpec
        FileReader(file).use { keyReader -> PemReader(keyReader).use { pemReader -> key = PKCS8EncodedKeySpec(pemReader.readPemObject().content) } }
        return key
    }


    @Throws(IOException::class)
    private fun getPkcs8EncodedKeySpec(signingKey: String): PKCS8EncodedKeySpec {
        var key: PKCS8EncodedKeySpec
        PemReader(StringReader(signingKey)).use { pemReader -> key = PKCS8EncodedKeySpec(pemReader.readPemObject().content) }
        return key
    }

    @Throws(Exception::class)
    fun sign(path: String?) {
        val signatureSHA256Java = Signature.getInstance("SHA256withRSA")
        Files.walk(Paths.get(path))
            .filter { path: Path? -> Files.isRegularFile(path) }
            .filter { file: Path -> isNotExcluded(file.toAbsolutePath().toString()) }
            .filter { fileName: Path -> fileName.toFile().name.endsWith(".json") }
            .forEach { file: Path ->
                val outFile = file.toAbsolutePath().toString() + "." + SignatureType.getDefault().fileExtension
                try {
                    FileInputStream(file.toFile()).use { fis ->
                        FileOutputStream(outFile).use { fos ->
                            signatureSHA256Java.initSign(TEST_KEY)
                            signatureSHA256Java.update(fis.readAllBytes())
                            val sig = signatureSHA256Java.sign()
                            fos.write(Base64.getEncoder().encode(sig))
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                } catch (e: SignatureException) {
                    throw RuntimeException(e)
                } catch (e: InvalidKeyException) {
                    throw RuntimeException(e)
                }
            }
    }
}
