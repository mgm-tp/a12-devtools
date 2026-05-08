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
package com.mgmtp.a12.devtools.gradle.plugins.models

import com.mgmtp.a12.gradle.utils.ModelHelper

import spock.lang.Specification
import spock.lang.TempDir

class ModelHelperSpec extends Specification {

    @TempDir
    File tempDir

    def "isFormModel should return true for form model"() {
        given:
        def formModel = new File(getClass().getResource('/test-models/sample-form.json').toURI())

        expect:
        ModelHelper.isFormModel(formModel)
    }

    def "isFormModel should return false for document model"() {
        given:
        def documentModel = new File(getClass().getResource('/test-models/sample-document.json').toURI())

        expect:
        !ModelHelper.isFormModel(documentModel)
    }

    def "isFormModel should return false for non-JSON file"() {
        given:
        def textFile = new File(getClass().getResource('/test-models/not-a-model.txt').toURI())

        expect:
        !ModelHelper.isFormModel(textFile)
    }

    def "isDocumentModel should return true for document model"() {
        given:
        def documentModel = new File(getClass().getResource('/test-models/sample-document.json').toURI())

        expect:
        ModelHelper.isDocumentModel(documentModel)
    }

    def "isDocumentModel should return false for form model"() {
        given:
        def formModel = new File(getClass().getResource('/test-models/sample-form.json').toURI())

        expect:
        !ModelHelper.isDocumentModel(formModel)
    }

    def "isDocumentModel should return false for non-JSON file"() {
        given:
        def textFile = new File(getClass().getResource('/test-models/not-a-model.txt').toURI())

        expect:
        !ModelHelper.isDocumentModel(textFile)
    }

    def "isModelType should detect correct model type"() {
        given:
        def formModel = new File(getClass().getResource('/test-models/sample-form.json').toURI())
        def documentModel = new File(getClass().getResource('/test-models/sample-document.json').toURI())

        expect:
        ModelHelper.isModelType(formModel, 'form')
        ModelHelper.isModelType(documentModel, 'document')
        !ModelHelper.isModelType(formModel, 'document')
        !ModelHelper.isModelType(documentModel, 'form')
    }

    def "isModelType should return false for file without modelType"() {
        given:
        def invalidModel = new File(getClass().getResource('/test-models/invalid.json').toURI())

        expect:
        !ModelHelper.isModelType(invalidModel, 'form')
        !ModelHelper.isModelType(invalidModel, 'document')
    }

    def "isModelType should respect numberOfLines parameter"() {
        given:
        def modelWithDeepType = new File(tempDir, 'deep-type.json')
        modelWithDeepType.text = '''
{
  "header": {
    "id": "test"
  },
  "other": "data",
  "more": "data",
  "even": "more",
  "still": "more",
  "modelType": "form"
}
'''

        expect:
        !ModelHelper.isModelType(modelWithDeepType, 'form', 5)
        ModelHelper.isModelType(modelWithDeepType, 'form', 20)
    }

    def "getIncludes should return empty list when no modelReferences"() {
        given:
        def documentModel = new File(getClass().getResource('/test-models/sample-document.json').toURI())

        when:
        def includes = ModelHelper.getIncludes(documentModel)

        then:
        includes.isEmpty()
    }

    def "getIncludes should return only include references"() {
        given:
        def documentModel = new File(getClass().getResource('/test-models/document-with-includes.json').toURI())

        when:
        def includes = ModelHelper.getIncludes(documentModel)

        then:
        includes.size() == 2
        includes.contains(new File(documentModel.parent, 'included-form-1.json').path)
        includes.contains(new File(documentModel.parent, 'included-form-2.json').path)
        !includes.any { it.contains('validation-form') }
    }

    def "getIncludes should resolve paths relative to parent directory"() {
        given:
        def documentModel = new File(getClass().getResource('/test-models/document-with-includes.json').toURI())
        def expectedBasePath = documentModel.parent

        when:
        def includes = ModelHelper.getIncludes(documentModel)

        then:
        includes.every { it.startsWith(expectedBasePath) }
        includes.every { it.endsWith('.json') }
    }

    def "getIncludes should handle nested structure in modelReferences"() {
        given:
        def modelFile = new File(tempDir, 'nested-refs.json')
        modelFile.text = '''
{
  "header": {
    "id": "nested",
    "modelType": "document",
    "modelReferences": [
      {
        "purpose": "include",
        "reference": "form1"
      },
      {
        "purpose": "other",
        "reference": "form2"
      },
      {
        "purpose": "include",
        "reference": "form3"
      }
    ]
  },
  "content": {}
}
'''

        when:
        def includes = ModelHelper.getIncludes(modelFile)

        then:
        includes.size() == 2
        includes.contains(new File(tempDir, 'form1.json').path)
        includes.contains(new File(tempDir, 'form3.json').path)
    }
}
