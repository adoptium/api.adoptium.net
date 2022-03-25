package net.adoptium.marketplace.server.frontend.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema
public class ReleaseNameList {

    private final List<String> releases;

    @JsonCreator
    public ReleaseNameList(
            @JsonProperty("releases") List<String> releases
    ) {
        this.releases = releases;
    }

    public List<String> getReleases() {
        return releases;
    }
}
