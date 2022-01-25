package net.adoptium.marketplace.schema;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/*
    Use a schema ref as adopt and adoptium will have a different subset of vendors
*/
public enum Vendor {
    adoptium, adoptopenjdk, openjdk, alibaba, ibm, eclipse;
}
