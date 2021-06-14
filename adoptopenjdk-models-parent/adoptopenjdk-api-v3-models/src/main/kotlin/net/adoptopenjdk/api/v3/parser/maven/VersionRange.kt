package net.adoptopenjdk.api.v3.parser.maven

import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.VersionParser.parse
import java.util.*

/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

/*
 * Based on work from the org.apache.maven:maven-artifact project
 */

/**
 * Construct a version range from a specification.
 *
 * @author [Brett Porter](mailto:brett@apache.org)
 */
class VersionRange private constructor(
    val recommendedVersion: VersionData?,
    val restrictions: List<Restriction>?
) {

    fun containsVersion(version: VersionData?): Boolean {
        for (restriction in restrictions!!) {
            if (restriction.containsVersion(version)) {
                return true
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is VersionRange) {
            return false
        }
        return (recommendedVersion == other.recommendedVersion && restrictions == other.restrictions)
    }

    override fun hashCode(): Int {
        var hash = 7
        hash = 31 * hash + (recommendedVersion?.hashCode() ?: 0)
        hash = 31 * hash + (restrictions?.hashCode() ?: 0)
        return hash
    }

    companion object {
        private val CACHE_SPEC = Collections.synchronizedMap(WeakHashMap<String, VersionRange>())

        /**
         *
         *
         * Create a version range from a string representation
         *
         * Some spec examples are:
         *
         *  * `1.0` Version 1.0
         *  * `[1.0,2.0)` Versions 1.0 (included) to 2.0 (not included)
         *  * `[1.0,2.0]` Versions 1.0 to 2.0 (both included)
         *  * `[1.5,)` Versions 1.5 and higher
         *  * `(,1.0],[1.2,)` Versions up to 1.0 (included) and 1.2 or higher
         *
         *
         * @param spec string representation of a version or version range
         * @return a new [VersionRange] object that represents the spec
         * @throws InvalidVersionSpecificationException
         */
        @Throws(InvalidVersionSpecificationException::class)
        fun createFromVersionSpec(spec: String?): VersionRange? {
            if (spec == null) {
                return null
            }
            var cached = CACHE_SPEC[spec]
            if (cached != null) {
                return cached
            }
            val restrictions: MutableList<Restriction> = ArrayList()
            var process: String = spec
            var version: VersionData? = null
            var upperBound: VersionData? = null
            var lowerBound: VersionData? = null
            while (process.startsWith("[") || process.startsWith("(")) {
                val index1 = process.indexOf(')')
                val index2 = process.indexOf(']')
                var index = index2
                if (index2 < 0 || index1 < index2) {
                    if (index1 >= 0) {
                        index = index1
                    }
                }
                if (index < 0) {
                    throw InvalidVersionSpecificationException("Unbounded range: $spec")
                }
                val restriction = parseRestriction(process.substring(0, index + 1))
                if (lowerBound == null) {
                    lowerBound = restriction.lowerBound
                }
                if (upperBound != null) {
                    if (restriction.lowerBound == null || restriction.lowerBound < upperBound) {
                        throw InvalidVersionSpecificationException("Ranges overlap: $spec")
                    }
                }
                restrictions.add(restriction)
                upperBound = restriction.upperBound
                process = process.substring(index + 1).trim { it <= ' ' }
                if (process.isNotEmpty() && process.startsWith(",")) {
                    process = process.substring(1).trim { it <= ' ' }
                }
            }
            if (process.isNotEmpty()) {
                if (restrictions.size > 0) {
                    throw InvalidVersionSpecificationException(
                        "Only fully-qualified sets allowed in multiple set scenario: $spec"
                    )
                } else {
                    version = parse(process, false, true)
                    restrictions.add(Restriction.EVERYTHING)
                }
            }
            cached = VersionRange(version, restrictions)
            CACHE_SPEC[spec] = cached
            return cached
        }

        @Throws(InvalidVersionSpecificationException::class)
        private fun parseRestriction(spec: String): Restriction {
            val lowerBoundInclusive = spec.startsWith("[")
            val upperBoundInclusive = spec.endsWith("]")
            val process = spec.substring(1, spec.length - 1).trim { it <= ' ' }
            val restriction: Restriction
            val index = process.indexOf(',')
            if (index < 0) {
                if (!lowerBoundInclusive || !upperBoundInclusive) {
                    throw InvalidVersionSpecificationException("Single version must be surrounded by []: $spec")
                }
                val version: VersionData = parse(process, false, true)
                restriction = Restriction(version, lowerBoundInclusive, version, upperBoundInclusive)
            } else {
                val lowerBound = process.substring(0, index).trim { it <= ' ' }
                val upperBound = process.substring(index + 1).trim { it <= ' ' }
                if (lowerBound == upperBound) {
                    throw InvalidVersionSpecificationException("Range cannot have identical boundaries: $spec")
                }
                var lowerVersion: VersionData? = null
                if (lowerBound.isNotEmpty()) {
                    lowerVersion = parse(lowerBound, false, true)
                }
                var upperVersion: VersionData? = null
                if (upperBound.isNotEmpty()) {
                    upperVersion = parse(upperBound, false, true)
                }
                if (upperVersion != null && lowerVersion != null && upperVersion < lowerVersion) {
                    throw InvalidVersionSpecificationException("Range defies version ordering: $spec")
                }
                restriction = Restriction(lowerVersion, lowerBoundInclusive, upperVersion, upperBoundInclusive)
            }
            return restriction
        }
    }
}
