# 1. 0003 Definition of Terms

Date: 2021-03-29

## Status

Proposed

## Context

We need a fairly well understood set of terms to use in the API. The terms are as follows:

- Asset
    - An archive/binary that can be downloaded.
- Vendor
    - An organisation that compiles/packages an Asset (i.e Adopt, Redhat, Oracle, Azul, Bellsoft, Amazon etc)
- Distribution
    - The brand name of a project (such as Dragonwell, Zulu, OpenJ9)
- JVM Implementation
    - The implementation of the JVM (hotspot/openj9)
- Archive
    - An file with an archive format (i.e tar/zip)
- Binary
    - A binary file such as an executable (exe).
- Version
    - Version information of the asset. To some extent this is going to be context specific to the type of resource that is being referred to. Typically this will mostly be the version as defined by Java as the majority of binaries will be Java binaries with their version define by one of the JEPs. However for assets such as installers we may need to represent a version for that ecosystem.
- Installer
    - A platform specific Asset that installs a package into that platform (such as msi/rpm).
- Properties
    - Miscellaneous properties or functionality that an Asset may posses. For instance a JVM could contain, JavaFX, JFR, DEBUG_SYMBOLS.
- Project
    - A project such as valhalla, metropolis, loom
- Architecture
    - The architecture for which a given asset is built for, i.e x86/ARM
- Operating System
    - Operating system for which an Asset is built for
- Image Type
    - i.e JDK/JRE
- Support Term Type
    - Rough duration of support that a Vendor is providing for the Asset, i.e LTS/MTS/STS.
    - Note this would not change over time, i.e Java 11 would remain listed as an "LTS" even after it has gone past its end of life.
- Release Type
    - Is the Asset considered ready for production (i.e GA/PRE_GA/EA)
    - Nightly releases will be EAs
    - Assets that are produced from release sources however contain features that are not considered production ready would be GA.
- Asset type
    - I.e msi/tar.gz/zip

## Decision

We agree to the definitions and will use these terms in the api code, public interface and documentation.

## Consequences
