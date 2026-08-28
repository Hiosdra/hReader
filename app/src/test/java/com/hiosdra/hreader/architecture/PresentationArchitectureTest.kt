package com.hiosdra.hreader.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

class PresentationArchitectureTest {
    @Test
    fun featurePresentationShouldNotResolveDependenciesFromKoin() {
        noClasses()
            .that().resideInAnyPackage(
                "com.hiosdra.hreader.presentation.article..",
                "com.hiosdra.hreader.presentation.components..",
                "com.hiosdra.hreader.presentation.feeds..",
                "com.hiosdra.hreader.presentation.main..",
                "com.hiosdra.hreader.presentation.onboarding..",
                "com.hiosdra.hreader.presentation.settings.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.koin..")
            .check(productionClasses)
    }

    @Test
    fun presentationShouldDependOnPortsAndUseCasesNotAdapters() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.presentation..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.hiosdra.hreader.adapter..",
                "com.hiosdra.hreader.entrypoint..",
                "com.hiosdra.hreader.bootstrap.."
            )
            .check(productionClasses)
    }

    @Test
    fun viewModelsShouldNotDependOnOtherViewModels() {
        listOf(
            "com.hiosdra.hreader.presentation.article.ArticleViewModel",
            "com.hiosdra.hreader.presentation.feeds.FeedsViewModel",
            "com.hiosdra.hreader.presentation.feeds.add.AddFeedViewModel",
            "com.hiosdra.hreader.presentation.main.MainViewModel",
            "com.hiosdra.hreader.presentation.settings.SettingsViewModel"
        ).forEach { viewModelName ->
            noClasses()
                .that().resideInAPackage("com.hiosdra.hreader.presentation..")
                .and().haveSimpleNameEndingWith("ViewModel")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName(viewModelName)
                .check(productionClasses)
        }
    }

    @Test
    fun viewModelsShouldDependOnUseCasesNotPorts() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.presentation..")
            .and().haveSimpleNameEndingWith("ViewModel")
            .should().dependOnClassesThat()
            .resideInAPackage("com.hiosdra.hreader.core.application.port..")
            .check(productionClasses)
    }

    @Test
    fun presentationShouldNotDependOnTransportLibraries() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.presentation..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("okhttp3..", "retrofit2..", "org.json..")
            .check(productionClasses)
    }

    @Test
    fun presentationShouldUseCapabilityPreferencePorts() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.presentation..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.hiosdra.hreader.core.application.port.out.AppPreferences")
            .check(productionClasses)
    }
}
