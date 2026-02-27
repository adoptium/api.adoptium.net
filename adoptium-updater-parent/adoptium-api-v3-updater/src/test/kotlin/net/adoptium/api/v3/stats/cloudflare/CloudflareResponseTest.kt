package net.adoptium.api.v3.stats.cloudflare

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CloudflareResponseTest {

    companion object {
        private val TEST_DATE = LocalDate.of(2025, 3, 24)
        private val SAMPLE_RESPONSE = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 84,
                          "dimensions": {
                            "clientRequestPath": "/artifactory/apk/alpine/main/x86_64/temurin-21-jdk-21.0.10_p7-r0.apk",
                            "date": "2026-02-25"
                          }
                        },
                        {
                          "count": 58,
                          "dimensions": {
                            "clientRequestPath": "/artifactory/apk/alpine/main/x86_64/temurin-21-jre-21.0.10_p7-r0.apk",
                            "date": "2026-02-25"
                          }
                        }             
                      ]
                    }
                  ]
                }
              },
              "errors": null
            }
        """.trimIndent()
    }

    @Test
    fun `from json should parse responses`() {
        val parsedResponse = CloudflareResponse.fromJson(SAMPLE_RESPONSE)
        assertEquals(2, parsedResponse.data.size)
        val firstItem = parsedResponse.data.first()
        val secondItem = parsedResponse.data.last()
        assertEquals(84, firstItem.count)
        assertEquals("/artifactory/apk/alpine/main/x86_64/temurin-21-jdk-21.0.10_p7-r0.apk", firstItem.path)
        assertEquals(58, secondItem.count)
        assertEquals("/artifactory/apk/alpine/main/x86_64/temurin-21-jre-21.0.10_p7-r0.apk", secondItem.path)
    }

    @Test
    fun `from json should handle empty zones array`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": []
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(0, response.data.size)
    }

    @Test
    fun `from json should handle missing httpRequestsAdaptiveGroups`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "someOtherField": []
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(0, response.data.size)
    }

    @Test
    fun `from json should handle missing count`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "dimensions": {
                            "clientRequestPath": "/test.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(0, response.data.size)
    }

    @Test
    fun `from json should handle missing dimensions`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 84
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(0, response.data.size)
    }

    @Test
    fun `from json should filter out zero count entries`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 0,
                          "dimensions": {
                            "clientRequestPath": "/zero.apk",
                            "date": "2026-02-25"
                          }
                        },
                        {
                          "count": 100,
                          "dimensions": {
                            "clientRequestPath": "/valid.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(1, response.data.size)
        assertEquals(100, response.data.first().count)
        assertEquals("/valid.apk", response.data.first().path)
    }

    @Test
    fun `from json should handle entries with blank path`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 84,
                          "dimensions": {
                            "clientRequestPath": "",
                            "date": "2026-02-25"
                          }
                        },
                        {
                          "count": 100,
                          "dimensions": {
                            "clientRequestPath": "/valid.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(1, response.data.size)
        assertEquals("/valid.apk", response.data.first().path)
    }

    @Test
    fun `from json should handle entries with blank date`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 84,
                          "dimensions": {
                            "clientRequestPath": "/test.apk",
                            "date": ""
                          }
                        },
                        {
                          "count": 100,
                          "dimensions": {
                            "clientRequestPath": "/valid.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(1, response.data.size)
        assertEquals("/valid.apk", response.data.first().path)
    }

    @Test
    fun `from json should handle multiple zones`() {
        val json = """
            {
              "data": {
                "viewer": {
                  "zones": [
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 50,
                          "dimensions": {
                            "clientRequestPath": "/zone1.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    },
                    {
                      "httpRequestsAdaptiveGroups": [
                        {
                          "count": 75,
                          "dimensions": {
                            "clientRequestPath": "/zone2.apk",
                            "date": "2026-02-25"
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = CloudflareResponse.fromJson(json)
        assertEquals(2, response.data.size)
        assertTrue(response.data.any { it.path == "/zone1.apk" && it.count == 50L })
        assertTrue(response.data.any { it.path == "/zone2.apk" && it.count == 75L })
    }

    @Test
    fun `merge two pages keep only unique elements`() {
        val commonItem = CloudflarePackageStats(TEST_DATE, 150, "/path/one.apk")
        val firstPage = CloudflareResponse(
            setOf(
                commonItem,
                CloudflarePackageStats(TEST_DATE, 200, "/path/two.deb")
            )
        )
        val secondPage = CloudflareResponse(
            setOf(
                commonItem,
                CloudflarePackageStats(TEST_DATE, 300, "/path/three.rpm")
            )
        )

        val merged = firstPage.merge(secondPage)

        assertEquals(3, merged.data.size)
        val one = merged.data.find { it.path == "/path/one.apk" }
        assertNotNull(one)
        assertEquals(150, one!!.count)
        assertTrue(merged.data.any { it.path == "/path/two.deb" && it.count == 200L })
        assertTrue(merged.data.any { it.path == "/path/three.rpm" && it.count == 300L })
    }

    @Test
    fun `merge should handle empty responses`() {
        val empty = CloudflareResponse(emptySet())
        val nonEmpty = CloudflareResponse(
            setOf(CloudflarePackageStats(TEST_DATE, 100, "/test.apk"))
        )

        assertEquals(0, empty.merge(empty).data.size)
        assertEquals(1, nonEmpty.merge(empty).data.size)
        assertEquals(1, empty.merge(nonEmpty).data.size)
    }

}
