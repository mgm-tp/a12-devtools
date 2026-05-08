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

import java.nio.file.Path
import java.nio.file.Paths

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Optional

import org.gradle.work.InputChanges

import com.mgmtp.a12.devtools.gradle.plugins.models.ValidateHelper

@CacheableTask
abstract class GenerateValidationCodeTask extends ModelConversionTask {
    GenerateValidationCodeTask() {
        mainClass.set('com.mgmtp.a12.kernel.md.model.a12internal.services.codegen.cli.BatchVkValidationCodeGeneratorJs')
        group = 'prepare-models-plugin'
        description = 'Generates the validation code for the document models in the configured output directory.'
        
        doFirst {
            ValidateHelper.validateKernelLibrary(
                'com.mgmtp.a12.kernel.md.model.a12internal.services.codegen.cli.BatchVkValidationCodeGeneratorJs',
                'com.mgmtp.a12.kernel:kernel-md-model',
                classpath
            )
            ValidateHelper.validateAndPreparePaths(
                inputDir.get().asFile,
                outputDir.get().asFile
            )
        }
    }

    @TaskAction
    void generate(InputChanges inputChanges) {
        final Path pathBase = inputDir.get().asFile.toPath()

        args "-w"
        args pathBase.toString()

        handleChanges(inputChanges, "validation.js")
    }
}
