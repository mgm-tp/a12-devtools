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

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.provider.Property

abstract class PrepareModelsPluginExtension {

    abstract DirectoryProperty getInputDir()

    abstract DirectoryProperty getOutputDir()

    abstract Property<Action<FileCopyDetails>> getEachFileAction()

    /**
     * When {@code true}, the {@code convertWorkspaceModels} task enables the
     * {@code ValidationCodeConverter} (order 55) inside the forked WCF conversion JVM
     * so that validation JS is generated alongside the expanded models.
     * Default {@code false}: the WCF converter runs without codegen.
     */
    abstract Property<Boolean> getGenerateValidationCode()

    /**
     * When {@code true}, the {@code DataDocumentValidationConverter} (order 200) is enabled inside the
     * forked WCF conversion JVM. It validates every data document against its document model and throws
     * with a full list of broken files if any are invalid.
     * Default {@code false}: validation is skipped.
     */
    abstract Property<Boolean> getValidateDataDocuments()

    /**
     * Version of the {@code validate-data-models} library
     * ({@code com.mgmtp.a12.devtools.plugins:validate-data-models:<version>}) resolved onto the forked
     * conversion JVM classpath when {@link #getValidateDataDocuments()} is {@code true}.
     * Default {@code "0.1.0"}.
     */
    abstract Property<String> getValidateDataModelsVersion()

    /**
     * Version of the prepare-models-validation-converter library
     * {@code com.mgmtp.a12.devtools.plugins:prepare-models-validation-converter:<version>} resolved onto
     * the forked conversion JVM classpath. The library carries ValidationCodeConverter and pulls the WCF
     * core, the RMC converter pipeline, and kernel codegen transitively, so those versions are fixed by
     * this library rather than overridable per consumer.
     */
    abstract Property<String> getValidationConverterVersion()

    /**
     * Version of {@code dataservices-wcf-cli}
     * ({@code com.mgmtp.a12.dataservices.wcf:dataservices-wcf-cli:<version>}) resolved onto the forked
     * conversion JVM classpath. {@code ConvertWorkspaceModelsTask} runs WCF's own CLI class directly as
     * the forked process's {@code mainClass}, in "library mode" (converters already resolved onto the
     * classpath, so no {@code -c} jar is needed). Must stay compatible with the
     * {@code dataservices-wcf-core} version pulled in transitively by
     * {@link #getValidationConverterVersion()}.
     */
    abstract Property<String> getWcfCliVersion()

    /**
     * Version of {@code kernel-md-facade} placed on the forked WCF conversion JVM classpath
     * for validation code generation. Must match the kernel version used at runtime so that
     * generated validation JS is compatible.
     *
     * <p>When unset (default), the plugin auto-detects the version from the project's buildscript
     * classpath. If kernel is not found there, no explicit kernel dependency is added; the forked
     * JVM receives kernel transitively from {@code wcf-core}, but the version may not match your
     * runtime kernel.
     *
     * <p>Override explicitly when the buildscript classpath does not contain kernel or when a
     * specific version is required.
     */
    abstract Property<String> getKernelMdFacadeVersion()
}
