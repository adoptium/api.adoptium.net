package net.adoptopenjdk.api.v3.models

class Package(
    name: String,
    link: String,
    size: Long,
    checksum: String?,
    checksum_link: String?,
    download_count: Long,
    signature_link: String? = null,
    metadata_link: String?
) : Asset(name, link, size, checksum, checksum_link, signature_link, download_count, metadata_link)
