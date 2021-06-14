package net.adoptopenjdk.api.v3

object OpenApiDocs {

    const val RELASE_NAME =
        """
<p>
    Name of the release as displayed in github or <a href="https://adoptopenjdk.net/">https://adoptopenjdk.net/</a> e.g
    <code>jdk-11.0.4+11, jdk8u172-b00-201807161800</code>.
</p>
<p>
    A list of release names can be obtained from 
    <a href="${ServerConfig.SERVER}/v3/info/release_names">${ServerConfig.SERVER}/v3/info/release_names</a>
</p>
"""

    const val FEATURE_RELEASE =
        """
<p>
    Feature release version you wish to download. Feature versions are whole numbers e.g. <code>8,9,10,11,12,13</code>.
</p>
<p>
    Available Feature versions can be obtained from 
    <a href="${ServerConfig.SERVER}/v3/info/available_releases">${ServerConfig.SERVER}/v3/info/available_releases</a>
</p>
"""

    const val VENDOR =
        """<p>Vendor of the binary. This is the organisation that produced the binary package.</p>"""

    const val VERSION_RANGE =
        """
<p>Java version range (maven style) of versions to include.</p>
<p>e.g: <code><ul><li>11.0.4.1+11.1</li><li>[1.0,2.0)</li><li>(,1.0]</li></ul></code></p>
<p>Details of maven version ranges can be found at 
    <a href="https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html">https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html<a>.
</p>
"""

    const val RELEASE_TYPE =
        """
<p>Type of release. Either a release version, known as General Availability(ga) or an Early Access(ea) </p>
"""
}
