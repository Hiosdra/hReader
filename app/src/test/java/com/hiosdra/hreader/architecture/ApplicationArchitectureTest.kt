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

    @Test
    fun applicationLayerShouldNotDependOnRoomOrWorkManager() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.core.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("androidx.room..", "androidx.work..")
            .check(productionClasses)
    }

    @Test
    fun applicationConsumersShouldUseNarrowArticlePorts() {
        noClasses()
            .that().resideInAnyPackage(
                "com.hiosdra.hreader.core.application.usecase..",
                "com.hiosdra.hreader.entrypoint.worker.."
            )
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.hiosdra.hreader.core.application.port.out.ArticleStore")
            .check(productionClasses)
    }

    @Test
    fun outerLayersShouldNotDependOnRoomDetails() {
        noClasses()
            .that().resideInAnyPackage(
                "com.hiosdra.hreader.core..",
                "com.hiosdra.hreader.presentation..",
                "com.hiosdra.hreader.entrypoint.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.hiosdra.hreader.adapter.persistence.room.."
            )
            .check(productionClasses)
    }
}
