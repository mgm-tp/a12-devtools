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

class Helper {
    static String getVersion(File file, String specifiedVersion, String use) {
        if (specifiedVersion != "unspecified") {
            return specifiedVersion;
        }
        String version = new JsonSlurper().parse(file).version
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
}
