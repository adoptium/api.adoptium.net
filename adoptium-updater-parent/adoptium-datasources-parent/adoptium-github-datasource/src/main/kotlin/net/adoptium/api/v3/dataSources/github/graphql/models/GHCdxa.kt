package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import net.adoptium.api.v3.dataSources.models.GitHubId

import java.time.Instant

data class Organization(
        @JacksonXmlProperty(localName = "name")
        var name: String? = null
)

data class Assessor(
        @JacksonXmlProperty(localName = "thirdParty")
        var thirdParty: Boolean? = null,

        @JacksonXmlProperty(localName = "organization")
        var organization: Organization? = null
)

data class Assessors(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var assessor: List<Assessor>
)

data class Claim(
        @JacksonXmlProperty(localName = "target")
        var target: String? = null,

        @JacksonXmlProperty(localName = "predicate")
        var predicate: String? = null,

        @JacksonXmlProperty(localName = "evidence")
        var evidence: String? = null
)

data class Claims(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var claim: List<Claim>
)

// Cannot use "data class" due to known bug with @JacksonXmlText: https://github.com/FasterXML/jackson-module-kotlin/issues/138
class Attachment() {
        @JacksonXmlProperty(isAttribute = true, localName = "content-type")
        var contentType: String? = null
        
        @JacksonXmlText
        var content: String? = null
}

data class Contents(
        @JacksonXmlProperty(localName = "attachment")
        var attachment: Attachment? = null
)

data class EvidenceData(
        @JacksonXmlProperty(localName = "name")
        var name: String? = null,
        
        @JacksonXmlProperty(localName = "contents")
        var contents: Contents? = null
)

data class Evidence(
        @JacksonXmlProperty(isAttribute = true, localName = "bom-ref")
        var bomRef: String? = null,
        
        @JacksonXmlProperty(localName = "propertyName")
        var propertyName: String? = null,
        
        @JacksonXmlProperty(localName = "data")
        var data: EvidenceData? = null
)

data class Evidences(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "evidence")
        var evidence: List<Evidence>
)

data class Affirmation(
        @JacksonXmlProperty(localName = "statement")
        var statement: String? = null,
)

// Cannot use "data class" due to known bug: https://github.com/FasterXML/jackson-module-kotlin/issues/138
@JacksonXmlRootElement(localName = "claim")
class ClaimRef() {
        @JacksonXmlText()
        var claimRef: String? = null
}

data class ClaimRefs(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var claim: List<ClaimRef>
)

data class ClaimMap(
        @JacksonXmlProperty(localName = "claims")
        var claims: ClaimRefs? = null
)

data class Cdxa(
        @JacksonXmlProperty(localName = "summary")
        var summary: String? = null,

        @JacksonXmlProperty(localName = "assessor")
        var assessor: String? = null,

        @JacksonXmlProperty(localName = "map")
        var map: ClaimMap? = null
)

data class Cdxas(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "attestation")
        var cdxa: List<Cdxa>
)

// Cannot use "data class" due to known bug: https://github.com/FasterXML/jackson-module-kotlin/issues/138
@JacksonXmlRootElement(localName = "hash")
class Hash() {
        @JacksonXmlText()
        var sha256: String? = null
}

data class Hashes(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var hash: List<Hash>
) 

data class Reference(
        @JacksonXmlProperty(localName = "url")
        var url: String? = null,

        @JacksonXmlProperty(localName = "hashes")
        var hashes: Hashes? = null
)

data class ExternalReferences(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var reference: List<Reference>
)

// Cannot use "data class" due to known bug: https://github.com/FasterXML/jackson-module-kotlin/issues/138
@JacksonXmlRootElement(localName = "property")
class Property() {
        @JacksonXmlProperty(isAttribute = true)
        var name: String? = null

        @JacksonXmlText()
        var value: String? = null
}

data class Properties(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var property: List<Property>
)

data class Component(
        @JacksonXmlProperty(localName = "name")
        var name: String? = null,

        @JacksonXmlProperty(localName = "version")
        var version: String? = null,

        @JacksonXmlProperty(localName = "externalReferences")
        var externalReferences: ExternalReferences? = null,

        @JacksonXmlProperty(localName = "properties")
        var properties: Properties? = null
)

data class Components(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var component: List<Component>
)

data class Targets(
        @JacksonXmlProperty(localName = "components")
        var components: Components? = null
)

data class Declarations(
        @JacksonXmlProperty(localName = "assessors")
        var assessors: Assessors? = null,

        @JacksonXmlProperty(localName = "claims")
        var claims: Claims? = null,

        @JacksonXmlProperty(localName = "evidence")
        var evidences: Evidences? = null,

        @JacksonXmlProperty(localName = "attestations")
        var cdxas: Cdxas? = null,

        @JacksonXmlProperty(localName = "targets")
        var targets: Targets? = null,

        @JacksonXmlProperty(localName = "affirmation")
        var affirmation: Affirmation? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "bom")
data class GHCdxa @JsonCreator constructor(
        var id: GitHubId?,
        var filename: String?,
        var linkUrl: String?,
        var linkSigUrl: String?,
        var committedDate: Instant?,

        @JacksonXmlProperty(localName = "declarations")
        var declarations: Declarations? = null,

        @JacksonXmlProperty(localName = "signature")
        var signature: String? = null
)

