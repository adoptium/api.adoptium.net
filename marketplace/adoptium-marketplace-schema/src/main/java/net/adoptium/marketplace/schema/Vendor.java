package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"adoptium", "redhat", "alibaba", "ibm", "microsoft", "azul", "huawei"})
public enum Vendor {
    adoptium, redhat, alibaba, ibm, microsoft, azul, huawei;
}
