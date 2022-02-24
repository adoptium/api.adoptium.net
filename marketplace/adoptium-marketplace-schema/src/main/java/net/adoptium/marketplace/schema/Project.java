package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        type = SchemaType.STRING,
        name = "project",
        description = "Project",
        defaultValue = "jdk",
        enumeration = {"jdk"},
        required = false
)
public enum Project {
    jdk;
}
