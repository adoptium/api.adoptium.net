package net.adoptopenjdk.api

import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.test.assertEquals
import net.adoptopenjdk.api.v3.dataSources.UpdaterJsonMapper
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHVersion
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.OperatingSystem
import org.junit.jupiter.api.Test

class MetadataSerializationTest {

    fun generateMetadata(): GHMetaData {
        return GHMetaData(
            "a",
            OperatingSystem.aix,
            Architecture.aarch64,
            "hotspot",
            GHVersion(

                1, 2, 3,
                "a",
                4,
                "b",
                5,
                "c",
                "d"
            ),
            "b",
            "c",
            ImageType.jdk,
            "d"
        )
    }

    @Test
    fun canAddNewUnknownFieldsToMetadata() {

        val metadata = generateMetadata()

        val serialized = UpdaterJsonMapper.mapper.writeValueAsString(metadata)
        var json = UpdaterJsonMapper.mapper.readValue(serialized, ObjectNode::class.java)
        json = json.put("version.foobar", "foo")

        val reparsed = UpdaterJsonMapper.mapper.readValue(json.toString(), GHMetaData::class.java)

        assertEquals(metadata, reparsed)
    }

    @Test
    fun canRemoveWarningFieldsToMetadata() {

        val metadata = generateMetadata()

        val serialized = UpdaterJsonMapper.mapper.writeValueAsString(metadata)
        var json = UpdaterJsonMapper.mapper.readValue(serialized, ObjectNode::class.java)
        json.remove("WARNING")

        val noWarning = GHMetaData(null, metadata.os, metadata.arch, metadata.variant, metadata.version, metadata.scmRef, metadata.version_data, metadata.binary_type, metadata.sha256)

        val reparsed = UpdaterJsonMapper.mapper.readValue(json.toString(), GHMetaData::class.java)

        assertEquals(noWarning, reparsed)
    }
}
