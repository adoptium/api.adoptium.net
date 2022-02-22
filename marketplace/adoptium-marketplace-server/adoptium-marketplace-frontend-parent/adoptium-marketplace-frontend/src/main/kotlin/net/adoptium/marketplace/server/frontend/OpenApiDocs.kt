package net.adoptium.marketplace.server.frontend

object OpenApiDocs {

    const val FEATURE_RELEASE =
        """
<p>
    Feature release version you wish to download. Feature versions are whole numbers e.g. <code>8,9,10,11,12,13</code>.
</p>
<p>
    Available Feature versions can be obtained from 
    <a href="${ServerConfig.SERVER}/v1/info/available_releases">${ServerConfig.SERVER}/v1/info/available_releases</a>
</p>
"""

    const val VENDOR =
        """<p>Vendor of the binary. This is the organisation that produced the binary package.</p>"""

    const val VERSION_RANGE =
        """
Java version range (maven style) of versions to include.

e.g:
* `11.0.4.1+11.1`
* `[1.0,2.0)`
* `(,1.0]`

Details of maven version ranges can be found at
    <https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html>
"""

    const val CLIB_TYPE = "C Lib type, typically would imply image_type has been set to staticlibs"
}
