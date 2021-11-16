package net.adoptium.api.v3

import net.adoptium.api.v3.models.Release
import javax.inject.Singleton

@Singleton
interface Updater {
    fun addToUpdate(toUpdate: String): List<Release>
}
