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

import java.util.regex.Matcher

/**
 * Rewrites a single version entry in a {@code gradle/libs.versions.toml}, e.g.
 * {@code dataservices = "39.0.1"}. Format-preserving, line-based. Only the entry with
 * the given key inside the {@code [versions]} table is touched.
 *
 * Generic by design: it rewrites the version-key entry for any A12 component listed
 * in the toml file's {@code [versions]} table.
 */
class GradleDependenciesRewriter {

    static boolean rewrite(File tomlFile, String versionKey, String newVersion) {
        if (tomlFile == null || !tomlFile.exists()) {
            return false
        }
        List<String> lines = new ArrayList<>(tomlFile.readLines())
        boolean trailingNewline = tomlFile.text.endsWith('\n')
        boolean inVersions = false
        boolean changed = false

        for (int i = 0; i < lines.size(); i++) {
            String line = lines[i]
            Matcher section = (line =~ /^\s*\[([^\]]+)\]\s*$/)
            if (section.find()) {
                inVersions = section.group(1).trim() == 'versions'
                continue
            }
            if (!inVersions) {
                continue
            }
            Matcher entry = (line =~ /^(\s*)(\Q${versionKey}\E)(\s*=\s*)(["'])([^"']*)(["'])(.*)$/)
            if (entry.find()) {
                lines[i] = entry.group(1) + entry.group(2) + entry.group(3) +
                    entry.group(4) + newVersion + entry.group(6) + entry.group(7)
                changed = true
                break
            }
        }

        if (changed) {
            tomlFile.text = lines.join('\n') + (trailingNewline ? '\n' : '')
        }
        return changed
    }
}
