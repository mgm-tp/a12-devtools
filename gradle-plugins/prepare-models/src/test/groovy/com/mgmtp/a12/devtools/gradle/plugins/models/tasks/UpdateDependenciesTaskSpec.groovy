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

class UpdateDependenciesTaskSpec extends Specification implements TaskTestHelpers {

    @TempDir
    File tempDir

    UpdateDependenciesTask task
    File cacheFile
    File inputDir

    def setup() {
        def project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        task = project.tasks.create('updateDeps', UpdateDependenciesTask)

        inputDir = new File(tempDir, 'models')
        inputDir.mkdirs()

        cacheFile = new File(tempDir, 'cache.json')
        task.inputDir.set(inputDir)
        task.cacheFile.set(cacheFile)
    }

    def "should create empty cache on first run with no files"() {
        given:
        def inputChanges = mockInputChanges([])

        when:
        task.update(inputChanges)

        then:
        cacheFile.exists()
        readCache() == [:]
    }

    def "should add dependency when document with includes is added"() {
        given: "a document that includes another document"
        def doc1 = createDocumentFile('doc1.json', ['included-doc'])
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "cache maps included-doc to doc1"
        def cache = readCache()
        cache[new File(inputDir, 'included-doc.json').path] == [doc1.path]
    }

    def "should handle multiple includes in a single document"() {
        given: "a document that includes multiple other documents"
        def doc1 = createDocumentFile('doc1.json', ['included-1', 'included-2', 'included-3'])
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "cache maps all included documents to doc1"
        def cache = readCache()
        cache[new File(inputDir, 'included-1.json').path] == [doc1.path]
        cache[new File(inputDir, 'included-2.json').path] == [doc1.path]
        cache[new File(inputDir, 'included-3.json').path] == [doc1.path]
    }

    def "should handle multiple documents including the same document"() {
        given: "two documents that both include the same document"
        def doc1 = createDocumentFile('doc1.json', ['shared-doc'])
        def doc2 = createDocumentFile('doc2.json', ['shared-doc'])
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE),
            mockFileChange(doc2, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "cache maps shared-doc to both doc1 and doc2"
        def cache = readCache()
        cache[new File(inputDir, 'shared-doc.json').path].sort() == [doc1.path, doc2.path].sort()
    }

    def "should remove file from all dependencies when deleted"() {
        given: "existing cache with dependencies"
        def doc1Path = new File(inputDir, 'doc1.json').path
        writeCache([
            (new File(inputDir, 'included-1.json').path): [doc1Path],
            (new File(inputDir, 'included-2.json').path): [doc1Path]
        ])

        and: "doc1 is removed"
        def inputChanges = mockInputChanges([
            mockFileChange(new File(inputDir, 'doc1.json'), ChangeType.REMOVED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "doc1 is removed from all dependency lists"
        def cache = readCache()
        cache[new File(inputDir, 'included-1.json').path] == []
        cache[new File(inputDir, 'included-2.json').path] == []
    }

    def "should update dependencies when includes change"() {
        given: "doc1 originally includes included-1"
        def doc1 = new File(inputDir, 'doc1.json')
        writeCache([
            (new File(inputDir, 'included-1.json').path): [doc1.path]
        ])

        and: "doc1 is modified to include included-2 instead"
        createDocumentFile('doc1.json', ['included-2'])
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "cache reflects new dependency"
        def cache = readCache()
        cache[new File(inputDir, 'included-2.json').path].contains(doc1.path)
    }

    def "should ignore non-file changes"() {
        given: "a directory change"
        def dir = new File(inputDir, 'subdir')
        dir.mkdirs()
        def inputChanges = mockInputChanges([
            mockFileChange(dir, ChangeType.ADDED, FileType.DIRECTORY)
        ])

        when:
        task.update(inputChanges)

        then: "cache remains empty"
        readCache() == [:]
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
        task.update(inputChanges)

        then: "cache remains empty"
        readCache() == [:]
    }

    def "should preserve existing cache entries when adding new document"() {
        given: "existing cache with one document"
        def doc1Path = new File(inputDir, 'doc1.json').path
        writeCache([
            (new File(inputDir, 'included-1.json').path): [doc1Path]
        ])

        and: "a new document is added"
        def doc2 = createDocumentFile('doc2.json', ['included-2'])
        def inputChanges = mockInputChanges([
            mockFileChange(doc2, ChangeType.MODIFIED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "both old and new entries exist"
        def cache = readCache()
        cache[new File(inputDir, 'included-1.json').path] == [doc1Path]
        cache[new File(inputDir, 'included-2.json').path] == [doc2.path]
    }

    def "should handle document with no includes"() {
        given: "a document without any includes"
        def doc1 = createDocumentFile('doc1.json', [])
        def inputChanges = mockInputChanges([
            mockFileChange(doc1, ChangeType.ADDED, FileType.FILE)
        ])

        when:
        task.update(inputChanges)

        then: "cache exists but has no entries for this document"
        def cache = readCache()
        !cache.values().any { it.contains(doc1.path) }
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

    File createDocumentFile(String filename, List<String> includes) {
        createDocumentFile(inputDir, filename, includes)
    }

    Map readCache() {
        readCache(cacheFile)
    }

    void writeCache(Map data) {
        writeCache(cacheFile, data)
    }
}
