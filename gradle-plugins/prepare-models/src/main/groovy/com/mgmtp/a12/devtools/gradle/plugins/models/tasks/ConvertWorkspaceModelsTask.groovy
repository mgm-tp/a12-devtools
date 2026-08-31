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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

import javax.inject.Inject

import com.mgmtp.a12.devtools.gradle.plugins.models.ValidateHelper

@CacheableTask
abstract class ConvertWorkspaceModelsTask extends DefaultTask {

    // WCF always nests its output under <root>/data/models. We pass it a private root
    // under temporaryDir, then copy <root>/data/models/** flat into outputDir.
    static final String WCF_OUTPUT_SUBDIR = 'wcf-output'
    static final String WCF_PRODUCED_NESTING = 'data/models'
    static final String WCF_PRODUCED_CODE_NESTING = 'data/code'
    // WCF's own CLI entry point, run in "library mode": converters are already resolved onto
    // conversionClasspath, so no -c jar is needed - WcfCli's @SpringBootApplication(scanBasePackages
    // = "com.mgmtp.a12") finds them the same way it finds RMC's own converter pipeline.
    static final String WCF_CLI_MAIN_CLASS = 'com.mgmtp.a12.dataservices.wcf.WcfCli'

    @Inject
    abstract ExecOperations getExecOperations()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    ConvertWorkspaceModelsTask() {
        group = 'prepare-models-plugin'
        description = 'Converts the model workspace using the WCF library (expands document/combination models, handles fragments).'
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getInputDir()

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    /**
     * Full classpath for the forked conversion JVM: {@code dataservices-wcf-cli} (WCF's own CLI, run
     * directly as {@code mainClass}) plus the prepare-models-validation-converter library and their
     * transitive deps (wcf-core, the RMC converter pipeline, kernel codegen, Spring Boot). Wired by the
     * plugin from a single resolvable configuration; consumers do not set this directly.
     */
    @Classpath
    abstract ConfigurableFileCollection getConversionClasspath()

    /**
     * When true, the ValidationCodeConverter is enabled via {@code -Dvalidation.codegen.enabled=true}.
     */
    @Input
    @Optional
    abstract Property<Boolean> getGenerateValidationCode()

    /**
     * When true, the DataDocumentValidationConverter is enabled via
     * {@code -Ddata.document.validation.enabled=true}. The converter reads every data document from
     * disk and throws with a full list of broken files if any fail validation.
     */
    @Input
    @Optional
    abstract Property<Boolean> getValidateDataDocuments()

    /**
     * Validation JS produced inside WCF ({@code <wcfOut>/data/code/**}), copied here so
     * {@code mergeOutputs} can fold it into the final model output. Only populated when
     * {@link #getGenerateValidationCode()} is true.
     */
    @OutputDirectory
    @Optional
    abstract DirectoryProperty getValidationCodeOutputDir()

    /**
     * Private workspace root passed to WCF as its output directory. Lives under the task's temporaryDir
     * so it does not pollute outputDir with WCF's hardcoded {@code data/models} nesting.
     */
    @Internal
    File getWcfOutputDir() {
        return new File(temporaryDir, WCF_OUTPUT_SUBDIR)
    }

    /**
     * Guards against a plugin misconfiguration: the conversion classpath must be wired (non-empty),
     * otherwise the forked JVM would have no WcfCli/converters to run.
     */
    void validateConversionClasspath() {
        if (conversionClasspath.empty) {
            throw new IllegalStateException(
                'conversionClasspath is empty. The prepare-models plugin must wire dataservices-wcf-cli, ' +
                'the prepare-models-validation-converter library, and their transitive dependencies.'
            )
        }
    }

    void cleanOutputDir() {
        def out = outputDir.get().asFile
        out.deleteDir()
        out.mkdirs()
    }

    /**
     * After WCF has run, copies the contents of {@code <wcfOutputDir>/data/models}
     * into {@code outputDir}, stripping the {@code data/models} prefix.
     */
    void copyConvertedModels() {
        def producedDir = new File(wcfOutputDir, WCF_PRODUCED_NESTING)
        if (!producedDir.exists() || !producedDir.isDirectory()) {
            throw new IllegalStateException(
                "WCF did not produce expected output directory '${WCF_PRODUCED_NESTING}' " +
                "under ${wcfOutputDir.absolutePath}. The conversion may have failed silently."
            )
        }
        fileSystemOperations.copy {
            from producedDir
            into outputDir.get().asFile
        }
    }

    /**
     * Copies {@code <wcfOutputDir>/data/code} (if present) into {@code validationCodeOutputDir}.
     * No-op when the directory was not produced (e.g. feature disabled or no document models).
     */
    void copyValidationCode() {
        if (!validationCodeOutputDir.isPresent()) {
            return
        }
        def producedDir = new File(wcfOutputDir, WCF_PRODUCED_CODE_NESTING)
        if (!producedDir.exists() || !producedDir.isDirectory()) {
            return
        }
        fileSystemOperations.copy {
            from producedDir
            into validationCodeOutputDir.get().asFile
        }
    }

    @TaskAction
    void convert() {
        ValidateHelper.validateAndPreparePaths(inputDir.get().asFile, outputDir.get().asFile)

        validateConversionClasspath()

        def codegenEnabled = generateValidationCode.getOrElse(false)
        def validateDocs = validateDataDocuments.getOrElse(false)

        cleanOutputDir()
        wcfOutputDir.deleteDir()
        wcfOutputDir.mkdirs()

        logger.lifecycle(
            "[prepare-models] Running WCF conversion: workspace=${inputDir.get().asFile}, " +
            "wcfOutput=${wcfOutputDir} (will republish to ${outputDir.get().asFile})"
        )

        execOperations.javaexec {
            // Run WCF's own CLI directly, in library mode: the whole conversion classpath (wcf-cli +
            // wcf-core + RMC pipeline + our ValidationCodeConverter/DataDocumentValidationConverter +
            // kernel codegen + Spring Boot) is resolved by Gradle and placed on -cp, so WcfCli's own
            // @SpringBootApplication(scanBasePackages = "com.mgmtp.a12") finds every @WcfConverter
            // already on the classpath - no -c jar, no classloader augmentation, no launcher of our own.
            it.classpath(conversionClasspath)
            it.mainClass.set(WCF_CLI_MAIN_CLASS)
            if (codegenEnabled) {
                it.systemProperty('validation.codegen.enabled', 'true')
            }
            if (validateDocs) {
                it.systemProperty('data.document.validation.enabled', 'true')
            }
            it.args inputDir.get().asFile.absolutePath
            it.args wcfOutputDir.absolutePath
        }

        copyConvertedModels()
        if (codegenEnabled) {
            copyValidationCode()
        }
    }
}
