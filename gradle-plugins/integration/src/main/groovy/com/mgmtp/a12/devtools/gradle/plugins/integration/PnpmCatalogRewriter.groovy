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

import groovy.yaml.YamlBuilder
import groovy.yaml.YamlSlurper

/**
 * Rewrites A12 dependency versions inside a {@code pnpm-workspace.yaml}. Entries whose
 * coordinate maps to a passed component are bumped in the pnpm default catalog (the
 * top-level {@code catalog:} map) and in the named catalogs (default {@code a12} and
 * {@code a12ranges}); everything else is left as its parsed value. Additionally, matching
 * coordinates in the top-level {@code overrides} map are rewritten to prevent version
 * conflicts from stale pins.
 *
 * Expected shape:
 * <pre>
 * catalog:
 *   "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
 * catalogs:
 *   a12:
 *     "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
 *   a12ranges:
 *     "@com.mgmtp.a12.widgets/widgets-core": ^39.0.0
 * overrides:
 *   "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
 * </pre>
 *
 * A missing workspace file is a no-op (returns 0)
 *
 * The file is parsed with {@link YamlSlurper} and written back with {@link YamlBuilder},
 * so comments and formatting are <em>not</em> preserved. That is acceptable here: the
 * integration pipeline rewrites this file only to build and publish, and never commits
 * the result. Using a real YAML parser (rather than a line-based one) keeps the rewrite
 * robust against arbitrary but valid formatting, comments, and quoting.
 */
class PnpmCatalogRewriter {

    static final List<String> DEFAULT_CATALOGS = ['a12', 'a12ranges'].asImmutable()

    static int rewrite(File workspaceFile, Map<String, String> componentVersions,
                       Collection<String> catalogs = DEFAULT_CATALOGS,
                       Map<String, Map<String, List<String>>> additionalComponents = [:]) {
        if (componentVersions.isEmpty() || !workspaceFile.exists()) {
            return 0
        }
        def root = new YamlSlurper().parse(workspaceFile)
        if (!(root instanceof Map)) {
            return 0
        }

        int rewritten = 0

        // The pnpm default (unnamed) catalog
        rewritten += rewriteEntries(root['catalog'], componentVersions, additionalComponents)

        // Named catalogs under `catalogs:` (default a12 and a12ranges).
        def catalogsNode = root['catalogs']
        if (catalogsNode instanceof Map) {
            catalogs.each { String catalogName ->
                rewritten += rewriteEntries(catalogsNode[catalogName], componentVersions, additionalComponents)
            }
        }

        // Also rewrite matching coordinates in the top-level overrides map.
        rewritten += rewriteEntries(root['overrides'], componentVersions, additionalComponents)

        if (rewritten > 0) {
            def builder = new YamlBuilder()
            builder.call(root)
            workspaceFile.text = builder.toString()
        }
        return rewritten
    }

    /**
     * Reassigns the version of every coordinate in {@code node} that maps to one of the passed components, and returns
     * how many were changed. No-op returning 0 when {@code node} is not a Map. Only values of existing keys
     * are reassigned - entries are never added or removed.
     */
    private static int rewriteEntries(Object node, Map<String, String> componentVersions,
                                      Map<String, Map<String, List<String>>> additionalComponents) {
        if (!(node instanceof Map)) {
            return 0
        }
        int rewritten = 0
        // Snapshot the keys: we only reassign values of existing keys, never add/remove.
        node.keySet().toList().each { coordinate ->
            String component = componentVersions.keySet().find {
                A12ComponentCatalog.npmCoordinateMatches(it, coordinate.toString(), additionalComponents)
            }
            if (component != null) {
                node[coordinate] = componentVersions[component]
                rewritten++
            }
        }
        return rewritten
    }
}
