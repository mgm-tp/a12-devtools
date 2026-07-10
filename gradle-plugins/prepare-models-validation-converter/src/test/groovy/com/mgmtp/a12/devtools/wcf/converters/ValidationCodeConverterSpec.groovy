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
import com.mgmtp.a12.dataservices.wcf.domain.ModelTuple
import com.mgmtp.a12.dataservices.wcf.domain.Workspace
import com.mgmtp.a12.model.header.Header

import spock.lang.Specification

class ValidationCodeConverterSpec extends Specification {

    ValidationJsGenerator generator = Mock()
    FileTuple fileTuple = Mock()
    FileTupleFactory fileTupleFactory = { String p, byte[] c -> fileTuple } as FileTupleFactory

    private ModelTuple model(String id, String type, String content) {
        Header header = Stub(Header) {
            getId() >> id
            getModelType() >> type
        }
        return Stub(ModelTuple) {
            getHeader() >> header
            getContent() >> content
        }
    }

    def "returns the workspace unchanged and generates nothing when disabled"() {
        given:
        Workspace workspace = Mock()
        def converter = new ValidationCodeConverter(false, generator, fileTupleFactory)

        when:
        def result = converter.convert(workspace)

        then:
        result.is(workspace)
        0 * workspace.getModels()
        0 * generator.generate(_)
    }

    def "generates a validation.js FileTuple for each document model when enabled"() {
        given:
        Map<String, FileTuple> files = [:]
        Workspace workspace = Stub(Workspace) {
            getModels() >> ['myModel': model('myModel', 'document', '{"x":1}')]
            getFiles() >> files
        }
        def converter = new ValidationCodeConverter(true, generator, fileTupleFactory)

        when:
        converter.convert(workspace)

        then:
        1 * generator.generate('{"x":1}') >> 'JS'.bytes
        files[ValidationCodeConverter.OUTPUT_PREFIX + 'myModel' + ValidationCodeConverter.OUTPUT_SUFFIX].is(fileTuple)
    }

    def "skips models that are not document models"() {
        given:
        Map<String, FileTuple> files = [:]
        Workspace workspace = Stub(Workspace) {
            getModels() >> ['f': model('f', 'form', '{}')]
            getFiles() >> files
        }
        def converter = new ValidationCodeConverter(true, generator, fileTupleFactory)

        when:
        converter.convert(workspace)

        then:
        0 * generator.generate(_)
        files.isEmpty()
    }

    def "generates a separate FileTuple for each of multiple document models"() {
        given:
        Map<String, FileTuple> files = [:]
        Workspace workspace = Stub(Workspace) {
            getModels() >> [
                'a': model('a', 'document', '{"a":1}'),
                'b': model('b', 'document', '{"b":2}'),
            ]
            getFiles() >> files
        }
        def converter = new ValidationCodeConverter(true, generator, fileTupleFactory)

        when:
        converter.convert(workspace)

        then:
        1 * generator.generate('{"a":1}') >> 'A'.bytes
        1 * generator.generate('{"b":2}') >> 'B'.bytes
        files.size() == 2
        files.containsKey(ValidationCodeConverter.OUTPUT_PREFIX + 'a' + ValidationCodeConverter.OUTPUT_SUFFIX)
        files.containsKey(ValidationCodeConverter.OUTPUT_PREFIX + 'b' + ValidationCodeConverter.OUTPUT_SUFFIX)
    }

    def "fails fast when generation throws"() {
        given:
        Workspace workspace = Stub(Workspace) {
            getModels() >> ['bad': model('bad', 'document', 'not-json')]
            getFiles() >> [:]
        }
        generator.generate(_) >> { throw new IllegalStateException('boom') }
        def converter = new ValidationCodeConverter(true, generator, fileTupleFactory)

        when:
        converter.convert(workspace)

        then:
        thrown(IllegalStateException)
    }
}
