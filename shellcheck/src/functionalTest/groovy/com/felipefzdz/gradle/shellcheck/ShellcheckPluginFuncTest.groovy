package com.felipefzdz.gradle.shellcheck

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ShellcheckPluginFuncTest extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    File buildFile
    File resources
    File localBuildCacheDirectory
    File localConfigCacheDirectory

    def setup() {
        localConfigCacheDirectory = new File(testProjectDir.root, '.gradle/configuration-cache')
        localBuildCacheDirectory = testProjectDir.newFolder('local-cache')
        testProjectDir.newFile('settings.gradle') << """
        buildCache {
            local {
                directory '${localBuildCacheDirectory.toURI()}'
            }
        }
    """
        buildFile = testProjectDir.newFile('build.gradle.kts')
        buildFile << """
plugins {
    id("base")
    id("com.felipefzdz.gradle.shellcheck")
}
        """
        FileUtils.copyDirectory(new File("build/resources"), testProjectDir.root)
        resources = new File(testProjectDir.root, "functionalTest")
    }

    def clearConfigurationCache() {
        localConfigCacheDirectory.listFiles().each { it.delete() };
    }

    def setupSpec() {
        ["docker", "image", "rm", "koalaman/shellcheck-alpine:v0.7.1"].execute().waitForProcessOutput()
    }

    def "(local binary) resource files are available for testing"() {
        given:
        def resourcePath = new File(testProjectDir.root, "functionalTest")

        when:
        List<String> files = resourcePath.list()

        then:
        def expectedFiles = ["no_shell_scripts",
         "another_without_violations",
         "with_violations",
         "without_violations"]
        files.each { assert expectedFiles.contains(it); }
        expectedFiles.each { assert files.contains(it); }
    }


    def "(local binary) fail the build when some scripts in the folder have violations"() {
        given:
        buildFile <<"""
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        when:
        def result = runner().buildAndFail()

        then:
        result.getOutput().contains("Shellcheck files with violations: 8")
        result.getOutput().contains("Shellcheck violations by severity: 3")

        def report = new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.html").text
        ["script_with_violations.bash", "script_with_violations.bash_login", "script_with_violations.bash_logout",
         "script_with_violations.bash_profile", "script_with_violations.bashrc", "script_with_violations.ksh",
         "script_with_violations.sh", "script_with_violations_2.sh"].each {
            assert report.contains(it)
        }
        !report.contains("script_with_violations_wrong_extension.txt")
    }

    def "(local binary) pass the build when no script in the folder has violations"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/without_violations"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        expect:
        runner().build()
    }

    def "(local binary) ensure cacheability"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/without_violations", "${resources.absolutePath}/another_without_violations"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        expect:
        runnerWithBuildCache().build().task(":shellcheck").outcome == TaskOutcome.SUCCESS

        and:
        runnerWithBuildCache().build().task(":shellcheck").outcome == TaskOutcome.FROM_CACHE

        when:
        new File("${resources.absolutePath}/without_violations/script_without_violations.sh") << "ls /"

        then:
        runnerWithBuildCache().build().task(":shellcheck").outcome == TaskOutcome.SUCCESS

        and:
        runnerWithBuildCache().build().task(":shellcheck").outcome == TaskOutcome.FROM_CACHE

        when:
        new File("${resources.absolutePath}/another_without_violations/another_script_without_violations.sh") << """#!/usr/bin/env bash

ls /etc
"""

        then:
        runnerWithBuildCache().build().task(":shellcheck").outcome == TaskOutcome.SUCCESS
    }

    def "(local binary) pass the build when some scripts in the folder have violations and ignoreFailures is passed"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    continueBuildOnFailure.set(true)
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        expect:
        runner().build()
    }

    def "(local binary) pass the build when no files are specified"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/no_shell_scripts"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        expect:
        runnerWithDebugLogging().build()
    }

    def "(local binary) only generate html reports"() {

        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}

tasks.withType<com.felipefzdz.gradle.shellcheck.Shellcheck>().configureEach {
    reports {
        xml.isEnabled = false
        txt.isEnabled = false
        html.isEnabled = true
    }
}
"""
        when:
        runner().buildAndFail()

        then:
        new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.html").exists()
        !new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.xml").exists()
        !new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.txt").exists()
    }

    def "(local binary) generate html, xml and txt reports by default"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        when:
        runner().buildAndFail()

        then:
        new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.html").exists()
        new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.xml").exists()
        new File(testProjectDir.root, "build/reports/shellcheck/shellcheck.txt").exists()
    }

    def "(local binary) provide proper error message when failing for reasons unrelated to shellcheck itself"() {
        given:
        buildFile << """
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    shellcheckVersion.set("vvvvv0.7.1")
    useDocker.set(false)
    shellcheckBinary.set("/usr/badpath/bin/shellcheck")
}
"""

        when:
        def result = runner().buildAndFail()

        then:
        result.output.contains("Execution failed")
    }

    def "(local binary) filtrates by severity"() {
        given:
        buildFile <<  """
shellcheck {
    sources.set(files("${resources.absolutePath}/with_violations"))
    severity.set("error")
    useDocker.set(false)
    shellcheckBinary.set("/usr/local/bin/shellcheck")
}
"""

        when:
        def result = runner().buildAndFail()

        then:
        result.getOutput().contains("Shellcheck files with violations: 1")
        result.getOutput().contains("Shellcheck violations by severity: 1")
    }

    private GradleRunner runnerWithDebugLogging() {
        runner(true)
    }

    private GradleRunner runnerWithBuildCache() {
        runner(false, true)
    }

    private GradleRunner runnerWithConfigurationCache() {
        runner(true, false, true)
    }

    private GradleRunner runner(boolean withDebugLogging = false, boolean withBuildCache = false, boolean withConfigurationCache = false) {
        def arguments = ["shellcheck", "--stacktrace"]
        if (withDebugLogging) {
            arguments << "--debug"
        }
        if (withBuildCache) {
            arguments.add(0, "clean")
            arguments << "--build-cache"
        }
        if(withConfigurationCache) {
            arguments << "--configuration-cache"
        }
        def runner = GradleRunner.create()
            .forwardOutput()
            .withPluginClasspath()
            .withArguments(arguments)
            .withProjectDir(testProjectDir.root)
            .withDebug(true)
        // https://github.com/gradle/gradle/issues/14125
        if (!withConfigurationCache) {
            runner.withDebug(true)
        }
        runner
    }
}
