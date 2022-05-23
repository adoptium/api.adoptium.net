package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"temurin", "dragonwell", "zulu", "microsoft", "semeru", "bisheng", "redhat"})
public enum Distribution {
    temurin, dragonwell, zulu, microsoft, semeru, bisheng, redhat;
}
