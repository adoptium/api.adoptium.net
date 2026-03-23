package net.adoptium.api.v3.mapping.adopt

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.mapping.CdxaMapper
import org.slf4j.LoggerFactory
import java.util.EnumMap
import java.time.Instant


@ApplicationScoped
open class AdoptCdxaMapperFactory @Inject constructor(
    val htmlClient: GitHubHtmlClient
) {
    private val mappers: MutableMap<Vendor, AdoptCdxaMapper> = EnumMap(Vendor::class.java)

    open fun get(vendor: Vendor): CdxaMapper {
        return if (mappers.containsKey(vendor)) {
            mappers[vendor]!!
        } else {
            val mapper = AdoptCdxaMapper(htmlClient)
            mappers[vendor] = mapper
            mapper
        }
    }
}

private class AdoptCdxaMapper(
    val htmlClient: GitHubHtmlClient
) : CdxaMapper() {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun toCdxaList(vendor: Vendor, ghCdxaAssets: List<GHCdxa>): List<Cdxa> {
        return ghCdxaAssets
            .map { it -> assetToCdxaAsync(vendor, it) }
            .mapNotNull { it.await() }
    }

    override suspend fun toCdxa(vendor: Vendor, ghCdxa: GHCdxa): Cdxa? {
        return assetToCdxaAsync(vendor, ghCdxa).await()
    }

    private fun assetToCdxaAsync(
        vendor: Vendor,
        ghCdxaAsset: GHCdxa
    ): Deferred<Cdxa?> {
        return GlobalScope.async {
            try {
                // Temurin Cdxas (https://github.com/adoptium/temurin-cdxa/blob/main/.github/workflows/validate-cdxa.yml) have:
                //   ONE cdxa
                //   ONE target component
                //   ONE assessor
                //   ONE claim
                //   ONE target component externalReferences reference with a single hash
                //   target component version is of format jdk-$MAJOR_VERSION+$BUILD_NUM or jdk-$MAJOR_VERSION.0.$UPDATE_VERSION+$BUILD_NUM
                
                val releaseName: String? = ghCdxaAsset?.declarations?.targets?.components?.component[0]?.version
                // featureVersion derived from releaseName
                val featureVersion: Int = releaseName?.split("-","+",".")[1]?.toInt() ?: 0

                val assessor_org: String? = ghCdxaAsset?.declarations?.assessors?.assessor[0]?.organization?.name
                val assessor_affirmation: String? = ghCdxaAsset?.declarations?.affirmation?.statement
                val assessor_claim_predicate: String? = ghCdxaAsset?.declarations?.claims?.claim[0]?.predicate
                val target_checksum: String? = ghCdxaAsset?.declarations?.targets?.components?.component[0]?.externalReferences?.reference[0]?.hashes?.hash[0]?.sha256?.uppercase()

                var archStr: String = ""
                var osStr: String = ""
                var imageTypeStr: String = ""
                var jvmImplStr: String = ""
                for (property in ghCdxaAsset?.declarations?.targets?.components?.component[0]?.properties?.property?: emptyList()) {
                    if (property.name == "platform") {
                        val split_platform: List<String>? = property.value?.split("_")
                        if (split_platform != null) {
                            archStr = split_platform[0]
                            osStr   = split_platform[1]
                        }
                    } else if (property.name == "imageType") {
                        if (property.value != null) {
                            imageTypeStr = property.value?:""
                        }
                    } else if (property.name == "jvmImpl") {
                        if (property.value != null) {
                            jvmImplStr = property.value?:""
                        }
                    }
                }

                val arch: Architecture  = Architecture.valueOf(archStr)
                val os: OperatingSystem = OperatingSystem.valueOf(osStr)
                val imageType: ImageType = ImageType.valueOf(imageTypeStr)
                val jvmImpl: JvmImpl = JvmImpl.valueOf(jvmImplStr)
 
                return@async Cdxa(ghCdxaAsset?.id?.id?:"", ghCdxaAsset.filename?:"",
                                         featureVersion, releaseName, os, arch, imageType, jvmImpl,
                                         vendor, target_checksum, assessor_org, assessor_affirmation, assessor_claim_predicate,
                                         ghCdxaAsset.linkUrl?:"", ghCdxaAsset.linkSigUrl?:"", ghCdxaAsset.committedDate?: Instant.now())
            } catch (e: java.lang.Exception) {
                LOGGER.error("Exception mapping cdxa : "+e+" GHCdxa: "+ghCdxaAsset)
                return@async null
            }
        }
    }
}
