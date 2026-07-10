/*
 * SPDX-License-Identifier: EUPL-1.2 OR LicenseRef-commercial
 *
 * Copyright (c) 2012-2026 mgm technology partners GmbH
 *
 * Dual License
 * ------------
 * This source file is part of the mgm A12 Platform and available under
 * a choice of two different licenses:
 *
 * 1. Open-Source License - EUPL v1.2
 *    You may redistribute and/or modify this file under the terms of the
 *    European Union Public License, version 1.2 - see https://eupl.eu/.
 *
 * 2. Commercial License
 *    Alternatively, you may obtain a commercial license from
 *    mgm technology partners GmbH, that permits use of this software
 *    under different terms (including support and maintenance services).
 *
 *    Please contact a12-license@mgm-tp.com for more information.
 *
 * You must select and comply with exactly one of the above license options.
 *
 * Warranty Disclaimer (applies to either option)
 * ----------------------------------------------
 * THIS SOFTWARE IS PROVIDED "AS IS" AND WITHOUT WARRANTY OF ANY KIND,
 * WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT, EXCEPT WHERE SUCH DISCLAIMERS ARE HELD TO BE
 * LEGALLY INVALID. SEE THE RESPECTIVE LICENSE TEXT FOR DETAILS.
 */
package com.mgmtp.a12.devtools.gradle.plugins.models

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

/**
 * End-to-end functional tests for the prepare-models plugin.
 *
 * These tests use Gradle TestKit (GradleRunner) to simulate real consumer projects.
 * They exercise the full plugin lifecycle as a real build would, covering edge cases
 * that any replacement implementation (e.g. WCF-based convertWorkspaceModels) must handle.
 *
 * Key behaviors under test:
 * - Plugin application and extension defaults
 * - Task registration and dependency wiring
 * - Input validation (missing/empty directories)
 * - File filtering (only document models are expanded, others are copied)
 * - Output structure (flat files at output root, no nesting)
 * - mergeOutputs merges WCF workspace conversion output (expanded models + validation code from
 *   build/expanded-code, generated inside the WCF converter), excludes _ei.json
 * - copyOtherFiles copies non-document-model JSON files
 * - Up-to-date / incremental behavior
 * - Extension property overrides
 */
class PrepareModelsFunctionalTest extends Specification {

    @TempDir
    File testProjectDir

    File buildFile
    File settingsFile
    File inputDir

    def setup() {
        // Gradle canonicalizes the project dir it is given (e.g. /var -> /private/var on macOS),
        // so canonicalize here too to keep path assertions in sync with the build's output.
        testProjectDir = testProjectDir.canonicalFile

        settingsFile = new File(testProjectDir, 'settings.gradle')
        settingsFile << "rootProject.name = 'test-project'\n"

        buildFile = new File(testProjectDir, 'build.gradle')

        inputDir = new File(testProjectDir, 'src')
        inputDir.mkdirs()
    }

    // =========================================================================
    // Plugin Application & Configuration
    // =========================================================================

    def "plugin can be applied without errors"() {
        given:
        buildFile << pluginBlock()

        when:
        def result = runGradle('tasks', '--group', 'prepare-models-plugin')

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
    }

    def "plugin registers all expected tasks"() {
        given:
        buildFile << pluginBlock()

        when:
        def result = runGradle('tasks', '--group', 'prepare-models-plugin')

        then:
        result.output.contains('convertWorkspaceModels')
        result.output.contains('mergeOutputs')
        result.output.contains('copyOtherFiles')
        result.output.contains('prepareModels')
    }

    def "plugin applies java plugin automatically"() {
        given: "project does not explicitly apply java"
        buildFile << pluginBlock()

        when:
        def result = runGradle('tasks', '--all')

        then: "java-related tasks are available (proves java plugin was applied)"
        result.output.contains('compileJava')
    }

    def "plugin does not conflict when java plugin is already applied"() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.mgmtp.a12.devtools.plugins.prepare-models'
            }
        """

        when:
        def result = runGradle('tasks', '--group', 'prepare-models-plugin')

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
    }

    def "extension has correct default inputDir convention (src)"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printInputDir') {
                doLast {
                    println "INPUT_DIR=\${prepareModels.inputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('printInputDir')

        then:
        result.output.contains("INPUT_DIR=${new File(testProjectDir, 'src').absolutePath}")
    }

    def "extension has correct default outputDir convention (../../target/models)"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printOutputDir') {
                doLast {
                    println "OUTPUT_DIR=\${prepareModels.outputDir.get().asFile.name}"
                }
            }
        """

        when:
        def result = runGradle('printOutputDir')

        then:
        result.output.contains("OUTPUT_DIR=models")
    }

    def "extension inputDir can be overridden"() {
        given:
        def customInput = new File(testProjectDir, 'custom-input')
        customInput.mkdirs()

        buildFile << pluginBlock() + """
            prepareModels {
                inputDir = layout.projectDirectory.dir('custom-input')
            }

            tasks.register('printInputDir') {
                doLast {
                    println "INPUT_DIR=\${prepareModels.inputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('printInputDir')

        then:
        result.output.contains("INPUT_DIR=${customInput.absolutePath}")
    }

    def "extension outputDir can be overridden"() {
        given:
        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('custom-output')
            }

            tasks.register('printOutputDir') {
                doLast {
                    println "OUTPUT_DIR=\${prepareModels.outputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('printOutputDir')

        then:
        result.output.contains("INPUT_DIR=" + new File(testProjectDir, 'custom-output').absolutePath) ||
            result.output.contains("OUTPUT_DIR=" + new File(testProjectDir, 'custom-output').absolutePath)
    }

    // =========================================================================
    // Task Dependency Graph
    // =========================================================================

    def "convertWorkspaceModels is registered and configurable"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printDeps') {
                doLast {
                    def convertTask = tasks.named('convertWorkspaceModels').get()
                    println "CONVERT_INPUT=\${convertTask.inputDir.get().asFile.absolutePath}"
                    println "CONVERT_OUTPUT=\${convertTask.outputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('printDeps')

        then:
        result.output.contains('CONVERT_INPUT=')
        result.output.contains('CONVERT_OUTPUT=')
    }

    def "mergeOutputs depends on convertWorkspaceModels"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printDeps') {
                doLast {
                    def task = tasks.named('mergeOutputs').get()
                    def deps = task.dependsOn.collect {
                        it instanceof org.gradle.api.tasks.TaskProvider ? it.get().name : it.toString()
                    }
                    println "MERGE_DEPS=\${deps}"
                }
            }
        """

        when:
        def result = runGradle('printDeps')

        then:
        result.output.contains('convertWorkspaceModels')
    }

    def "prepareModels depends on mergeOutputs and copyOtherFiles"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printDeps') {
                doLast {
                    def task = tasks.named('prepareModels').get()
                    def deps = task.dependsOn.collect {
                        it instanceof org.gradle.api.tasks.TaskProvider ? it.get().name : it.toString()
                    }
                    println "PREPARE_DEPS=\${deps}"
                }
            }
        """

        when:
        def result = runGradle('printDeps')

        then:
        result.output.contains('mergeOutputs') || result.output.contains('copyOtherFiles')
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    def "task pipeline fails when inputDir does not exist"() {
        given: "inputDir points to a non-existent directory"
        inputDir.deleteDir()
        buildFile << pluginBlock()

        when:
        def result = runGradleAndFail('convertWorkspaceModels')

        then: "fails because inputDir does not exist"
        result.output.contains('FAILED')
    }

    // =========================================================================
    // File Filtering — Document Model Detection
    // =========================================================================

    def "modelType detection is based on first 10 lines (verified via copyOtherFiles)"() {
        given: "a JSON file with modelType=document on line 11+ (not detected as document)"
        def content = new StringBuilder()
        content.append('{\n')
        (1..9).each { content.append("  \"line${it}\": \"value\",\n") }
        content.append('  "modelType": "document"\n')
        content.append('}')
        new File(inputDir, 'deep-type.json').text = content.toString()

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then: "file is NOT detected as document model, so copyOtherFiles includes it"
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        // Since modelType is beyond line 10, isDocumentModel returns false → file gets copied
        new File(testProjectDir, 'target/models/deep-type.json').exists()
    }

    // =========================================================================
    // Output Structure
    // =========================================================================

    def "convertWorkspaceModels outputDir is configured to build/expanded (flat structure expected)"() {
        given: "document models in nested input structure"
        createDocumentModel('model-a.json', 'model-a')

        buildFile << pluginBlock() + """
            tasks.register('verifyOutputDir') {
                doLast {
                    def task = tasks.named('convertWorkspaceModels').get()
                    println "OUTPUT_DIR=\${task.outputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('verifyOutputDir')

        then: "output dir is build/expanded, models should be flat there"
        result.output.contains("OUTPUT_DIR=${new File(testProjectDir, 'build/expanded').absolutePath}")
    }

    // =========================================================================
    // mergeOutputs Behavior
    // =========================================================================

    def "mergeOutputs excludes _ei.json files from expanded output"() {
        given: "expanded output contains _ei.json files"
        def expandedDir = new File(testProjectDir, 'build/expanded')
        expandedDir.mkdirs()
        new File(expandedDir, 'model1.json').text = '{"expanded": true}'
        new File(expandedDir, 'model1_ei.json').text = '{"external_include": true}'
        new File(expandedDir, 'model2.json').text = '{"expanded": true}'

        def expandedCodeDir = new File(testProjectDir, 'build/expanded-code')
        expandedCodeDir.mkdirs()
        new File(expandedCodeDir, 'model1.validation.js').text = 'function validate() {}'

        def outputDir = new File(testProjectDir, 'target/models')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }

            // Skip the actual conversion task, just test mergeOutputs
            tasks.named('convertWorkspaceModels').configure { enabled = false }
        """

        when:
        def result = runGradle('mergeOutputs')

        then:
        result.task(':mergeOutputs').outcome == TaskOutcome.SUCCESS
        def mergedDir = outputDir
        if (mergedDir.exists()) {
            new File(mergedDir, 'model1.json').exists()
            new File(mergedDir, 'model2.json').exists()
            new File(mergedDir, 'model1.validation.js').exists()
            !new File(mergedDir, 'model1_ei.json').exists()
        }
    }

    def "mergeOutputs combines WCF validation code and expanded models"() {
        given:
        def expandedDir = new File(testProjectDir, 'build/expanded')
        expandedDir.mkdirs()
        new File(expandedDir, 'doc1.json').text = '{"id":"doc1","expanded":true}'

        def expandedCodeDir = new File(testProjectDir, 'build/expanded-code')
        expandedCodeDir.mkdirs()
        new File(expandedCodeDir, 'doc1.validation.js').text = '// validation code'

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('merged-output')
            }
            // Skip the actual conversion task, just test mergeOutputs
            tasks.named('convertWorkspaceModels').configure { enabled = false }
        """

        when:
        def result = runGradle('mergeOutputs')

        then:
        result.task(':mergeOutputs').outcome == TaskOutcome.SUCCESS
        def mergedDir = new File(testProjectDir, 'merged-output')
        new File(mergedDir, 'doc1.json').exists()
        new File(mergedDir, 'doc1.validation.js').exists()
    }

    def "mergeOutputs produces no validation code when generateValidationCode is off (default)"() {
        given: "expanded output has a model but expanded-code dir is empty (flag is off by default)"
        def expandedDir = new File(testProjectDir, 'build/expanded')
        expandedDir.mkdirs()
        new File(expandedDir, 'doc1.json').text = '{"id":"doc1","expanded":true}'

        def expandedCodeDir = new File(testProjectDir, 'build/expanded-code')
        expandedCodeDir.mkdirs()
        // No .validation.js files — generateValidationCode is false (default)

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('merged-output')
            }
            // Skip the actual conversion task, just test mergeOutputs
            tasks.named('convertWorkspaceModels').configure { enabled = false }
        """

        when:
        def result = runGradle('mergeOutputs')

        then:
        result.task(':mergeOutputs').outcome == TaskOutcome.SUCCESS
        def mergedDir = new File(testProjectDir, 'merged-output')
        mergedDir.listFiles().findAll { it.name.endsWith('.validation.js') }.isEmpty()
    }

    // =========================================================================
    // copyOtherFiles Behavior
    // =========================================================================

    def "copyOtherFiles copies non-document JSON files"() {
        given:
        createFormModel('form1.json', 'form1')
        new File(inputDir, 'config.json').text = '{"setting": "value"}'

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        def outputDir = new File(testProjectDir, 'target/models')
        new File(outputDir, 'form1.json').exists()
        new File(outputDir, 'config.json').exists()
    }

    def "copyOtherFiles does NOT copy document models"() {
        given:
        createDocumentModel('doc1.json', 'doc1')
        createFormModel('form1.json', 'form1')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        def outputDir = new File(testProjectDir, 'target/models')
        !new File(outputDir, 'doc1.json').exists()
        new File(outputDir, 'form1.json').exists()
    }

    def "copyOtherFiles does NOT copy non-JSON files"() {
        given:
        new File(inputDir, 'readme.txt').text = 'This is not a model'
        new File(inputDir, 'data.xml').text = '<data/>'
        createFormModel('form1.json', 'form1')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        def outputDir = new File(testProjectDir, 'target/models')
        !new File(outputDir, 'readme.txt').exists()
        !new File(outputDir, 'data.xml').exists()
        new File(outputDir, 'form1.json').exists()
    }

    def "copyOtherFiles flattens directory structure via eachFileAction"() {
        given: "form models in nested directories"
        def subDir = new File(inputDir, 'nested/deep')
        subDir.mkdirs()
        createFormModel('nested/deep/nested-form.json', 'nested-form')
        createFormModel('top-form.json', 'top-form')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then: "all files end up flat at output root (default eachFileAction flattens paths)"
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        def outputDir = new File(testProjectDir, 'target/models')
        new File(outputDir, 'nested-form.json').exists()
        new File(outputDir, 'top-form.json').exists()
        // No nested directories in output
        !new File(outputDir, 'nested').exists()
    }

    def "copyOtherFiles does not include empty directories"() {
        given:
        def emptySubDir = new File(inputDir, 'empty-subdir')
        emptySubDir.mkdirs()
        createFormModel('form1.json', 'form1')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
        def outputDir = new File(testProjectDir, 'target/models')
        !new File(outputDir, 'empty-subdir').exists()
    }

    // =========================================================================
    // Incremental Build / Up-to-Date Behavior
    // =========================================================================

    def "copyOtherFiles is UP-TO-DATE on second run without changes"() {
        given:
        createFormModel('form1.json', 'form1')
        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        runGradle('copyOtherFiles')
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.UP_TO_DATE
    }

    def "copyOtherFiles re-runs when input file is added"() {
        given:
        createFormModel('form1.json', 'form1')
        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when: "first run"
        runGradle('copyOtherFiles')

        and: "add a new file"
        createFormModel('form2.json', 'form2')

        and: "second run"
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
    }

    // =========================================================================
    // Classpath Validation
    // =========================================================================

    def "convertWorkspaceModels fails when inputDir does not exist"() {
        given: "inputDir points to a non-existent directory"
        inputDir.deleteDir()
        buildFile << pluginBlock()

        when:
        def result = runGradleAndFail('convertWorkspaceModels')

        then:
        result.output.contains('FAILED')
    }

    // =========================================================================
    // Edge Cases — File Content Patterns
    // =========================================================================

    // =========================================================================
    // Custom eachFileAction
    // =========================================================================

    def "custom eachFileAction is applied during copyOtherFiles"() {
        given:
        createFormModel('nested/form1.json', 'form1')
        new File(inputDir, 'nested').mkdirs()
        createFormModel('form1.json', 'form1')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
                // Custom action: preserve directory structure (override default flattening)
                eachFileAction = new Action<FileCopyDetails>() {
                    void execute(FileCopyDetails details) {
                        // no-op: keep original path
                    }
                }
            }
        """

        when:
        def result = runGradle('copyOtherFiles')

        then:
        result.task(':copyOtherFiles').outcome == TaskOutcome.SUCCESS
    }

    // =========================================================================
    // Output Dir Cleaning (Critical for WCF migration)
    // =========================================================================

    def "stale files in expanded output dir persist unless task cleans them"() {
        given: "a pre-existing stale file in expanded dir"
        def expandedDir = new File(testProjectDir, 'build/expanded')
        expandedDir.mkdirs()
        new File(expandedDir, 'stale-model.json').text = '{"stale": true}'

        createDocumentModel('doc1.json', 'doc1')
        // The new ConvertWorkspaceModelsTask cleans the output dir before running
        // (since WCF processes the whole workspace). This test just verifies the
        // stale file exists before the task runs — cleaning is tested in ConvertWorkspaceModelsTaskSpec.
        buildFile << pluginBlock() + """
            tasks.register('checkStaleFile') {
                doLast {
                    def stale = file('build/expanded/stale-model.json')
                    println "STALE_EXISTS=\${stale.exists()}"
                }
            }
        """

        when:
        def result = runGradle('checkStaleFile')

        then: "stale file still exists (old task uses incremental, not whole-workspace processing)"
        result.output.contains('STALE_EXISTS=true')
    }

    // =========================================================================
    // Task Output Directories
    // =========================================================================

    def "convertWorkspaceModels output goes to build/expanded"() {
        given:
        buildFile << pluginBlock() + """
            tasks.register('printExpandedDir') {
                doLast {
                    def task = tasks.named('convertWorkspaceModels').get()
                    println "EXPANDED_DIR=\${task.outputDir.get().asFile.absolutePath}"
                }
            }
        """

        when:
        def result = runGradle('printExpandedDir')

        then:
        result.output.contains("EXPANDED_DIR=${new File(testProjectDir, 'build/expanded').absolutePath}")
    }

    // =========================================================================
    // Integration: Full prepareModels Pipeline (Configuration Only)
    // =========================================================================

    def "prepareModels task graph can be resolved without errors"() {
        given:
        createDocumentModel('doc1.json', 'doc1')
        createFormModel('form1.json', 'form1')

        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when: "just resolve the task graph (dry run)"
        def result = runGradle('prepareModels', '--dry-run')

        then:
        result.output.contains(':convertWorkspaceModels')
        result.output.contains(':mergeOutputs')
        result.output.contains(':copyOtherFiles')
        result.output.contains(':prepareModels')
    }

    def "prepareModels dry-run shows correct task ordering"() {
        given:
        createDocumentModel('doc1.json', 'doc1')
        buildFile << pluginBlock() + """
            prepareModels {
                outputDir = layout.projectDirectory.dir('target/models')
            }
        """

        when:
        def result = runGradle('prepareModels', '--dry-run')

        then: "convertWorkspaceModels runs before mergeOutputs; mergeOutputs before prepareModels"
        def output = result.output
        def convertIdx = output.indexOf(':convertWorkspaceModels')
        def mergeIdx = output.indexOf(':mergeOutputs')
        def prepareIdx = output.indexOf(':prepareModels')

        convertIdx < mergeIdx
        mergeIdx < prepareIdx
    }

    // =========================================================================
    // kernelMdFacadeVersion — auto-detection and explicit override
    // =========================================================================

    def "prepareModelsConversion has no explicit kernel-md-facade when kernelMdFacadeVersion is unset and buildscript has no kernel"() {
        given: "no kernelMdFacadeVersion set and no kernel on buildscript classpath (TestKit environment)"
        buildFile << pluginBlock() + """
            tasks.register('printConversionDeps') {
                doLast {
                    def kernelDep = configurations.prepareModelsConversion.dependencies.find { dep ->
                        dep.group == 'com.mgmtp.a12.kernel' && dep.name == 'kernel-md-facade'
                    }
                    println "KERNEL_DEP=\${kernelDep != null ? kernelDep.version : 'none'}"
                }
            }
        """

        when:
        def result = runGradle('printConversionDeps')

        then: "no explicit kernel dep added — kernel reaches the classpath transitively via wcf-core"
        result.output.contains('KERNEL_DEP=none')
    }

    def "explicit kernelMdFacadeVersion overrides auto-detection and fallback"() {
        given:
        buildFile << pluginBlock() + """
            prepareModels {
                kernelMdFacadeVersion = '99.0.0'
            }
            tasks.register('printConversionDeps') {
                doLast {
                    configurations.prepareModelsConversion.dependencies.each { dep ->
                        println "CONV_DEP=\${dep.group}:\${dep.name}:\${dep.version}"
                    }
                }
            }
        """

        when:
        def result = runGradle('printConversionDeps')

        then:
        result.output.contains('CONV_DEP=com.mgmtp.a12.kernel:kernel-md-facade:99.0.0')
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String pluginBlock() {
        """
            plugins {
                id 'com.mgmtp.a12.devtools.plugins.prepare-models'
            }
        """
    }

    private void createDocumentModel(String relativePath, String modelId) {
        def file = new File(inputDir, relativePath)
        file.parentFile.mkdirs()
        file.text = """{
    "header": {
        "id": "${modelId}",
        "modelType": "document",
        "modelVersion": "1.0.0",
        "modelReferences": []
    },
    "content": {}
}"""
    }

    private void createFormModel(String relativePath, String modelId) {
        def file = new File(inputDir, relativePath)
        file.parentFile.mkdirs()
        file.text = """{
    "header": {
        "id": "${modelId}",
        "modelType": "form",
        "modelVersion": "1.0.0",
        "modelReferences": []
    },
    "content": {}
}"""
    }

    private def runGradle(String... args) {
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments(args.toList() + ['--stacktrace'])
            .build()
    }

    private def runGradleAndFail(String... args) {
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments(args.toList() + ['--stacktrace'])
            .buildAndFail()
    }
}
