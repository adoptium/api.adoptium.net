package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"linux", "windows", "mac", "solaris", "aix", "alpine_linux"})
public enum OperatingSystem {
    linux,
    windows,
    mac,
    solaris,
    aix,
    alpine_linux;
}
