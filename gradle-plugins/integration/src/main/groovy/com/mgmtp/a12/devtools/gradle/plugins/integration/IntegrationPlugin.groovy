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

import com.mgmtp.a12.devtools.gradle.plugins.integration.tasks.PrepareForIntegrationTask

import org.gradle.api.Plugin
import org.gradle.api.Project

class IntegrationPlugin implements Plugin<Project> {

    static final String GROUP = 'integration build'

    @Override
    void apply(Project project) {
        def extension = project.extensions.create('integration', IntegrationExtension)
        // A managed ListProperty conventionally defaults to a present-but-empty list, which would
        // make `publishTasks = []` (publish nothing) indistinguishable from "not configured".
        // A null convention leaves the unconfigured property without a value. `buildTasks` needs
        // the same distinction: the unconfigured default builds every project, an explicit list
        // is wired verbatim.
        extension.buildTasks.convention((Iterable<String>) null)
        extension.publishTasks.convention((Iterable<String>) null)
        // Standard A12 repo layout; override via `integration { libsVersionsFile = ... }`.
        extension.libsVersionsFile.convention(project.layout.projectDirectory.file('gradle/libs.versions.toml'))

        // The task names 'prepareForIntegration', 'buildAndTestForIntegration' and
        // 'publishForIntegration' are defined by the BD team and MUST NOT be renamed:
        // BD's automated integration build invokes them by these exact names.
        project.tasks.register('prepareForIntegration', PrepareForIntegrationTask) { task ->
            task.group = GROUP
            task.description = 'Rewrites A12 dependency versions from -Pa12DependencyVersions and prints this component version.'
            task.dependencyVersionsSpec.set(project.providers.gradleProperty('a12DependencyVersions'))
            task.pnpmWorkspaceFile.set(project.layout.projectDirectory.file('pnpm-workspace.yaml'))
            task.libsVersionsFile.set(extension.libsVersionsFile)
            task.pnpmCommand.set(extension.pnpmCommand)
            task.outputs.upToDateWhen { false }
        }

        project.tasks.register('buildAndTestForIntegration') { task ->
            task.group = GROUP
            task.description = 'Builds and runs the integration-relevant tests (default: the full build).'
        }

        project.tasks.register('publishForIntegration') { task ->
            task.group = GROUP
            task.description = 'Publishes the artifacts needed for integration. By default depends on the repo\'s npm publish (npmPublish) and maven publish (publish) tasks; it does not wire the DevApp docker image or documentation tasks. Override integration.publishTasks to control publishing explicitly.'
        }

        project.afterEvaluate {
            // Capture the resolved component version as a plain String (config-cache safe).
            project.tasks.named('prepareForIntegration', PrepareForIntegrationTask) { task ->
                task.componentVersion.set(project.version.toString())
                task.additionalComponents.set(extension.additionalComponents)
                task.additionalPnpmCatalogs.set(extension.additionalPnpmCatalogs)
            }

            def build = project.tasks.named('buildAndTestForIntegration')
            if (extension.buildTasks.present) {
                // The repo pinned its own surface: wire exactly the tasks it lists, resolved
                // against this project the way a task path always is.
                build.configure { it.dependsOn(project.provider { extension.buildTasks.get() }) }
            } else {
                // Convention: the full build of every project. `dependsOn('build')` would stop at
                // the root's `:build`, which is not what invoking `gradle build` does - there a
                // bare name is a selector matching that name in every project, and it is the only
                // way subproject `test` tasks are reached.
                project.allprojects.each { p ->
                    build.configure { it.dependsOn(p.tasks.matching { t -> t.name == 'build' }) }
                }
            }

            def publish = project.tasks.named('publishForIntegration')
            // Configured at all - including an empty list
            if (extension.publishTasks.present) {
                // The repo controls publishing explicitly: wire exactly the tasks it lists.
                publish.configure { it.dependsOn(project.provider { extension.publishTasks.get() }) }
            } else {
                // Convention: wire the npm + maven publish channels that exist, deliberately
                // excluding devapp, docker, and documentation subprojects.
                def excludedProjects = { Project p ->
                    p.name == 'docker' || p.name.contains('devapp') || p.name == 'documentation'
                }
                project.allprojects.each { p ->
                    if (excludedProjects(p)) {
                        return
                    }
                    publish.configure { it.dependsOn(p.tasks.matching { t -> t.name == 'npmPublish' }) }
                    p.plugins.withId('maven-publish') {
                        publish.configure { it.dependsOn(p.tasks.matching { t -> t.name == 'publish' }) }
                    }
                }
                // Wire security scans (must pass before publishing).
                def securityChecks = project.allprojects.collect { p ->
                    p.tasks.matching { t -> t.name == 'runSecurityChecks' }
                }
                securityChecks.each { checks ->
                    publish.configure { it.dependsOn(checks) }
                }
                // `dependsOn` orders nothing between siblings: the scans and the publishes are both
                // dependencies of publishForIntegration, so Gradle was free to publish first and scan
                // afterwards - and did. Order every publish channel behind every scan, so a failing
                // scan aborts the run before an artifact has left the machine.
                project.allprojects.each { p ->
                    p.tasks.matching { t -> t.name == 'npmPublish' || t.name == 'publish' }
                        .configureEach { t -> securityChecks.each { t.mustRunAfter(it) } }
                }
            }
        }
    }
}
