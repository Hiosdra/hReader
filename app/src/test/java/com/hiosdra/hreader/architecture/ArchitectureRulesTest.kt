package com.hiosdra.hreader.architecture

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchUnitRunner
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.runner.RunWith

@RunWith(ArchUnitRunner::class)
@AnalyzeClasses(
    packages = ["com.hiosdra.hreader"],
    importOptions = [com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests::class]
)
class ArchitectureRulesTest {
    @ArchTest
    val uiShouldNotDependOnRemote: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.ui..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.data.remote..")

    @ArchTest
    val uiShouldNotDependOnEntities: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.ui..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.data.local.entity..")

    @ArchTest
    val uiShouldNotDependOnDaos: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.ui..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "com.hiosdra.hreader.data.local.dao.."
        )

    @ArchTest
    val dataShouldNotDependOnUi: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.data..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.ui..")

    @ArchTest
    val workerShouldNotDependOnUi: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.worker..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.ui..")
}
