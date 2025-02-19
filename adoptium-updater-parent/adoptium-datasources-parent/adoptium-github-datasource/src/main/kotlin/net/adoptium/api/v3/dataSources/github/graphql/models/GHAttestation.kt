package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

data class Organization(
        @JacksonXmlProperty(localName = "name")
        var name: String
)

data class Assessor(
        @JacksonXmlProperty(localName = "thirdParty")
        var thirdParty: Boolean,

        @JacksonXmlProperty(localName = "organization")
        var organization: Organization
)

data class Assessors(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var assessor: List<Assessor>
)

data class Claim(
        @JacksonXmlProperty(localName = "target")
        var target: String,

        @JacksonXmlProperty(localName = "predicate")
        var predicate: String
)

data class Claims(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var claim: List<Claim>
)

data class Affirmation(
        @JacksonXmlProperty(localName = "statement")
        var statement: String,
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
        var claims: ClaimRefs
)

data class Attestation(
        @JacksonXmlProperty(localName = "summary")
        var summary: String,

        @JacksonXmlProperty(localName = "assessor")
        var assessor: String,

        @JacksonXmlProperty(localName = "map")
        var map: ClaimMap
)

data class Attestations(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var attestation: List<Attestation>
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
        var url: String,

        @JacksonXmlProperty(localName = "hashes")
        var hashes: Hashes
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
        var name: String,

        @JacksonXmlProperty(localName = "version")
        var version: String,

        @JacksonXmlProperty(localName = "externalReferences")
        var externalReferences: ExternalReferences,

        @JacksonXmlProperty(localName = "properties")
        var properties: Properties
)

data class Components(
        @get:JacksonXmlElementWrapper(useWrapping = false)
        var component: List<Component>
)

data class Targets(
        @JacksonXmlProperty(localName = "components")
        var components: Components
)

data class Declarations(
        @JacksonXmlProperty(localName = "assessors")
        var assessors: Assessors,

        @JacksonXmlProperty(localName = "claims")
        var claims: Claims,

        @JacksonXmlProperty(localName = "attestations")
        var attestations: Attestations,

        @JacksonXmlProperty(localName = "targets")
        var targets: Targets,

        @JacksonXmlProperty(localName = "affirmation")
        var affirmation: Affirmation
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "bom")
data class GHAttestation @JsonCreator constructor(
        @JacksonXmlProperty(localName = "declarations")
        var declarations: Declarations,

        @JacksonXmlProperty(localName = "signature")
        var signature: String? = null
)

