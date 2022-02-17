package net.adoptium.marketplace.server.frontend.filters

/* ktlint-enable no-wildcard-imports */
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.VersionData
import net.adoptium.marketplace.server.frontend.models.SortMethod
import net.adoptium.marketplace.server.frontend.models.SortOrder

class ReleaseSorter {
    companion object {
        fun getComparator(order: SortOrder, releaseSortMethod: SortMethod): Comparator<Release> {
            val sorter = if (releaseSortMethod == SortMethod.DEFAULT) {
                VERSION_THEN_TIME_SORTER
            } else {
                TIME_THEN_VERSION_SORTER
            }

            return if (order == SortOrder.DESC) {
                sorter.reversed()
            } else {
                sorter
            }
        }

        // Cant use the default sort as we want to ignore optional
        val VERSION_COMPARATOR = compareBy<VersionData> { it.major }
            .thenBy { it.minor.orElseGet(null) }
            .thenBy { it.security.orElseGet(null) }
            .thenBy { it.patch.orElseGet(null) }
            .thenBy { it.pre.orElseGet(null) }
            .thenBy { it.build.orElseGet(null) }

        private val TIME_COMPARATOR = compareBy { release: Release -> release.timestamp }

        val RELEASE_COMPARATOR = compareBy<Release, VersionData>(VERSION_COMPARATOR) { it.version_data }

        private val RELEASE_NAME_COMPARATOR = compareBy { release: Release -> release.release_name }

        val VERSION_THEN_TIME_SORTER: Comparator<Release> =
            RELEASE_COMPARATOR
                .then(TIME_COMPARATOR)
                .then(RELEASE_NAME_COMPARATOR)

        val TIME_THEN_VERSION_SORTER: Comparator<Release> =
            TIME_COMPARATOR
                .then(RELEASE_COMPARATOR)
                .then(RELEASE_NAME_COMPARATOR)
    }
}
