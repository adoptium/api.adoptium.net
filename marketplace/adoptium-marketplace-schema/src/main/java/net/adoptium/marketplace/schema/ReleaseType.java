package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, defaultValue = "ga", enumeration = {"ga", "ea"})
public enum ReleaseType {
    ga,
    ea;
}
