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
    fun applicationLayerShouldNotDependOnFrameworkAdapters() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.core.application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "androidx.room..",
                "androidx.work..",
                "androidx.paging..",
                "retrofit2..",
                "okhttp3..",
                "com.squareup.moshi.."
            )
            .check(productionClasses)
    }

    @Test
    fun pagingShouldStayAtThePersistenceAndPresentationBoundary() {
        noClasses()
            .that().resideOutsideOfPackages(
                "com.hiosdra.hreader.adapter.persistence..",
                "com.hiosdra.hreader.presentation.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage("androidx.paging..")
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

    @Test
    fun roomDetailsShouldStayInPersistenceOrCompositionRoot() {
        noClasses()
            .that().resideOutsideOfPackages(
                "com.hiosdra.hreader.adapter.persistence..",
                "com.hiosdra.hreader.bootstrap.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "androidx.room..",
                "androidx.sqlite..",
                "com.hiosdra.hreader.adapter.persistence.room.."
            )
            .check(productionClasses)
    }
}
