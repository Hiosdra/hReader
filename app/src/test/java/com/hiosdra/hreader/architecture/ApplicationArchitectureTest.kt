package com.hiosdra.hreader.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

class ApplicationArchitectureTest {
    @Test
    fun applicationLayerShouldNotKnowOuterLayers() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.core.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.hiosdra.hreader.adapter..",
                "com.hiosdra.hreader.entrypoint..",
                "com.hiosdra.hreader.presentation..",
                "com.hiosdra.hreader.bootstrap.."
            )
            .check(productionClasses)
    }

    @Test
    fun portsShouldNotDependOnConcreteAdapters() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.core.application.port..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.hiosdra.hreader.adapter..")
            .check(productionClasses)
    }
}
