package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.V3.Companion.ENABLE_PERIODIC_UPDATES
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DbExtension : BeforeAllCallback, AfterAllCallback {
    override fun beforeAll(context: ExtensionContext?) {
        System.setProperty(ENABLE_PERIODIC_UPDATES, "false")
    }

    override fun afterAll(p0: ExtensionContext?) {
        System.gc() // Don't ask, but also don't remove me, breaks deadlock that hangs vm after all tests are completed
    }
}
