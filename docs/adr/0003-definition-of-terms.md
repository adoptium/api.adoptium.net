# 1. Record architecture decisions

Date: 2021-03-29

## Status

Proposed

## Context

We need a fairly well understood set of terms to use in the API. The terms are as follows:

- Asset
  - An archive/binary/package that can be downloaded.
- Vendor
  - An organisation that compiles/packages an Asset (i.e Adopt, Redhat, Oracle, Azul, Bellsoft, Amazon etc)
- Distribution
  - The brand name of a project (such as Dragonwell, Zulu,  OpenJ9)
- JVM Implementation
  - The implementation of the JVM (hotspot/openj9)
- Version
  - Version information of the asset. To some extent this is going to be context specific to the type of resource that is being referred to.  Typically this will 
    mostly be the version as defined by Java as the majority of binaries will be Java binaries with their version define by one of the JEPs. However for assets
    such as installers we may need to represent a version for that ecosystem. 
- Feature
  - Miscellaneous properties or functionality that an Asset may posses. For instance a JVM could contain, JavaFX, JFR, DEBUG_SYMBOLS.
- Project 
  - A project such as valhalla, metropolis, loom
- Architecture
  - The architecture for which a given asset is built for, i.e x86/ARM
- Operating System
  - Operating system for which an Asset is built for
- Bundle Type
  - i.e JDK/JRE
- Support Term
  - Rough duration of support that a Vendor is providing for the Asset, i.e LTS/MTS/STS.
  - Note this would not change over time, i.e Java 11 would remain listed as an "LTS" even after it has gone past its end of life. 
- Bitness
  - Word size of the architecture for which the Asset is built, i.e 32/64
- Release Status
  - Is the Asset considered ready for production (i.e GA/EA)
- Snapshot
  - If the Asset produced is built using source considered ready for production i.e was built using whatever state the repo was in when the build happened (i.e nightly).
    This to some extent overlaps with "Release Status", the distinction here is that we may include a feature that is not considered ready for production, however build it
    using source that at a release tag. I.e OpenJ9 produced builds that contained early access support for AArch64 from source that was at a release tag.
- Asset type
  - I.e msi/tar.gz/zip 

## Things to consider:
Should we ban/embrace acronyms and shortened terms? In the past certain terms we have shortened when it is convention to do so, i.e:
- Long Term Stable -> LTS, 
- Operating System -> OS
- Architecture -> arch
- Early access -> EA
Should we continue to do this on a case by case basis or settle on not doing this ever (unless there is some fairly significant reason not to, I am not proposing to 
  include "Microprocessor_without_Interlocked_Pipelined_Stages_sixty_four")

## Decision

We agree to the definitions and will use these terms in the api code, public interface and documentation.

## Consequences
