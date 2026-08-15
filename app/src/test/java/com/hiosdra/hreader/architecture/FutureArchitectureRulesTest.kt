package com.hiosdra.hreader.architecture

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchUnitRunner
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.Ignore
import org.junit.runner.RunWith

@Ignore("These architecture rules are staged for a future dependency-boundary cleanup.")
@RunWith(ArchUnitRunner::class)
@AnalyzeClasses(
    packages = ["com.hiosdra.hreader"],
    importOptions = [com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests::class]
)
class FutureArchitectureRulesTest {
    @ArchTest
    val onlyRepositoriesMayAccessDaos: ArchRule = noClasses()
        .that().resideOutsideOfPackage("..repository..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.data.local.dao..")

    @ArchTest
    val navigationShouldNotDependOnUi: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.navigation..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.ui..")

    @ArchTest
    val utilShouldNotDependOnData: ArchRule = noClasses()
        .that().resideInAPackage("com.hiosdra.hreader.util..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.hiosdra.hreader.data..")

    @ArchTest
    val repositoriesNaming: ArchRule = classes()
        .that().resideInAPackage("..repository..")
        .and().haveSimpleNameNotContaining("$")
        .should().haveSimpleNameEndingWith("Repository")

    @ArchTest
    val topLevelPackagesNoCycles: ArchRule = slices()
        .matching("com.hiosdra.hreader.(*)..")
        .should().beFreeOfCycles()
}
