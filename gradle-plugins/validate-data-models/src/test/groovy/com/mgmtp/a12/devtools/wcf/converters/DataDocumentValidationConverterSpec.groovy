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
package com.mgmtp.a12.devtools.wcf.converters

import com.mgmtp.a12.dataservices.wcf.domain.FileTuple
import com.mgmtp.a12.dataservices.wcf.domain.Workspace

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class DataDocumentValidationConverterSpec extends Specification {

    /**
     * The WCF workspace supplier sets inputDir to the workspace root and creates empty FileTuples.
     * Our converter reads file content from disk via workspace.getInputDir() + workspace-relative key.
     *
     * Test layout mirrors real usage:
     *   <tempDir>/          <- project root (parent of inputDir)
     *   <tempDir>/src/      <- inputDir (workspace.getInputDir())
     *   <tempDir>/src/data/workspacedata_items.json
     *   <tempDir>/src/data/documents/<dmId>/<name>.json
     *
     * fileName values in workspacedata_items.json are project-root-relative: "src/data/...".
     */
    @TempDir
    Path tempDir

    // helpers

    private Path inputDir() { tempDir.resolve('src') }

    private Workspace workspaceWithIndex(Map<String, String> dmKeyToFileName) {
        writeIndex(dmKeyToFileName)
        return stubWorkspace()
    }

    private void writeIndex(Map<String, String> dmKeyToFileName) {
        def docs = dmKeyToFileName.collect { key, fileName ->
            '"' + key + '": {"fileName": "' + fileName + '"}'
        }.join(', ')
        writeFile('src/data/workspacedata_items.json', '{"documents": {' + docs + '}}')
    }

    private void writeFile(String projectRelativePath, String content) {
        Path file = tempDir.resolve(projectRelativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private Workspace stubWorkspace() {
        Workspace ws = Stub(Workspace)
        ws.getInputDir() >> inputDir().toString()
        ws.getFiles() >> buildFilesTupleMap()
        ws.getModels() >> [:]
        return ws
    }

    private Map<String, FileTuple> buildFilesTupleMap() {
        Map<String, FileTuple> map = [:]
        if (!Files.exists(inputDir())) return map
        Files.walk(inputDir()).filter { Files.isRegularFile(it) }.forEach { path ->
            String key = inputDir().relativize(path).toString()
            map[key] = Stub(FileTuple) { getContent() >> null }
        }
        return map
    }

    private static String dataFilePath(String dmId, String baseName) {
        'src/data/documents/' + dmId + '/' + baseName + '.json'
    }

    // tests

    def "returns workspace unchanged and reads no files when disabled"() {
        given:
        Workspace workspace = Mock()
        def converter = new DataDocumentValidationConverter(false, null)

        when:
        def result = converter.convert(workspace)

        then:
        result.is(workspace)
        0 * workspace.getInputDir()
        0 * workspace.getFiles()
    }

    def "returns workspace unchanged when no workspacedata_items.json is present"() {
        given:
        Files.createDirectories(inputDir())
        def converter = new DataDocumentValidationConverter(true, { json, id -> [] } as DataDocumentValidator)

        when:
        def result = converter.convert(stubWorkspace())

        then:
        result != null
        notThrown(Exception)
    }

    def "returns workspace unchanged when workspacedata_items.json has no documents key"() {
        given:
        writeFile('src/data/workspacedata_items.json', '{}')
        def converter = new DataDocumentValidationConverter(true, { json, id -> [] } as DataDocumentValidator)

        when:
        def result = converter.convert(stubWorkspace())

        then:
        result != null
        notThrown(Exception)
    }

    def "returns workspace unchanged when documents map is empty"() {
        given:
        writeFile('src/data/workspacedata_items.json', '{"documents":{}}')
        def converter = new DataDocumentValidationConverter(true, { json, id -> [] } as DataDocumentValidator)

        when:
        converter.convert(stubWorkspace())

        then:
        notThrown(Exception)
    }

    def "returns workspace unchanged when all documents validate cleanly"() {
        given:
        def fileName = dataFilePath('my.doc', 'data')
        writeFile(fileName, '{"document":{"root":{}}}')
        def converter = new DataDocumentValidationConverter(true, { json, id -> [] } as DataDocumentValidator)

        when:
        def result = converter.convert(workspaceWithIndex(['my.doc/uuid-1': fileName]))

        then:
        result != null
        notThrown(Exception)
    }

    def "throws when one document has a notification"() {
        given:
        def fileName = dataFilePath('my.doc', 'data')
        writeFile(fileName, '{"document":{"root":{}}}')
        def validator = { json, id -> ['unknown field: badField'] } as DataDocumentValidator
        def converter = new DataDocumentValidationConverter(true, validator)

        when:
        converter.convert(workspaceWithIndex(['my.doc/uuid-1': fileName]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(fileName)
        ex.message.contains('unknown field: badField')
    }

    def "throws listing all broken documents when multiple fail"() {
        given:
        def fileA = dataFilePath('a.doc', 'a')
        def fileB = dataFilePath('b.doc', 'b')
        writeFile(fileA, '{"document":{}}')
        writeFile(fileB, '{"document":{}}')
        def validator = { json, id -> ['error in ' + id] } as DataDocumentValidator
        def converter = new DataDocumentValidationConverter(true, validator)

        when:
        converter.convert(workspaceWithIndex(['a.doc/uuid-a': fileA, 'b.doc/uuid-b': fileB]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(fileA)
        ex.message.contains(fileB)
        ex.message.contains('2 file(s)')
    }

    def "throws listing only the broken document when one is valid and one is not"() {
        given:
        def fileOk = dataFilePath('ok.doc', 'ok')
        def fileBad = dataFilePath('bad.doc', 'bad')
        writeFile(fileOk, '{"document":{}}')
        writeFile(fileBad, '{"document":{}}')
        def validator = { json, id -> id == 'bad.doc' ? ['oops'] : [] } as DataDocumentValidator
        def converter = new DataDocumentValidationConverter(true, validator)

        when:
        converter.convert(workspaceWithIndex(['ok.doc/uuid-ok': fileOk, 'bad.doc/uuid-bad': fileBad]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains(fileBad)
        !ex.message.contains(fileOk)
        ex.message.contains('1 file(s)')
    }

    def "throws when referenced data file does not exist on disk"() {
        given:
        def missingFile = dataFilePath('missing.doc', 'missing')
        def converter = new DataDocumentValidationConverter(true, { json, id -> [] } as DataDocumentValidator)

        when:
        converter.convert(workspaceWithIndex(['missing.doc/uuid-1': missingFile]))

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains('1 file(s)')
    }

    def "passes extracted document JSON (not full wrapper) to the validator"() {
        given:
        def fileName = dataFilePath('my.doc', 'data')
        writeFile(fileName, '{"documentModelName":"my.doc","document":{"root":{"field":"val"}}}')
        String capturedJson = null
        def validator = { json, id -> capturedJson = json; [] } as DataDocumentValidator
        def converter = new DataDocumentValidationConverter(true, validator)

        when:
        converter.convert(workspaceWithIndex(['my.doc/uuid-1': fileName]))

        then:
        capturedJson != null
        !capturedJson.contains('documentModelName')
        capturedJson.contains('field')
    }
}
