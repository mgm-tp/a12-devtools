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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import groovy.json.JsonSlurper

import com.mgmtp.a12.gradle.utils.ModelHelper

@CacheableTask
abstract class ModelConversionTask extends JavaExec {
    ModelConversionTask() {
        group = 'prepare-models-plugin'
        description = 'The base task for document model tasks (like expansion and code generation).'
    }

    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputDirectory
    abstract DirectoryProperty getInputDir()

    @Internal
    final RegularFileProperty cacheFile = project.objects.fileProperty()

    @Internal
    final Property<Boolean> enableLog = project.objects.property(Boolean)

    @OutputDirectory
    abstract DirectoryProperty getOutputDir()

    private int defaultArgsSize;

    void log(String message) {
        if (enableLog.getOrElse(false)) {
            logger.lifecycle("[prepare-models] - ${message}")
        }
    }

    List<String> getDependentsRecursive(final String inputFilePath, final Map<String, List<String>> dependents) {
        final List<String> dependentPaths = []

        dependents[inputFilePath].each {
            dependentPaths.add(it)

            // collect transitive dependencies
            dependentPaths.addAll(getDependentsRecursive(it, dependents))
        }

        return dependentPaths
    }

    List<String> getDependents(final String inputFilePath) {
        final Map<String, List<String>> dependents = new JsonSlurper().parse(cacheFile.get().asFile)

        return getDependentsRecursive(inputFilePath, dependents)
    }

    File getOutputFile(final String inputFilePath, final String fileEnding) {
        final String name = new File(inputFilePath).name
        final String fileName = name.take(name.lastIndexOf('.'))
        return project.file("${outputDir.get()}/${fileName}.${fileEnding}")
    }

    void handleChanges(final InputChanges inputChanges, final String outputFileEnding) {

        defaultArgsSize = args.size()

        final List<String> changedInputs = []
        final Set<String> processedInputs = []

        inputChanges.getFileChanges(inputDir).each { change ->

            final File inputFile = change.file

            if (
                change.fileType != FileType.FILE ||
                (change.changeType != ChangeType.REMOVED && !ModelHelper.isDocumentModel(inputFile))
            ) return

            final File outputFile = getOutputFile(inputFile.path, outputFileEnding)

            if (change.changeType == ChangeType.REMOVED) {
                outputFile.delete()
                log("${inputFile.name}: removed from output because it was deleted")
            } else {
                args inputFile.path
                args outputFile.path

                changedInputs.add(inputFile.path)
                processedInputs.add(inputFile.path)
                log("${inputFile.path}: added because it was ${change.changeType.toString()} in inputDir")
            }
        }

        changedInputs.each { inputPath ->

            // for each changed input, the dependent inputs also need to be considered
            getDependents(inputPath).each { dependentInputPath ->

                // ...if they are not part of the processed set already
                if (!processedInputs.contains(dependentInputPath)) {
                    args dependentInputPath
                    args getOutputFile(dependentInputPath, outputFileEnding).path

                    processedInputs.add(dependentInputPath)
                    log("${dependentInputPath}: added because it includes ${inputPath}, which was changed")
                }
            }
        }
    }

    @Override
    void exec() {
        if (args.size() > defaultArgsSize) {
            super.exec();
        } else {
            logger.lifecycle('Nothing to do')
        }
    }
}
