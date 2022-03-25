package net.adoptium.api.marketplace.parser.maven

import net.adoptium.marketplace.schema.OpenjdkVersionData


interface Restriction<T : Comparable<T>> {
    fun test(a: T, b: T): Boolean
}

interface OpenRestriction<T : Comparable<T>> : Restriction<T>
interface CloseRestriction<T : Comparable<T>> : Restriction<T>

object LT : CloseRestriction<OpenjdkVersionData> {
    override fun test(a: OpenjdkVersionData, b: OpenjdkVersionData): Boolean = a < b
}

object LTE : CloseRestriction<OpenjdkVersionData> {
    override fun test(a: OpenjdkVersionData, b: OpenjdkVersionData): Boolean = a <= b
}

object GT : OpenRestriction<OpenjdkVersionData> {
    override fun test(a: OpenjdkVersionData, b: OpenjdkVersionData): Boolean = a > b
}

object GTE : OpenRestriction<OpenjdkVersionData> {
    override fun test(a: OpenjdkVersionData, b: OpenjdkVersionData): Boolean = a >= b
}

object EXACT : OpenRestriction<OpenjdkVersionData>, CloseRestriction<OpenjdkVersionData> {
    override fun test(a: OpenjdkVersionData, b: OpenjdkVersionData): Boolean = a == b
}
