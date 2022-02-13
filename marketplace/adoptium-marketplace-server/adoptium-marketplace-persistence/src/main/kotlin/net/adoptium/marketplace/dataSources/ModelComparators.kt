package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.schema.Asset
import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.SourcePackage
import net.adoptium.marketplace.schema.VersionData

object ModelComparators {

    private fun <T> compareCollection(a: Collection<T>?, b: Collection<T>?, comparator: Comparator<T>): Int {
        return if (a == null && b != null) {
            -1
        } else if (a != null && b == null) {
            1
        } else if (a == null && b == null) {
            0
        } else if (a!!.size < b!!.size) {
            -1
        } else if (a.size > b.size) {
            1
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

    val VERSION_DATA = compareBy<VersionData?> { it?.openjdk_version }
        .thenBy { it?.build }
        .thenBy { it?.major }
        .thenBy { it?.minor }
        .thenBy { it?.optional }
        .thenBy { it?.patch }
        .thenBy { it?.pre }
        .thenBy { it?.security }

    val SOURCE = compareBy<SourcePackage?> { it?.link }
        .thenBy { it?.name }
        .thenBy { it?.size }

    val BINARY = compareBy<Binary?> { it?.architecture }
        .thenBy { it?.distribution }
        .thenBy { it?.c_lib }
        .thenBy { it?.os }
        .thenBy { it?.image_type }
        .thenBy { it?.jvm_impl }
        .thenBy { it?.scm_ref }
        .thenBy { it?.updated_at }
        .thenBy { it?.project }
        .then { a, b -> ASSET.compare(a?._package, b?._package) }
        .then { a, b -> ASSET.compare(a?.installer, b?.installer) }

    val ASSET = compareBy<Asset?> { it?.link }
        .thenBy { it?.name }
        .thenBy { it?.size }
        .thenBy { it?.checksum }
        .thenBy { it?.checksum_link }
        .thenBy { it?.metadata_link }
        .thenBy { it?.signature_link }

    val RELEASE = compareBy<Release?> { it?.release_link }
        .thenBy { it?.release_name }
        .thenBy { it?.timestamp }
        .thenBy { it?.vendor }
        .then { a, b -> SOURCE.compare(a?.source, b?.source) }
        .then { a, b -> collectionComparator(BINARY).compare(a?.binaries, b?.binaries) }
        .then { a, b -> VERSION_DATA.compare(a?.version_data, b?.version_data) }

    val RELEASE_LIST = compareBy<ReleaseList?> { it?.releases?.size }
        .then { a, b -> collectionComparator(RELEASE).compare(a?.releases, b?.releases) }
}
