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

import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.ConvertWorkspaceModelsTask
import com.mgmtp.a12.devtools.gradle.plugins.models.tasks.MigrateDocumentModelsTask

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
        project.prepareModels.generateValidationCode.convention(false)
        project.prepareModels.validationConverterVersion.convention('0.5.0')
        // No stable dataservices-wcf-cli release exists yet at a version compatible with the pinned
        // wcf-core; move to a released version once upstream cuts one (see prepare-models-validation-
        // converter/build.gradle for the matching wcf-core pin).
        project.prepareModels.wcfCliVersion.convention('1.0.1-build.20260729')
        project.prepareModels.validateDataDocuments.convention(false)
        project.prepareModels.validateDataModelsVersion.convention('0.1.0')
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

        // Create a logging configuration for the migrateDocumentModels task
        def logging = project.configurations.create('prepareModelsLogging') {
            visible = false
            canBeConsumed = false
            canBeResolved = true
            description = 'Logging implementation for the migrateDocumentModels task.'
            defaultDependencies { deps ->
                deps.add(project.dependencies.create('org.slf4j:slf4j-simple:2.0.16'))
            }
        }

        def conversion = project.configurations.create('prepareModelsConversion') {
            visible = false
            canBeConsumed = false
            canBeResolved = true
            description = 'WCF conversion classpath: dataservices-wcf-cli (WCF\'s own CLI entry point, run ' +
                'directly as the forked mainClass) plus the prepare-models-validation-converter library ' +
                '(ValidationCodeConverter) and their transitive deps (wcf-core, the RMC converter ' +
                'pipeline, kernel codegen, Spring Boot).'
        }

        // Dependencies are added after the project is evaluated so that user-configured
        // extension properties (kernelMdFacadeVersion, validationConverterVersion) are
        // fully resolved before they are read.
        project.afterEvaluate {
            project.dependencies.add('prepareModelsConversion',
                "com.mgmtp.a12.dataservices.wcf:dataservices-wcf-cli:${project.prepareModels.wcfCliVersion.get()}"
            )
            project.dependencies.add('prepareModelsConversion',
                "com.mgmtp.a12.devtools.plugins:prepare-models-validation-converter:${project.prepareModels.validationConverterVersion.get()}"
            )
            if (project.prepareModels.validateDataDocuments.get()) {
                project.dependencies.add('prepareModelsConversion',
                    "com.mgmtp.a12.devtools.plugins:validate-data-models:${project.prepareModels.validateDataModelsVersion.get()}"
                )
            }
            String kernelVersion = project.prepareModels.kernelMdFacadeVersion.orNull
                    ?: detectKernelVersionFromBuildscript(project)
            if (kernelVersion != null) {
                project.dependencies.add('prepareModelsConversion', "com.mgmtp.a12.kernel:kernel-md-facade:${kernelVersion}")
            }
        }

        project.tasks.register('convertWorkspaceModels', ConvertWorkspaceModelsTask) {
            conversionClasspath.setFrom(conversion)

            generateValidationCode.set(project.prepareModels.generateValidationCode)
            validateDataDocuments.set(project.prepareModels.validateDataDocuments)
            validationCodeOutputDir.set(project.layout.buildDirectory.dir('expanded-code'))

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
            dependsOn project.tasks.named('convertWorkspaceModels')

            from(project.tasks.named('convertWorkspaceModels').flatMap { it.outputDir }) {
                exclude '**/*_ei.json'
            }
            // Validation JS generated inside the WCF converter (build/expanded-code). Empty when
            // generateValidationCode is off, so this contributes nothing in that case.
            from project.tasks.named('convertWorkspaceModels').flatMap { it.validationCodeOutputDir }

            into project.prepareModels.outputDir

            group = 'prepare-models-plugin'
            description = 'Helper task to merge the WCF workspace conversion output (expanded models + validation code).'
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

    private static String detectKernelVersionFromBuildscript(Project project) {
        try {
            def artifact = project.buildscript.configurations.classpath
                    .resolvedConfiguration.resolvedArtifacts
                    .find {
                        it.moduleVersion.id.module.group == 'com.mgmtp.a12.kernel' &&
                        it.moduleVersion.id.module.name == 'kernel-md-facade'
                    }
            return artifact?.moduleVersion?.id?.version
        } catch (Exception e) {
            project.logger.debug('[prepare-models] Could not auto-detect kernel-md-facade version: {}', e.message)
            return null
        }
    }
}
