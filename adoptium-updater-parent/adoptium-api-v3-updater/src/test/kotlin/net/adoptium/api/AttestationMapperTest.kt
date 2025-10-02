package net.adoptium.api

import io.mockk.clearMocks
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.XmlMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestation
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.AttestationMapper
import net.adoptium.api.v3.mapping.adopt.AdoptAttestationMapperFactory
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
import org.slf4j.LoggerFactory

import java.time.Instant

@TestInstance(Lifecycle.PER_CLASS)
class AttestationMapperTest {

    private val LOGGER = LoggerFactory.getLogger(AttestationMapperTest::class.java)

    private val fakeGithubHtmlClient = mockk<GitHubHtmlClient>()
    private val adoptAttestationMapperFactory = AdoptAttestationMapperFactory(fakeGithubHtmlClient)
    private val adoptAttestationMapper = adoptAttestationMapperFactory.get(Vendor.eclipse)

    companion object {
        val ghAttestation = XmlMapper.mapper.readValue(
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
      </claim>
    </claims>
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
            GHAttestation::class.java
        )

        init {
            ghAttestation.id = GitHubId("1")
            ghAttestation.filename = "filename"
            ghAttestation.linkUrl = "linkUrl"
            ghAttestation.linkSignUrl = "linkSignUrl"
            ghAttestation.committedDate = Instant.parse("2025-09-25T12:00:00Z")
        }
    }

    @BeforeEach
    fun beforeEach() {
        clearMocks(fakeGithubHtmlClient)
    }

    @Test
    fun `Test Attestation xml mapper parsing`() {

        runBlocking {
            val parsed = adoptAttestationMapper.toAttestation(Vendor.eclipse, ghAttestation)
            LOGGER.info("Parsed Attestation: ${parsed}")
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
            assertEquals("linkUrl", parsed?.attestation_link)
            assertEquals("linkSignUrl", parsed?.attestation_public_signing_key_link)
            assertEquals(Instant.parse("2025-09-25T12:00:00Z"), parsed?.committedDate)
        }
    }
}
