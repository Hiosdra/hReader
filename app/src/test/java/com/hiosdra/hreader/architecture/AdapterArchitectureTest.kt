package com.hiosdra.hreader.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.Test

class AdapterArchitectureTest {
    @Test
    fun freshRssAndMinifluxAdaptersShouldBeIndependent() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter.backend.freshrss..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.adapter.backend.miniflux..")
            .check(productionClasses)

        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter.backend.miniflux..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.adapter.backend.freshrss..")
            .check(productionClasses)
    }

    @Test
    fun persistenceShouldDependOnTheBackendPortNotBackendImplementations() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter.persistence..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.hiosdra.hreader.adapter.backend.freshrss..",
                "com.hiosdra.hreader.adapter.backend.miniflux.."
            )
            .check(productionClasses)
    }

    @Test
    fun contentRepositoryShouldUsePortsForCrossRepositoryWork() {
        listOf(
            "com.hiosdra.hreader.adapter.persistence.ArticleAiOverviewRepository",
            "com.hiosdra.hreader.adapter.persistence.ArticleImageRepository",
            "com.hiosdra.hreader.adapter.persistence.ArticlePageRepository",
            "com.hiosdra.hreader.adapter.persistence.CredibilityRepository"
        ).forEach { concreteRepositoryName ->
            noClasses()
                .that().haveFullyQualifiedName(
                    "com.hiosdra.hreader.adapter.persistence.ArticleContentRepository"
                )
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(concreteRepositoryName)
                .check(productionClasses)
        }
    }

    @Test
    fun adaptersShouldNotDependOnEntrypoints() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.entrypoint..")
            .check(productionClasses)
    }

    @Test
    fun backendDtoPackagesShouldNotCrossContaminateEachOther() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter.backend.freshrss.dto..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.adapter.backend.miniflux.dto..")
            .check(productionClasses)

        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.adapter.backend.miniflux.dto..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.adapter.backend.freshrss.dto..")
            .check(productionClasses)
    }

    @Test
    fun roomSlicesShouldBeFreeOfCycles() {
        slices()
            .matching("com.hiosdra.hreader.adapter.persistence.room.(*)..")
            .should().beFreeOfCycles()
            .check(productionClasses)
    }
}
