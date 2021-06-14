package net.adoptopenjdk.api.v3.models

class ReleaseList {

    val releases: Array<String>

    constructor(releases: Array<String>) {
        this.releases = releases
    }
}
