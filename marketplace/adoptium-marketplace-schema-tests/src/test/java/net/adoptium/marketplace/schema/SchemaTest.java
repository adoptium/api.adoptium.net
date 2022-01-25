package net.adoptium.marketplace.schema;

import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiParser;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;

public class SchemaTest {
    @Test
    public void canParseCurrentJsonSchema() throws IOException {
        try (FileInputStream fis = new FileInputStream("../adoptium-marketplace-schema/target/generated/openapi.json")) {
            OpenAPI schema = OpenApiParser.parse(fis, Format.JSON);
        }
    }

    @Test
    public void canParseCurrentYamlSchema() throws IOException {
        try (FileInputStream fis = new FileInputStream("../adoptium-marketplace-schema/target/generated/openapi.yaml")) {
            OpenAPI schema = OpenApiParser.parse(fis, Format.YAML);
        }
    }
}
