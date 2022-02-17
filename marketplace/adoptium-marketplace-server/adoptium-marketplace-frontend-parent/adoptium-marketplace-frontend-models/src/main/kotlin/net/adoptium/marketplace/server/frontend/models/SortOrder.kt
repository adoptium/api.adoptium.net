package net.adoptium.marketplace.server.frontend.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, enumeration = ["ASC", "DESC"], defaultValue = "DESC")
enum class SortOrder {
    ASC,
    DESC;
}
