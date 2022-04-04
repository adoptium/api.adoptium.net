package net.adoptium.marketplace.server.frontend.filters

/* ktlint-enable no-wildcard-imports */
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.OpenjdkVersionData
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
        val VERSION_COMPARATOR = compareBy<OpenjdkVersionData> { it.major }
            .thenBy { it.minor.orElse(null) }
            .thenBy { it.security.orElse(null) }
            .thenBy { it.patch.orElse(null) }
            .thenBy { it.pre.orElse(null) }
            .thenBy { it.build.orElse(null) }
            .thenBy { it.optional.orElse(null) }
            .thenBy { it.openjdk_version }


        private val TIME_COMPARATOR = compareBy { release: Release -> release.lastUpdatedTimestamp }

        val RELEASE_COMPARATOR = compareBy<Release, OpenjdkVersionData>(VERSION_COMPARATOR) { it.openjdkVersionData }

        private val RELEASE_NAME_COMPARATOR = compareBy { release: Release -> release.releaseName }

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
