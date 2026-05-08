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

import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.GenerateValidationCodeTask
import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.ExpandDocumentModelsTask
import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.MigrateDocumentModelsTask
import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.UpdateDependenciesTask
import com.mgmtp.a12.gradle.utils.ModelHelper

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Copy

class PrepareModelsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply('java')

        project.extensions.create('prepareModels', PrepareModelsPluginExtension)

        project.prepareModels.inputDir.convention(project.layout.projectDirectory.dir('src'))
        project.prepareModels.outputDir.convention(project.layout.projectDirectory.dir('../../target/models'))
        project.prepareModels.enableLog.convention(false)
        project.prepareModels.eachFileAction.convention(new Action() {
            @Override
            void execute(Object fileCopyDetails) {
                fileCopyDetails.path = fileCopyDetails.name
            }
        })

        // Map Gradle's log level to slf4j-simple level
        def taskLogLevel = {
            switch (project.gradle.startParameter.logLevel) {
                case LogLevel.DEBUG: return 'debug'
                case LogLevel.INFO: return 'info'
                case LogLevel.LIFECYCLE: return 'warn'
                case LogLevel.WARN: return 'warn'
                case LogLevel.QUIET: return 'error'
                case LogLevel.ERROR: return 'error'
                default: return 'warn'
            }
        }()

        // Create a logging configuration for the code generation tasks
        def logging = project.configurations.create('prepareModelsLogging') {
            visible = false
            canBeConsumed = false
            canBeResolved = true
            description = 'Logging implementation for prepare-models code generation tasks.'
            defaultDependencies { deps ->
                deps.add(project.dependencies.create('org.slf4j:slf4j-simple:2.0.16'))
            }
        }

        project.tasks.register('updateDependencies', UpdateDependenciesTask) {
            inputDir.set(project.prepareModels.inputDir)
            cacheFile.set(project.layout.buildDirectory.file('dmIncludeCache'))

            mustRunAfter project.tasks.named('compileJava')
            mustRunAfter project.tasks.named('processResources')
        }

        project.tasks.register('generateValidationCode', GenerateValidationCodeTask) {
            dependsOn project.tasks.named('updateDependencies')
            cacheFile.set(project.tasks.named('updateDependencies').flatMap { it.cacheFile })
            enableLog.set(project.prepareModels.enableLog)

            classpath project.buildscript.configurations.classpath
            classpath logging

            jvmArgs "-Dorg.slf4j.simpleLogger.defaultLogLevel=${taskLogLevel}"

            inputDir.set(project.prepareModels.inputDir)
            outputDir.set(project.layout.buildDirectory.dir('code'))
        }

        project.tasks.register('expandDocumentModels', ExpandDocumentModelsTask) {
            dependsOn project.tasks.named('updateDependencies')
            cacheFile.set(project.tasks.named('updateDependencies').flatMap { it.cacheFile })
            enableLog.set(project.prepareModels.enableLog)

            classpath project.buildscript.configurations.classpath
            classpath logging

            jvmArgs "-Dorg.slf4j.simpleLogger.defaultLogLevel=${taskLogLevel}"

            inputDir.set(project.prepareModels.inputDir)
            outputDir.set(project.layout.buildDirectory.dir('expanded'))
        }

        project.tasks.register('copyOtherFiles', Copy) {
            from project.prepareModels.inputDir
            into project.prepareModels.outputDir

            include('**/*.json')
            exclude { ModelHelper.isDocumentModel(it.file) }
            setIncludeEmptyDirs(false)

            eachFile(project.prepareModels.eachFileAction.get())

            group = 'prepare-models-plugin'
            description = 'Helper task to copy all files to the output directory that are not document models (which are processed by other tasks).'
        }

        project.tasks.register('mergeOutputs', Copy) {
            dependsOn project.tasks.named('generateValidationCode'), project.tasks.named('expandDocumentModels')

            from project.tasks.named('generateValidationCode').flatMap { it.outputDir }
            from project.tasks.named('expandDocumentModels').flatMap { it.outputDir }

            into project.prepareModels.outputDir

            group = 'prepare-models-plugin'
            description = 'Helper task to merge outputs of code generation and document model expansion.'
        }

        project.tasks.register('prepareModels') {
            dependsOn project.tasks.named('mergeOutputs'), project.tasks.named('copyOtherFiles')
            group = 'prepare-models-plugin'
            description = 'Prepares a runtime model workspace by copying all models to the output directory, expanding document models and generating validation code.'
        }

        project.tasks.register('migrateDocumentModels', MigrateDocumentModelsTask) {
            classpath project.buildscript.configurations.classpath
            classpath logging

            jvmArgs "-Dorg.slf4j.simpleLogger.defaultLogLevel=${taskLogLevel}"

            workingDir project.prepareModels.inputDir.get()
            args project.prepareModels.inputDir.get()
        }
    }
}
