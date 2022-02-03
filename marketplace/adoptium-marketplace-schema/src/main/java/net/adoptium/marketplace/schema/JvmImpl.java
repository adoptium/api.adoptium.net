package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/*
    Use a schema ref as adopt and adoptium will have a different subset of implementations
*/
@Schema(type = SchemaType.STRING, enumeration = {"hotspot"})
public enum JvmImpl {
    hotspot;
}
