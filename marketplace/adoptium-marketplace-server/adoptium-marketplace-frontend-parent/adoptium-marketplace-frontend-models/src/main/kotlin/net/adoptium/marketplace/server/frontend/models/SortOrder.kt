package net.adoptium.marketplace.server.frontend.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(description = "Sort results either ascending or descending based on requested sort method", type = SchemaType.STRING, enumeration = ["ASC", "DESC"], defaultValue = "DESC")
enum class SortOrder {
    ASC,
    DESC;
}
