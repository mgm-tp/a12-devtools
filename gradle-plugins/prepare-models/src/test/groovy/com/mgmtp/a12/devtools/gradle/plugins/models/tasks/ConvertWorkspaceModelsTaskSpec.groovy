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
package com.mgmtp.a12.devtools.gradle.plugins.models.tasks

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import spock.lang.Specification
import spock.lang.TempDir

class ConvertWorkspaceModelsTaskSpec extends Specification {

    @TempDir
    File testProjectDir

    Project project
    ConvertWorkspaceModelsTask task

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()
        project.pluginManager.apply('java')

        task = project.tasks.register('convertWorkspaceModels', ConvertWorkspaceModelsTask).get()
    }

    def "wcfOutputDir should be a subdirectory of the task's temporaryDir"() {
        expect:
        task.wcfOutputDir.parentFile.canonicalPath == task.temporaryDir.canonicalPath
    }

    def "should clean output directory before conversion"() {
        given:
        def outputDir = new File(testProjectDir, 'build/converted')
        outputDir.mkdirs()
        new File(outputDir, 'stale.json').createNewFile()
        task.outputDir.set(outputDir)

        when:
        task.cleanOutputDir()

        then:
        outputDir.exists()
        outputDir.listFiles().length == 0
    }

    def "copyConvertedModels should copy <wcfOut>/data/models contents into outputDir, stripping the data/models prefix"() {
        given:
        def outputDir = new File(testProjectDir, 'build/converted')
        task.outputDir.set(outputDir)
        task.cleanOutputDir()

        def wcfModelsDir = new File(task.wcfOutputDir, 'data/models')
        wcfModelsDir.mkdirs()
        new File(wcfModelsDir, 'model1.json').text = '{"name":"m1"}'
        new File(wcfModelsDir, 'sub').mkdirs()
        new File(wcfModelsDir, 'sub/model2.json').text = '{"name":"m2"}'

        when:
        task.copyConvertedModels()

        then:
        new File(outputDir, 'model1.json').exists()
        new File(outputDir, 'sub/model2.json').exists()
        !new File(outputDir, 'data').exists()
    }

    def "copyConvertedModels should fail loudly if the WCF conversion did not produce a data/models directory"() {
        given:
        def outputDir = new File(testProjectDir, 'build/converted')
        task.outputDir.set(outputDir)
        task.cleanOutputDir()
        task.wcfOutputDir.mkdirs()

        when:
        task.copyConvertedModels()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('data/models')
    }

    def "generateValidationCode has no task-level convention (plugin supplies the default)"() {
        expect:
        !task.generateValidationCode.isPresent()
    }

    def "validateConversionClasspath fails when the conversion classpath is empty"() {
        given:
        task.conversionClasspath.setFrom(project.files())

        when:
        task.validateConversionClasspath()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('conversionClasspath')
    }

    def "validateConversionClasspath passes when the conversion classpath is non-empty"() {
        given:
        task.conversionClasspath.setFrom(project.files(new File(testProjectDir, 'lib.jar').tap { createNewFile() }))

        when:
        task.validateConversionClasspath()

        then:
        notThrown(Exception)
    }

    def "copyValidationCode copies <wcfOut>/data/code contents into validationCodeOutputDir"() {
        given:
        def codeOut = new File(testProjectDir, 'build/expanded-code')
        task.validationCodeOutputDir.set(codeOut)

        def wcfCodeDir = new File(task.wcfOutputDir, 'data/code')
        wcfCodeDir.mkdirs()
        new File(wcfCodeDir, 'myModel.validation.js').text = 'var x = 1;'

        when:
        task.copyValidationCode()

        then:
        new File(codeOut, 'myModel.validation.js').exists()
        new File(codeOut, 'myModel.validation.js').text == 'var x = 1;'
    }

    def "copyValidationCode is a no-op when the WCF conversion produced no data/code directory"() {
        given:
        def codeOut = new File(testProjectDir, 'build/expanded-code')
        task.validationCodeOutputDir.set(codeOut)
        task.wcfOutputDir.mkdirs()

        when:
        task.copyValidationCode()

        then:
        notThrown(Exception)
        !codeOut.exists() || codeOut.listFiles().length == 0
    }
}
