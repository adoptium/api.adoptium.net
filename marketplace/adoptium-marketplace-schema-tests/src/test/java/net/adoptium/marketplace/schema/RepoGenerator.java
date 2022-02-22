package net.adoptium.marketplace.schema;

import java.time.Instant;
import java.util.Date;
import java.util.List;

public class RepoGenerator {

    public static ReleaseList generate() {
        return generate(null);
    }

    public static ReleaseList generate(String releaseName) {
        return new ReleaseList(
            List.of(
                new Release(
                    "https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.1%2B12",
                    releaseName == null ? "jdk-17.0.1+12" : releaseName,
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
                                "d7eefa08d893d1ae263dc4ba427baa67b3cb9d48e1151654e423007fb2801358",
                                "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz.sha256.txt",
                                null,
                                "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-jre_x64_mac_hotspot_17.0.1_12.tar.gz.json"
                            ),
                            null,
                            Date.from(Instant.now()),
                            "scmref",
                            Project.jdk,
                            Distribution.temurin
                        )
                    ),
                    Vendor.adoptium,
                    new VersionData(
                        8,
                        1,
                        2,
                        null,
                        null,
                        1,
                        "foo",
                        "bar"
                    ),
                    new SourcePackage(
                        "OpenJDK17U-sources_17.0.1_12.tar.gz",
                        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.1%2B12/OpenJDK17U-sources_17.0.1_12.tar.gz"
                    )
                )
            )
        );
    }
}
