package com.exchange.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void productionDependenciesPointInwardAndNeverReachBenchmarks() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.exchange");
        noClasses().that().resideInAPackage("com.exchange.matching..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.exchange.application..", "com.exchange.infrastructure..", "com.exchange.bootstrap..",
                        "com.lmax..", "java.io..", "java.nio..", "java.util.concurrent..")
                .check(classes);
        noClasses().that().resideInAPackage("com.exchange.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.exchange.infrastructure..", "com.exchange.bootstrap..", "com.lmax..", "java.nio..")
                .check(classes);
        noClasses().that().resideInAPackage("com.exchange.infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage("com.exchange.bootstrap..")
                .check(classes);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("com.exchange.benchmark..")
                .check(classes);
    }
}
