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
package com.mgmtp.a12.devtools.gradle.plugins.integration

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty

abstract class IntegrationExtension {

    /**
     * The gradle version catalog file whose A12 {@code [versions]} keys are rewritten by
     * {@code prepareForIntegration}.
     * <p>
     * Defaults to {@code gradle/libs.versions.toml} relative to the project directory. The file may not exist.
     */
    abstract RegularFileProperty getLibsVersionsFile()

    /**
     * The pnpm executable used to enumerate the workspace packages that
     * {@code prepareForIntegration} stamps the integration version into.
     * <p>
     * Unset by default, which falls back to {@code pnpm} from {@code PATH}. Repos that let
     * {@code node-gradle} provision a pinned pnpm do not have that binary on {@code PATH} --
     * set this to the same value given to {@code node.pnpmCommand}, e.g.
     * {@code pnpmCommand = computePnpmPath().toString()}.
     * <p>
     * When set, a pnpm that cannot enumerate the workspace fails the build rather than
     * silently degrading to stamping only the root {@code package.json}.
     */
    abstract org.gradle.api.provider.Property<String> getPnpmCommand()

    /**
     * Extra pnpm named catalogs in {@code pnpm-workspace.yaml} to rewrite. Use this for a repo that keeps A12
     * dependency versions in a further named catalog, e.g. {@code additionalPnpmCatalogs = ['myCatalog']}.
     * Empty by default.
     */
    abstract ListProperty<String> getAdditionalPnpmCatalogs()

    /**
     * Task names that {@code buildAndTestForIntegration} depends on.
     * <p>
     * Left unset, it runs {@code build} in every project - the same surface as invoking
     * {@code gradle build}, which in a multi-project repo includes the subprojects' tests.
     * <p>
     * Set it to narrow the integration build surface without changing the task's name/contract.
     * An explicit list is wired verbatim: each entry is a task path resolved against the root
     * project, so {@code ['build']} means {@code :build} alone. Name subproject tasks explicitly
     * ({@code [':form-model:test']}) if they should take part.
     */
    abstract ListProperty<String> getBuildTasks()

    /**
     * Task names that {@code publishForIntegration} depends on.
     * <p>
     * Left unset by default: the plugin then auto-detects the standard channels
     * ({@code npmPublish}, plus the maven {@code publish} where {@code maven-publish} is
     * applied), which is the default convention. Set this to take full control of how a repo
     * publishes - e.g. a repo that does not use the A12 artifact-publish plugin, or publishes
     * npm packages differently - without changing the task's name/contract.
     * <p>
     * Setting it to an <em>empty</em> list ({@code publishTasks = []} or {@code publishTasks.empty()})
     * is honoured as an explicit configuration: auto-detection is switched off and
     * {@code publishForIntegration} runs without publishing anything.
     */
    abstract ListProperty<String> getPublishTasks()

    /**
     * Additional component → coordinate mappings to merge into the built-in
     * {@link A12ComponentCatalog}. Use this to add custom components that are not
     * part of the built-in catalog.
     * <p>
     * Format: {@code ['component-name': [npm: ['@scope/pkg'], toml: ['key']]]}.
     * Entries here are merged with (and override) the built-in catalog.
     */
    abstract org.gradle.api.provider.MapProperty<String, Map<String, List<String>>> getAdditionalComponents()
}
