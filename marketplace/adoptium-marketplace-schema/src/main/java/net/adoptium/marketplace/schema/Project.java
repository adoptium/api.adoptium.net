package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        type = SchemaType.STRING,
        name = "project",
        description = "Project",
        defaultValue = "jdk",
        enumeration = {"jdk", "valhalla", "metropolis", "jfr", "shenandoah"},
        required = false
)
public enum Project {
    jdk,
    valhalla,
    metropolis,
    jfr,
    shenandoah;
}
