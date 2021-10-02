package net.adoptopenjdk.api.v3

import net.adoptopenjdk.api.v3.models.Release
import javax.inject.Singleton

@Singleton
interface Updater {
    fun addToUpdate(toUpdate: String): List<Release>
}
