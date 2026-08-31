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
package com.mgmtp.a12.devtools.gradle.plugins.integration.tasks

import com.mgmtp.a12.devtools.gradle.plugins.buildutils.Helper
import com.mgmtp.a12.devtools.gradle.plugins.integration.A12ComponentCatalog
import com.mgmtp.a12.devtools.gradle.plugins.integration.DependencyVersionSpecParser
import com.mgmtp.a12.devtools.gradle.plugins.integration.GradleDependenciesRewriter
import com.mgmtp.a12.devtools.gradle.plugins.integration.PnpmCatalogRewriter

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

/**
 * Rewrites A12 dependency versions supplied via {@code -Pa12DependencyVersions}, stamps
 * the integration build-version into all workspace {@code package.json} files, and prints
 * this component's own integration version (for the pipeline's later build/publish steps).
 *
 * Config-cache safe: the action reads only its own properties, never the Project.
 */
@DisableCachingByDefault(because = 'Rewrites files in place; not yet cacheable.')
abstract class PrepareForIntegrationTask extends DefaultTask {

    /** Runs {@code pnpm list} to enumerate the workspace packages to stamp. */
    @Inject
    abstract ExecOperations getExecOps()

    /** Raw value of {@code -Pa12DependencyVersions}; absent means "rewrite nothing". */
    @Input @Optional
    abstract Property<String> getDependencyVersionsSpec()

    /**
     * The repo's {@code pnpm-workspace.yaml}. The file may not exist
     */
    @Internal
    abstract RegularFileProperty getPnpmWorkspaceFile()

    /**
     * The gradle version catalog to rewrite; defaults to the repo's {@code gradle/libs.versions.toml}
     * but is configurable via {@code integration.libsVersionsFile}. The file may not exist.
     */
    @Internal
    abstract RegularFileProperty getLibsVersionsFile()

    /** This component's own version, captured from {@code project.version} at configuration time. */
    @Input
    abstract Property<String> getComponentVersion()

    /**
     * The pnpm executable used to enumerate the workspace packages to stamp. Absent means
     * {@code pnpm} from {@code PATH}; see {@code IntegrationExtension.pnpmCommand}.
     */
    @Input @Optional
    abstract Property<String> getPnpmCommand()

    /** Additional component mappings merged with the built-in catalog. */
    @Input @Optional
    abstract MapProperty<String, Map<String, List<String>>> getAdditionalComponents()

    /**
     * Extra pnpm named catalogs to rewrite in addition to the built-in {@link PnpmCatalogRewriter#DEFAULT_CATALOGS}.
     * Empty by default.
     */
    @Input @Optional
    abstract ListProperty<String> getAdditionalPnpmCatalogs()

    @TaskAction
    void run() {
        Map<String, Map<String, List<String>>> extra = additionalComponents.getOrElse([:])
        Map<String, String> versions = DependencyVersionSpecParser.parse(dependencyVersionsSpec.getOrElse(''))

        if (!versions.isEmpty()) {
            File workspace = pnpmWorkspaceFile.get().asFile
            List<String> catalogs = PnpmCatalogRewriter.DEFAULT_CATALOGS + additionalPnpmCatalogs.getOrElse([])
            int npm = PnpmCatalogRewriter.rewrite(workspace, versions, catalogs, extra)
            logger.info('[integration] rewrote {} pnpm catalog entrie(s) in {}', npm, workspace.name)

            File toml = libsVersionsFile.get().asFile
            versions.each { component, version ->
                A12ComponentCatalog.gradleTomlKeys(component, extra).each { key ->
                    if (GradleDependenciesRewriter.rewrite(toml, key, version)) {
                        logger.info("[integration] rewrote gradle version key '{}' in {}", key, toml.name)
                    }
                }
            }
        }

        // Stamp the integration build-version into all workspace package.json files.
        String integrationVersion = Helper.computeBuildVersion(componentVersion.get(), 'integration')
        File workspace = pnpmWorkspaceFile.get().asFile
        if (workspace.exists()) {
            Helper.setVersionInWorkspace(execOps, workspace, integrationVersion, pnpmCommand.getOrNull())
            logger.info('[integration] stamped version {} into workspace package.json files', integrationVersion)
        } else {
            logger.info('[integration] no {} - skipping package.json version stamping', workspace.name)
        }

        // QUIET level => the only line printed under `gradle prepareForIntegration -q`.
        logger.quiet(integrationVersion)
    }
}
