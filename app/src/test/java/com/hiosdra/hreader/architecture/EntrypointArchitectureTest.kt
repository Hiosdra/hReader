package com.hiosdra.hreader.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

class EntrypointArchitectureTest {
    @Test
    fun entrypointsShouldDependOnApplicationPortsNotUiOrAdapters() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.entrypoint..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.hiosdra.hreader.adapter..",
                "com.hiosdra.hreader.bootstrap..",
                "com.hiosdra.hreader.presentation.."
            )
            .check(productionClasses)
    }
}
