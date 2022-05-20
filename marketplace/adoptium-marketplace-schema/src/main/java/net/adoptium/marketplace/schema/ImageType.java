package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"jdk", "jre"}, example = "jdk")
public enum ImageType {
    jdk,
    jre;
}
