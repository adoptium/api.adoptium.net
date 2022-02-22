package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.adoptium.marketplace.client.MarketplaceMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class DeserializationTest {

    @Test
    public void canDeserializeExampleDoc() throws IOException {
        ReleaseList deserialized = MarketplaceMapper.repositoryObjectMapper.readValue(DeserializationTest.class.getResourceAsStream("example.json"), ReleaseList.class);

        Assertions.assertNotNull(deserialized);
    }

    @Test
    public void canSerializeThenDeserialize() throws JsonProcessingException {

        ObjectMapper mapper = MarketplaceMapper.repositoryObjectMapper;

        String serialized = mapper.writeValueAsString(RepoGenerator.generate(""));

        ReleaseList deserialized = mapper.readValue(serialized, ReleaseList.class);

        Assertions.assertNotNull(deserialized);
    }
}
