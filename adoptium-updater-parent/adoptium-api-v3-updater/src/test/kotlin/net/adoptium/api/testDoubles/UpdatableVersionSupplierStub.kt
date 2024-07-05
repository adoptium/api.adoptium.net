package net.adoptium.api.testDoubles

import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier

@Priority(1)
@Alternative
@ApplicationScoped
class UpdatableVersionSupplierStub : UpdatableVersionSupplier {
    override suspend fun updateVersions() {
        // NOP
    }

    override fun getTipVersion(): Int? {
        return 15
    }

    override fun getLtsVersions(): Array<Int> {
        return arrayOf(8, 11)
    }

    override fun getAllVersions(): Array<Int> {
        return listOf(8, 10, 11, 12, 18).toList().toTypedArray()
    }
}
