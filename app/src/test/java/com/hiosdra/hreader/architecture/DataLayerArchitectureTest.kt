package com.hiosdra.hreader.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Test

class DataLayerArchitectureTest {

    private val allClasses: JavaClasses = ClassFileImporter()
        .importPackages("com.hiosdra.hreader")

    @Test
    fun remoteShouldNotDependOnLocal() {
        val rule: ArchRule = noClasses()
            .that().resideInAPackage("..data.remote..")
            .should().dependOnClassesThat().resideInAPackage("..data.local..")

        rule.check(allClasses)
    }

    @Test
    fun noDirectClassImportsBetweenRemoteAndLocal() {
        val rule: ArchRule = noClasses()
            .that().resideInAnyPackage("..data.remote..", "..data.local..")
            .should().dependOnClassesThat().resideInAnyPackage("..data.remote..", "..data.local..")
            .andShould().onlyDependOnClassesThat().resideOutsideOfPackages("..data.remote..", "..data.local..")

        rule.check(allClasses)
    }
}
