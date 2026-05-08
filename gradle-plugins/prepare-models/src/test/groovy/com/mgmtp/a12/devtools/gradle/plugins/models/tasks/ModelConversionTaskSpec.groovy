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
 * 1. Open-Source License – EUPL v1.2
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
 * THIS SOFTWARE IS PROVIDED “AS IS” AND WITHOUT WARRANTY OF ANY KIND,
 * WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT, EXCEPT WHERE SUCH DISCLAIMERS ARE HELD TO BE
 * LEGALLY INVALID. SEE THE RESPECTIVE LICENSE TEXT FOR DETAILS.
 */
package com.mgmtp.a12.devtools.gradle.plugins.models.tasks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.api.file.FileType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.InputChanges

import spock.lang.Specification
import spock.lang.TempDir

class ModelConversionTaskSpec extends Specification implements TaskTestHelpers {

    @TempDir
    File tempDir

    TestModelConversionTask task
    File cacheFile
    File inputDir
    File outputDir

    def setup() {
        def project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        task = project.tasks.create('testConversion', TestModelConversionTask)

        inputDir = new File(tempDir, 'models')
        inputDir.mkdirs()

        outputDir = new File(tempDir, 'output')
        outputDir.mkdirs()

        cacheFile = new File(tempDir, 'cache.json')
        task.inputDir.set(inputDir)
        task.outputDir.set(outputDir)
        task.cacheFile.set(cacheFile)
        
        // Initialize with empty cache
        writeCache([:])
    }

    def "should add arguments for added document model"() {
        given: "a new document model file"
        def doc1 = createDocumentFile('doc1.json')
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "arguments include input and output paths"
        task.getArgs().size() == 2
        task.getArgs()[0] == doc1.path
        task.getArgs()[1] == new File(outputDir, 'doc1.json').path
    }

    def "should add arguments for modified document model"() {
        given: "an existing document model that is modified"
        def doc1 = createDocumentFile('doc1.json')
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "arguments include input and output paths"
        task.getArgs().size() == 2
        task.getArgs()[0] == doc1.path
        task.getArgs()[1] == new File(outputDir, 'doc1.json').path
    }

    def "should delete output file when document model is removed"() {
        given: "an output file exists"
        def doc1 = new File(inputDir, 'doc1.json')
        def outputFile = new File(outputDir, 'doc1.json')
        outputFile.text = 'some content'

        and: "the document is removed"
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.REMOVED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "output file is deleted and no args added"
        !outputFile.exists()
        task.getArgs().size() == 0
    }

    def "should handle multiple changed files"() {
        given: "multiple document models changed"
        def doc1 = createDocumentFile('doc1.json')
        def doc2 = createDocumentFile('doc2.json')
        def doc3 = createDocumentFile('doc3.json')
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE),
            mockFileChange(doc2, ChangeType.MODIFIED, FileType.FILE),
            mockFileChange(doc3, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "arguments include all files"
        task.getArgs().size() == 6
        task.getArgs().contains(doc1.path)
        task.getArgs().contains(doc2.path)
        task.getArgs().contains(doc3.path)
    }

    def "should ignore non-file changes"() {
        given: "a directory change"
        def dir = new File(inputDir, 'subdir')
        dir.mkdirs()
        def inputChanges = mockInputChanges([
            mockFileChange(dir, ChangeType.ADDED, FileType.DIRECTORY)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "no arguments added"
        task.getArgs().size() == 0
    }

    def "should ignore non-document-model files"() {
        given: "a non-document JSON file (form model)"
        def formModel = new File(inputDir, 'form.json')
        formModel.text = '''
{
  "header": {
    "id": "test-form",
    "modelType": "form"
  }
}
'''
        def inputChanges = mockInputChanges([
            mockFileChange(formModel, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "no arguments added"
        task.getArgs().size() == 0
    }

    def "should add dependent files when dependency changes"() {
        given: "cache shows doc2 depends on doc1"
        def doc1 = createDocumentFile('doc1.json')
        def doc2Path = new File(inputDir, 'doc2.json').path
        writeCache([
            (doc1.path): [doc2Path]
        ])

        and: "doc1 is modified"
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "both doc1 and doc2 are in arguments"
        task.getArgs().size() == 4
        task.getArgs().contains(doc1.path)
        task.getArgs().contains(doc2Path)
    }

    def "should handle transitive dependencies"() {
        given: "doc3 depends on doc2, doc2 depends on doc1"
        def doc1 = createDocumentFile('doc1.json')
        def doc2Path = new File(inputDir, 'doc2.json').path
        def doc3Path = new File(inputDir, 'doc3.json').path
        writeCache([
            (doc1.path): [doc2Path],
            (doc2Path): [doc3Path]
        ])

        and: "doc1 is modified"
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "all three documents are in arguments"
        task.getArgs().size() == 6
        task.getArgs().contains(doc1.path)
        task.getArgs().contains(doc2Path)
        task.getArgs().contains(doc3Path)
    }

    def "should not duplicate files already in change set"() {
        given: "doc2 depends on doc1"
        def doc1 = createDocumentFile('doc1.json')
        def doc2 = createDocumentFile('doc2.json')
        writeCache([
            (doc1.path): [doc2.path]
        ])

        and: "both doc1 and doc2 are modified"
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE),
            mockFileChange(doc2, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "each file appears exactly once"
        task.getArgs().size() == 4
        task.getArgs().findAll { it == doc1.path }.size() == 1
        task.getArgs().findAll { it == doc2.path }.size() == 1
    }

    def "should handle multiple dependents"() {
        given: "doc2 and doc3 both depend on doc1"
        def doc1 = createDocumentFile('doc1.json')
        def doc2Path = new File(inputDir, 'doc2.json').path
        def doc3Path = new File(inputDir, 'doc3.json').path
        writeCache([
            (doc1.path): [doc2Path, doc3Path]
        ])

        and: "doc1 is modified"
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "all three documents are in arguments"
        task.getArgs().size() == 6
        task.getArgs().contains(doc1.path)
        task.getArgs().contains(doc2Path)
        task.getArgs().contains(doc3Path)
    }

    // hetereogenity example from Client
    def "should not duplicate dependents when multiple files depend on same changed file"() {
        given: "Fruits includes Healthy, Vegetables includes Healthy, Healthy includes Food"
        def healthy = createDocumentFile('Healthy-document.json')
        def fruitsPath = new File(inputDir, 'Fruits-document.json').path
        def vegetablesPath = new File(inputDir, 'Vegetables-document.json').path
        def foodPath = new File(inputDir, 'Food-document.json').path
        writeCache([
            (healthy.path): [fruitsPath, vegetablesPath],
            (foodPath): [healthy.path]
        ])

        and: "Healthy is modified"
        def inputChanges = mockInputChanges([
            mockFileChange(healthy, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "each dependent appears exactly once"
        task.getArgs().size() == 6
        task.getArgs().findAll { it == healthy.path }.size() == 1
        task.getArgs().findAll { it == fruitsPath }.size() == 1
        task.getArgs().findAll { it == vegetablesPath }.size() == 1
    }

    def "should handle empty change set"() {
        given: "no changes"
        def inputChanges = mockInputChanges([])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "no arguments added"
        task.getArgs().size() == 0
    }

    def "should preserve initial args when handling changes"() {
        given: "task has initial arguments"
        task.args("--initial-arg", "value")
        def doc1 = createDocumentFile('doc1.json')
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.handleChanges(inputChanges, 'json')

        then: "initial args are preserved and new args added"
        task.getArgs().size() == 4
        task.getArgs()[0] == '--initial-arg'
        task.getArgs()[1] == 'value'
        task.getArgs()[2] == doc1.path
    }

    def "should execute when arguments are present"() {
        given: "a document model change"
        def doc1 = createDocumentFile('doc1.json')
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when: "handleChanges adds arguments"
        task.handleChanges(inputChanges, 'json')
        
        and: "exec is called"
        task.exec()

        then: "execution was attempted"
        task.executionAttempted
    }

    def "should skip execution when no arguments added"() {
        given: "no changes"
        def inputChanges = mockInputChanges([])

        when: "handleChanges sets defaultArgsSize but adds no args"
        task.handleChanges(inputChanges, 'json')
        
        and: "exec is called"
        task.exec()

        then: "execution was skipped"
        !task.executionAttempted
    }

    def "should skip execution when only default args present"() {
        given: "task has some initial args before handling changes"
        task.args("--default-arg", "value")
        
        and: "no file changes"
        def inputChanges = mockInputChanges([])

        when: "handleChanges sets defaultArgsSize and adds no new args"
        task.handleChanges(inputChanges, 'json')
        
        and: "exec is called"
        task.exec()

        then: "execution was skipped because args.size() == defaultArgsSize"
        !task.executionAttempted
    }

    // Helper methods

    InputChanges mockInputChanges(List<FileChange> changes) {
        Mock(InputChanges) {
            getFileChanges(task.inputDir) >> changes
        }
    }

    FileChange mockFileChange(File file, ChangeType type, FileType fileType) {
        Mock(FileChange) {
            getFile() >> file
            getChangeType() >> type
            getFileType() >> fileType
        }
    }

    File createDocumentFile(String filename) {
        createDocumentFile(inputDir, filename)
    }

    Map readCache() {
        readCache(cacheFile)
    }

    void writeCache(Map data) {
        writeCache(cacheFile, data)
    }

    // Test implementation of ModelConversionTask
    static abstract class TestModelConversionTask extends ModelConversionTask {
        boolean executionAttempted = false
        private int testDefaultArgsSize = 0

        @Override
        void handleChanges(InputChanges inputChanges, String outputFileEnding) {
            testDefaultArgsSize = getArgs().size()
            super.handleChanges(inputChanges, outputFileEnding)
        }

        @Override
        void exec() {
            if (getArgs().size() > testDefaultArgsSize) {
                executionAttempted = true
                // Don't call super.exec() to avoid actual JavaExec
            }
        }
    }
}
