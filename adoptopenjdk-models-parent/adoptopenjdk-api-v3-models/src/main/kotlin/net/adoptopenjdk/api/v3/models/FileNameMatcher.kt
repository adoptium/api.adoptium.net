package net.adoptopenjdk.api.v3.models

interface FileNameMatcher {
    var names: List<String>

    // if multiple enums match then highest priority will be chosen
    var priority: Int

    fun matchesFile(fileName: String): Boolean {
        val lowerCaseFileName = fileName.toLowerCase()
        return names
            .firstOrNull {
                lowerCaseFileName.contains(fileNameMatcher(it))
            } != null
    }

    fun fileNameMatcher(name: String): Regex {
        return Regex("[\\-_]${name}_")
    }

    fun setNames(instanceName: String, alternativeNames: List<String>) {
        names = listOf(instanceName)
            .union(alternativeNames.toList())
            .map { it.toLowerCase() }
            .toList()
    }
}
