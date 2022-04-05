package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.schema.Asset
import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.Installer
import net.adoptium.marketplace.schema.OpenjdkVersionData
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.SourcePackage

object ModelComparators {

    private fun <T> compareCollection(a: Collection<T>?, b: Collection<T>?, comparator: Comparator<T>): Int {
        return if (a == null && b != null) {
            -1
        } else if (a != null && b == null) {
            1
        } else if (a == null && b == null) {
            0
        } else if (a!!.size != b!!.size) {
            a.size - b.size
        } else {
            a.zip(b)
                .map { (c, d) ->
                    return@map comparator.compare(c, d)
                }
                .firstOrNull { it != 0 } ?: 0
        }
    }

    private fun <T> collectionComparator(comparator: Comparator<T>): Comparator<Collection<T>> {
        return Comparator { a, b -> compareCollection(a, b, comparator) }
    }

    val VERSION_DATA = compareBy<OpenjdkVersionData?> { it?.openjdk_version }
        .thenBy { it?.build?.orElse(null) }
        .thenBy { it?.major }
        .thenBy { it?.minor?.orElse(null) }
        .thenBy { it?.optional?.orElse(null) }
        .thenBy { it?.patch?.orElse(null) }
        .thenBy { it?.pre?.orElse(null) }
        .thenBy { it?.security?.orElse(null) }

    val SOURCE = compareBy<SourcePackage?> { it?.link }
        .thenBy { it?.name }

    val BINARY = compareBy<Binary?> { it?.architecture }
        .thenBy { it?.distribution }
        .thenBy { it?.cLib }
        .thenBy { it?.os }
        .thenBy { it?.imageType }
        .thenBy { it?.jvmImpl }
        .thenBy { it?.scmRef }
        .thenBy { it?.timestamp }
        .thenBy { it?.aqavitResultsLink }
        .thenBy { it?.openjdkScmRef }
        .thenBy { it?.tckAffidavitLink }
        .then { a, b -> ASSET.compare(a?.`package`, b?.`package`) }
        .then { a, b -> collectionComparator(INSTALLER).compare(a?.installer, b?.installer) }



    val ASSET = compareBy<Asset?> { it?.link }
        .thenBy { it?.name }
        .thenBy { it?.sha256sum }
        .thenBy { it?.sha256sumLink }
        .thenBy { it?.signatureLink }

    val INSTALLER = compareBy<Installer?> { it?.installerType }
        .then(ASSET)

    val RELEASE = compareBy<Release?> { it?.releaseLink }
        .thenBy { it?.releaseName }
        .thenBy { it?.lastUpdatedTimestamp }
        .thenBy { it?.vendor }
        .thenBy { it?.vendorPublicKeyLink }
        .then { a, b -> SOURCE.compare(a?.source, b?.source) }
        .then { a, b -> collectionComparator(BINARY).compare(a?.binaries, b?.binaries) }
        .then { a, b -> VERSION_DATA.compare(a?.openjdkVersionData, b?.openjdkVersionData) }

    val RELEASE_LIST = compareBy<ReleaseList?> { it?.releases?.size }
        .then { a, b -> collectionComparator(RELEASE).compare(a?.releases, b?.releases) }
}
