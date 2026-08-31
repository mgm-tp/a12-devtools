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
package com.mgmtp.a12.devtools.gradle.plugins.buildutils

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.process.ExecOperations

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class Helper {

    /** UTC {@code yyyyMMddHHmmss}, used as the build identifier of integration versions. */
    private static final DateTimeFormatter INTEGRATION_TIMESTAMP =
        DateTimeFormatter.ofPattern('yyyyMMddHHmmss').withZone(ZoneOffset.UTC)

    static String getVersion(File file, String specifiedVersion, String use) {
        if (specifiedVersion != "unspecified") {
            return specifiedVersion;
        }
        String version = new JsonSlurper().parse(file).version
        return computeBuildVersion(version, use)
    }

    /**
     * Applies the build-version transformation for the given {@code use} variant.
     * Replaces {@code -SNAPSHOT} with the appropriate build suffix, or returns the
     * version unchanged if {@code use} is null or unrecognized.
     *
     * <p>The {@code integration} variant is stamped with a UTC timestamp rather than the
     * Jenkins build number, because the integration build chains several components from
     * separate jobs whose build numbers are unrelated and collide across runs. The
     * timestamp is evaluated per call; the integration pipeline keeps it stable across its
     * gradle invocations because {@code prepareForIntegration} writes the computed version
     * into the root {@code package.json}, which {@link #getVersion} reads back (no
     * {@code -SNAPSHOT} remains, so this method is then a no-op).
     */
    static String computeBuildVersion(String version, String use) {
        if ("integration" == use) {
            return version.replaceAll('-SNAPSHOT', "-build.${integrationTimestamp()}.integration")
        }

        Integer buildNumber = Integer.parseInt(System.env.BUILD_NUMBER ?: '0')

        if("ondemand" == use) {
            version = version.replaceAll('-SNAPSHOT', "-build.${buildNumber}.ondemand")
        } else if ("nightly" == use) {
            version = version.replaceAll('-SNAPSHOT', "-build.${buildNumber}")
        } else if ("performance" == use) {
            version = version.replaceAll('-SNAPSHOT', "-build.${buildNumber}.performance")
        }
        return version;
    }

    /** The current UTC time as {@code yyyyMMddHHmmss}, e.g. {@code 20260729134501}. */
    static String integrationTimestamp() {
        return INTEGRATION_TIMESTAMP.format(Instant.now())
    }

    static Iterable<String> getUnifiedCommandLine(final Iterable<String> args) {
        return System.getProperty('os.name').toLowerCase().contains('windows')
            ? Arrays.asList('cmd', '/c') + args.toList()
            : args
    }

    static final String prettyPrintJSON(final Object obj) {
        String prettyJsonStringWith4SpaceIndentation = JsonOutput.prettyPrint(JsonOutput.toJson(obj))
        // Multiline matcher for spaces
        String prettyJsonStringWith2SpaceIndentation = prettyJsonStringWith4SpaceIndentation.replaceAll(/(?m)^( +)/) {
            _, String match -> match.substring(match.length() / 2 as int)
        }
        return prettyJsonStringWith2SpaceIndentation + System.lineSeparator()
    }

    /** Prevent issues with changing line endings */
    static final writeFile(final File file, final String content) {
        file.withWriter { out -> content.eachLine { out.println it } }
    }

    static final Closure createSetVersion(final String version) {
        return { Object packageJSON -> packageJSON.version = version }
    }

    /**
     * Stamps {@code version} into every workspace {@code package.json}, including the root.
     * This is the shared implementation behind the per-repo {@code setVersion} tasks and the
     * integration plugin's version stamping.
     *
     * <p>The package set comes from {@code pnpm list}, so pnpm itself resolves the
     * {@code packages} globs in {@code pnpm-workspace.yaml}. Resolving them here instead
     * meant treating each entry as a literal directory, which silently skipped every
     * package in a repo using {@code ./*} and stamped only the root.
     *
     * <p>{@code execOps} leads because it has no sensible default — Groovy only defaults
     * trailing parameters, and {@code pnpmCommand} takes that slot.
     */
    static void setVersionInWorkspace(ExecOperations execOps, File workspaceYaml, String version,
                                      String pnpmCommand = null) {
        Closure stamp = createSetVersion(version)
        for (File packageDir in workspacePackageDirs(execOps, workspaceYaml.parentFile, pnpmCommand)) {
            File file = new File(packageDir, 'package.json')
            if (file.exists()) {
                Object packageJSON = new JsonSlurper().parse(file)
                stamp.call(packageJSON)
                writeFile(file, prettyPrintJSON(packageJSON))
            }
        }
    }

    /**
     * The directory of every package in the pnpm workspace rooted at {@code rootDir},
     * root included, as reported by {@code pnpm list -r --depth -1}.
     *
     * <p>Does not require {@code pnpm install} to have run: the command reads
     * {@code pnpm-workspace.yaml} and the workspace manifests, not {@code node_modules}.
     * Unlike a glob walk this yields only real packages, so non-package directories
     * (docker, patches, scripts) never enter the list.
     *
     * <p>{@code pnpmCommand} is the executable to run. Repos that let {@code node-gradle}
     * provision a pinned pnpm must pass its path, because that binary is not on {@code PATH}.
     * Passing {@code null} falls back to {@code pnpm} from {@code PATH}.
     *
     * <p>Failure handling depends on whether the caller chose the executable. With an explicit
     * {@code pnpmCommand}, a pnpm that cannot enumerate the workspace throws: the caller stated
     * where pnpm is, so a broken invocation is a build error, not something to paper over.
     * Without one, the workspace may legitimately not be enumerable — no pnpm on {@code PATH},
     * or a {@code pnpm-workspace.yaml} whose catalogs are mid-rewrite, since callers run before
     * {@code pnpm install} validates them — and the result degrades to {@code [rootDir]}, which
     * is the pre-existing behaviour for a single-package repo.
     */
    static List<File> workspacePackageDirs(ExecOperations execOps, File rootDir, String pnpmCommand = null) {
        boolean configured = pnpmCommand != null && !pnpmCommand.trim().isEmpty()
        String executable = configured ? pnpmCommand.trim() : 'pnpm'

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        try {
            execOps.exec {
                commandLine getUnifiedCommandLine([executable, 'list', '-r', '--depth', '-1', '--json'])
                workingDir = rootDir
                standardOutput = stdout
                errorOutput = stderr
            }
        } catch (Exception e) {
            if (configured) {
                throw new IllegalStateException(
                    "Failed to enumerate the pnpm workspace in ${rootDir} using '${executable}'. " +
                        "Without the package list the integration version would be stamped into the root " +
                        "package.json only, leaving every workspace package at its -SNAPSHOT version. " +
                        "pnpm stderr: ${stderr.toString('UTF-8').trim()}", e)
            }
            return [rootDir]
        }

        String json = stdout.toString('UTF-8').trim()
        if (json.isEmpty()) {
            if (configured) {
                throw new IllegalStateException(
                    "'${executable} list' reported no packages for the pnpm workspace in ${rootDir}, " +
                        "so only the root package.json would be stamped. " +
                        "pnpm stderr: ${stderr.toString('UTF-8').trim()}")
            }
            return [rootDir]
        }

        return new JsonSlurper().parseText(json).collect { new File(it.path as String) }
    }
}
