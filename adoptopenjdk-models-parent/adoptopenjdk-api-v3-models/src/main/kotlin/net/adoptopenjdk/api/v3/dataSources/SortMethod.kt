package net.adoptopenjdk.api.v3.dataSources

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING, enumeration = ["DEFAULT", "DATE"], defaultValue = "DEFAULT",
    description = "DEFAULT sort order is by: version, then date, then name, then id. DATE sorts by date, then version, then name, then id."
)
enum class SortMethod {
    DEFAULT,
    DATE;
}
