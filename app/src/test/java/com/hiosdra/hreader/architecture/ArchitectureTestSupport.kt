package com.hiosdra.hreader.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

private class DoNotIncludeAndroidUnitTestClasses : ImportOption {
    override fun includes(location: Location): Boolean =
        !location.contains("/debugUnitTest") &&
            !location.contains("/releaseUnitTest") &&
            !location.contains("/test-classes") &&
            !location.contains("/src/test/")
}

internal val productionClasses: JavaClasses by lazy {
    ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .withImportOption(DoNotIncludeAndroidUnitTestClasses())
        .importPackages("com.hiosdra.hreader")
}
