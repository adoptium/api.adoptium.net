package net.adoptium.api.v3

import net.adoptium.api.v3.models.Release

interface Updater {
    fun addToUpdate(toUpdate: String): List<Release>
}
