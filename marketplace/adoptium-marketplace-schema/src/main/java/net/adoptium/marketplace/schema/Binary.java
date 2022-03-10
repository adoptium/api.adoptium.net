package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;

@Schema(description = "Details of a binary that can be downloaded including either or both a package and platform specific installer")
public class Binary {

    @Schema(implementation = OperatingSystem.class, required = true)
    private final OperatingSystem os;

    @Schema(implementation = Architecture.class, required = true)
    private final Architecture architecture;

    @Schema(implementation = ImageType.class, required = true)
    private final ImageType imageType;

    @Schema(implementation = CLib.class)
    private final CLib cLib;

    @Schema(implementation = JvmImpl.class, required = true)
    private final JvmImpl jvmImpl;

    @Schema(implementation = Package.class, description = "Describes details of the archive", name = "package", required = false)
    @JsonProperty("package")
    private final Package _package;

    @Schema(implementation = Installer.class, description = "Describes details of the installer archive associated with this binary", required = false)
    private final Installer installer;

    @Schema(required = true, description = "Timestamp of the creation time of the binary")
    private final Date timestamp;

    @Schema(description = "Scm reference to the commit inside the vendors own repository upon which this build is based",
        example = "dd28d6d2cde2b931caf94ac2422a2ad082ea62f0beee3bf7057317c53093de93",
        required = true)
    private final String scmRef;

    @Schema(description = "Scm reference to the commit inside the OpenJDK project, upon which this build is based",
        example = "dd28d6d2cde2b931caf94ac2422a2ad082ea62f0beee3bf7057317c53093de93",
        required = true)
    private final String openjdkScmRef;

    @Schema(implementation = Project.class, required = true)
    private final Project project;

    @Schema(implementation = Distribution.class, required = true)
    private final Distribution distribution;

    @Schema(required = true,
        description = "Link to the aquavit results details for this binary",
        example = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz.aqavit.zip")
    private final String aqavitResultsLink;

    @Schema(required = true,
        example = "https://adoptium.net/tck_affidavit.html",
        description = "Link to the tck affidavit for this binary")
    private final String tckAffidavitLink;

    @JsonCreator
    public Binary(
        @JsonProperty("os") OperatingSystem os,
        @JsonProperty("architecture") Architecture architecture,
        @JsonProperty("image_type") ImageType imageType,
        @JsonProperty("c_lib") CLib cLib,
        @JsonProperty("jvm_impl") JvmImpl jvmImpl,
        @JsonProperty("package") Package aPackage,
        @JsonProperty("installer") Installer installer,
        @JsonProperty("timestamp") Date timestamp,
        @JsonProperty("scm_ref") String scmRef,
        @JsonProperty("openjdk_scm_ref") String openjdkScmRef,
        @JsonProperty("project") Project project,
        @JsonProperty("distribution") Distribution distribution,
        @JsonProperty("aqavit_results_link") String aqavitResultsLink,
        @JsonProperty("tck_affidavit_link") String tckAffidavitLink
    ) {
        this.os = os;
        this.architecture = architecture;
        this.imageType = imageType;
        this.cLib = cLib;
        this.jvmImpl = jvmImpl;
        _package = aPackage;
        this.installer = installer;
        this.timestamp = timestamp;
        this.scmRef = scmRef;
        this.openjdkScmRef = openjdkScmRef;
        this.project = project;
        this.distribution = distribution;
        this.aqavitResultsLink = aqavitResultsLink;
        this.tckAffidavitLink = tckAffidavitLink;
    }

    @JsonProperty("os")
    public OperatingSystem getOs() {
        return os;
    }

    @JsonProperty("architecture")
    public Architecture getArchitecture() {
        return architecture;
    }

    @JsonProperty("image_type")
    public ImageType getImageType() {
        return imageType;
    }

    @JsonProperty("c_lib")
    public CLib getCLib() {
        return cLib;
    }

    @JsonProperty("jvm_impl")
    public JvmImpl getJvmImpl() {
        return jvmImpl;
    }

    @JsonProperty("package")
    public Package getPackage() {
        return _package;
    }

    @JsonProperty("installer")
    public Installer getInstaller() {
        return installer;
    }

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    public Date getTimestamp() {
        return timestamp;
    }

    @JsonProperty("scm_ref")
    public String getScmRef() {
        return scmRef;
    }

    @JsonProperty("project")
    public Project getProject() {
        return project;
    }

    @JsonProperty("distribution")
    public Distribution getDistribution() {
        return distribution;
    }

    @JsonProperty("aqavit_results_link")
    public String getAqavitResultsLink() {
        return aqavitResultsLink;
    }

    @JsonProperty("tck_affidavit_link")
    public String getTckAffidavitLink() {
        return tckAffidavitLink;
    }

    @JsonProperty("openjdk_scm_ref")
    public String getOpenjdkScmRef() {
        return openjdkScmRef;
    }
}
