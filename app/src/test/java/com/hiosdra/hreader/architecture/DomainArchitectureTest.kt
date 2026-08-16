package com.hiosdra.hreader.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

class DomainArchitectureTest {
    @Test
    fun domainShouldBeIndependentOfFrameworksAndAdapters() {
        noClasses()
            .that().resideInAPackage("com.hiosdra.hreader.core.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "android..",
                "androidx.room..",
                "androidx.paging..",
                "androidx.lifecycle..",
                "androidx.work..",
                "androidx.navigation..",
                "androidx.compose.ui..",
                "androidx.compose.foundation..",
                "androidx.compose.material..",
                "com.squareup..",
                "okhttp3..",
                "retrofit2..",
                "org.jsoup..",
                "io.sentry..",
                "com.hiosdra.hreader.core.application..",
                "com.hiosdra.hreader.adapter..",
                "com.hiosdra.hreader.entrypoint..",
                "com.hiosdra.hreader.presentation..",
                "com.hiosdra.hreader.bootstrap.."
            )
            .check(productionClasses)
    }
}
