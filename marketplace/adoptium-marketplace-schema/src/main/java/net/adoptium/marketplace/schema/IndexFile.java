package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/***
 * Index file that vendors will publish. These provide links to either further index files or release files that contain a ReleaseList.
 *
 * i.e:
 *
 * ```
 * {
 *     "indexes": [
 *         "8/index.json",
 *         "17/index.json"
 *     ],
 *     "releases": [
 *         "11/11.0.14+9.json"
 *     ]
 * }
 * ```
 */
@Schema
public class IndexFile {

    public static final String SCHEMA_VERSION_NAME = "schema_version";
    public static final String LATEST_VERSION = "1.0.0";
    @Schema(
        implementation = JvmImpl.class,
        name = SCHEMA_VERSION_NAME,
        defaultValue = LATEST_VERSION,
        example = LATEST_VERSION,
        required = true)
    private final String schemaVersion;

    // Relative path to another index file, links to further index files
    private final List<String> indexes;

    // Relative path to release file, these will contain data as defined by ReleaseList
    private final List<String> releases;

    @JsonCreator
    public IndexFile(
        @JsonProperty(value = SCHEMA_VERSION_NAME) String schemaVersion,
        @JsonProperty(value = "indexes") List<String> indexes,
        @JsonProperty(value = "releases") List<String> releases) {
        this.schemaVersion = schemaVersion;
        this.indexes = indexes;
        this.releases = releases;
    }

    public List<String> getIndexes() {
        return indexes;
    }

    public List<String> getReleases() {
        return releases;
    }

    @JsonProperty(SCHEMA_VERSION_NAME)
    public String getSchemaVersion() {
        return schemaVersion;
    }
}
