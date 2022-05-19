package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"x64", "x86", "ppc64", "ppc64le", "s390x", "aarch64", "arm", "sparcv9", "riscv64"})
public enum Architecture {
    x64,
    x86,
    ppc64,
    ppc64le,
    s390x,
    aarch64,
    arm,
    sparcv9,
    riscv64;
}
