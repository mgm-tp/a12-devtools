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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.work.ChangeType
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

import com.mgmtp.a12.gradle.utils.ModelHelper

@CacheableTask
abstract class UpdateDependenciesTask extends DefaultTask {

    UpdateDependenciesTask() {
        group = 'prepare-models-plugin'
        description = 'A helper task that caches the include dependency information ("who includes who") for document models.'
    }

    @Incremental
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputDirectory
    abstract DirectoryProperty getInputDir()

    @OutputFile
    final RegularFileProperty cacheFile = project.objects.fileProperty()

    @Internal
    Map<String, List<String>> dependents

    void updateDependents(final FileChange change) {
        if (change.changeType == ChangeType.REMOVED) {
            this.dependents.each { key, list ->
                this.dependents[key] = list.findAll { it != change.file.path }
            }
        } else {
            ModelHelper.getIncludes(change.file).each { this.dependents[it] << change.file.path }
        }
    }

    @TaskAction
    void update(InputChanges inputChanges) {
        final def cache = cacheFile.get().asFile
        this.dependents = cache.exists() ? new JsonSlurper().parse(cache).withDefault { [] } : [:].withDefault { [] }

        inputChanges.getFileChanges(inputDir).each { change ->

            final File inputFile = change.file

            if (
                change.fileType != FileType.FILE ||
                (change.changeType != ChangeType.REMOVED && !ModelHelper.isDocumentModel(inputFile))
            ) return

            // at least one document model file changed, update the dependency information for it
            // as there might be new includes now or includes that were removed
            updateDependents(change)
        }

        // store the new info in the cache file
        cache.text = JsonOutput.toJson(this.dependents)
    }
}

