package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class DeserializationTest {

    @Test
    public void canDeserializeExampleDoc() throws IOException {
        ReleaseList deserialized = new ObjectMapper().readValue(DeserializationTest.class.getResourceAsStream("example.json"), ReleaseList.class);

        Assertions.assertNotNull(deserialized);
    }

    @Test
    public void canSerializeThenDeserialize() throws JsonProcessingException {

        ReleaseList releaseList = new ReleaseList(
                List.of(
                        new Release(
                                "https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.1%2B12",
                                "jdk-17.0.1+12",
                                Date.from(Instant.now()),
                                Date.from(Instant.now()),
                                List.of(
                                        new Binary(
                                                OperatingSystem.linux,
                                                Architecture.riscv64,
                                                ImageType.jdk,
                                                null,
                                                JvmImpl.hotspot,
                                                new Package(
                                                        "OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz",
                                                        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz",
                                                        49381283l,
                                                        "d7eefa08d893d1ae263dc4ba427baa67b3cb9d48e1151654e423007fb2801358",
                                                        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz.sha256.txt",
                                                        null,
                                                        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz.json"
                                                ),
                                                null,
                                                HeapSize.normal,
                                                Date.from(Instant.now()),
                                                "scmref",
                                                Project.jdk
                                        )
                                ),
                                ReleaseType.ga,
                                Vendor.adoptium,
                                new VersionData(
                                        8,
                                        1,
                                        2,
                                        3,
                                        "",
                                        "",
                                        1,
                                        ""
                                ),
                                new SourcePackage(
                                        "OpenJDK17U-sources_17.0.1_12.tar.gz",
                                        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-sources_17.0.1_12.tar.gz",
                                        105458676l
                                )
                        )
                )
        );
        ObjectMapper mapper = new ObjectMapper();

        String serialized = mapper.writeValueAsString(releaseList);

        ReleaseList deserialized = mapper.readValue(serialized, ReleaseList.class);

        Assertions.assertNotNull(deserialized);
    }
}
