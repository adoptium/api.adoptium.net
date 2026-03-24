package net.adoptium.api

import io.mockk.clearMocks
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.XmlMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.CdxaMapper
import net.adoptium.api.v3.mapping.adopt.AdoptCdxaMapperFactory
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory

import java.time.Instant

@TestInstance(Lifecycle.PER_CLASS)
class CdxaMapperTest {

    private val LOGGER = LoggerFactory.getLogger(CdxaMapperTest::class.java)

    private val fakeGithubHtmlClient = mockk<GitHubHtmlClient>()
    private val adoptCdxaMapperFactory = AdoptCdxaMapperFactory(fakeGithubHtmlClient)
    private val adoptCdxaMapper = adoptCdxaMapperFactory.get(Vendor.eclipse)

    companion object {
        val ghCdxa = XmlMapper.mapper.readValue(
            """
<?xml version="1.0" encoding="UTF-8"?>
<bom serialNumber="urn:uuid:8d58d54f-e666-4c12-be06-ecfef6a0f0ab" version="1" xmlns="http://cyclonedx.org/schema/bom/1.6">
  <declarations>
    <assessors>
      <assessor bom-ref="assessor-1">
        <thirdParty>true</thirdParty>
        <organization>
          <name>Acme Inc</name>
        </organization>
      </assessor>
    </assessors>
    <attestations>
      <attestation>
        <summary>Eclipse Temurin Attestation</summary>
        <assessor>assessor-1</assessor>
        <map>
          <claims>
            <claim>claim-1</claim>
          </claims>
        </map>
      </attestation>
    </attestations>
    <claims>
      <claim bom-ref="claim-1">
        <target>target-jdk-1</target>
        <predicate>VERIFIED_REPRODUCIBLE_BUILD</predicate>
        <evidence>evidence-1</evidence>
      </claim>
    </claims>
    <evidence>
       <evidence bom-ref="evidence-1">
          <propertyName>VERIFICATION_LOG</propertyName>
          <data>
            <name>log</name>
            <contents>
              <attachment content-type="text/plain">
Reproducible script output...it is...
successfully compared
123456 files
and is 100% identical!
              </attachment>
            </contents>
          </data>
      </evidence>
    </evidence>
    <targets>
      <components>
        <component type="application" bom-ref="target-jdk-1">
          <name>Temurin jdk-21.0.5+11 aarch64 Linux</name>
          <version>jdk-21.0.5+11</version>
          <externalReferences>
            <reference type="distribution">
              <url>https://api.adoptium.net/v3/binary/version/jdk-21.0.5+11/linux/aarch64/jdk/hotspot/normal/eclipse</url>
              <hashes>
                <hash alg="SHA-256">1234567890123456789012345678901234567890123456789012345678901234</hash>
              </hashes>
            </reference>
          </externalReferences>
          <properties>
               <property name="platform">aarch64_linux</property>
               <property name="imageType">jdk</property>
               <property name="jvmImpl">hotspot</property>
          </properties>
        </component>
      </components>
    </targets>
    <affirmation>
      <statement>Acme confirms a verified reproducible build</statement>
      <signatories/>
    </affirmation>
  </declarations>
</bom>
            """.trimIndent(),
            GHCdxa::class.java
        )

        init {
            ghCdxa.id = GitHubId("1")
            ghCdxa.filename = "filename"
            ghCdxa.linkUrl = "linkUrl"
            ghCdxa.linkSigUrl = "linkSigUrl"
            ghCdxa.committedDate = Instant.parse("2025-09-25T12:00:00Z")
        }
    }

    @BeforeEach
    fun beforeEach() {
        clearMocks(fakeGithubHtmlClient)
    }

    @Test
    fun `Test Cdxa xml mapper parsing`() {

        runBlocking {
            val parsed = adoptCdxaMapper.toCdxa(Vendor.eclipse, ghCdxa)
            LOGGER.info("Parsed Cdxa: ${parsed}")
            assertEquals(GitHubId("1").id.toString(), parsed?.id)
            assertEquals("filename", parsed?.filename)
            assertEquals(Vendor.eclipse, parsed?.vendor)
            assertEquals("jdk-21.0.5+11", parsed?.release_name)
            assertEquals(OperatingSystem.linux, parsed?.os)
            assertEquals(Architecture.aarch64, parsed?.architecture)
            assertEquals(ImageType.jdk, parsed?.image_type)
            assertEquals(JvmImpl.hotspot, parsed?.jvm_impl)
            assertEquals("Acme Inc", parsed?.assessor_org)
            assertEquals("Acme confirms a verified reproducible build", parsed?.assessor_affirmation)
            assertEquals("VERIFIED_REPRODUCIBLE_BUILD", parsed?.assessor_claim_predicate)
            assertEquals("1234567890123456789012345678901234567890123456789012345678901234", parsed?.target_checksum)
            assertEquals("linkUrl", parsed?.cdxa_link)
            assertEquals("linkSigUrl", parsed?.cdxa_sig_link)
            assertEquals(Instant.parse("2025-09-25T12:00:00Z"), parsed?.committedDate)
            
            // Verify evidence parsing
            val evidences = ghCdxa.declarations?.evidences
            assertNotNull(evidences, "Evidences should not be null")
            assertEquals(1, evidences?.evidence?.size, "Should have 1 evidence")
            
            val evidence = evidences?.evidence?.get(0)
            assertNotNull(evidence, "Evidence should not be null")
            assertEquals("evidence-1", evidence?.bomRef, "Evidence bom-ref should be evidence-1")
            assertEquals("VERIFICATION_LOG", evidence?.propertyName, "Evidence propertyName should be VERIFICATION_LOG")
            
            // Verify evidence data section
            val evidenceData = evidence?.data
            assertNotNull(evidenceData, "Evidence data should not be null")
            assertEquals("log", evidenceData?.name, "Evidence data name should be log")
            
            val contents = evidenceData?.contents
            assertNotNull(contents, "Evidence data contents should not be null")
            
            val attachment = contents?.attachment
            assertNotNull(attachment, "Evidence data attachment should not be null")
            assertEquals("text/plain", attachment?.contentType, "Attachment content-type should be text/plain")
            assertNotNull(attachment?.content, "Attachment content should not be null")
            assertTrue(attachment?.content?.contains("Reproducible script output") == true, "Attachment should contain expected text")
            assertTrue(attachment?.content?.contains("successfully compared") == true, "Attachment should contain 'successfully compared'")
            assertTrue(attachment?.content?.contains("123456 files") == true, "Attachment should contain '123456 files'")
            assertTrue(attachment?.content?.contains("100% identical") == true, "Attachment should contain '100% identical'")
            
            // Verify claim evidence reference
            val claims = ghCdxa.declarations?.claims
            assertNotNull(claims, "Claims should not be null")
            assertEquals(1, claims?.claim?.size, "Should have 1 claim")
            
            val claim = claims?.claim?.get(0)
            assertNotNull(claim, "Claim should not be null")
            assertEquals("evidence-1", claim?.evidence, "Claim should reference evidence-1")
            assertEquals("target-jdk-1", claim?.target, "Claim target should be target-jdk-1")
            assertEquals("VERIFIED_REPRODUCIBLE_BUILD", claim?.predicate, "Claim predicate should be VERIFIED_REPRODUCIBLE_BUILD")
        }
    }
}
