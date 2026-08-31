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

import spock.lang.Specification
import spock.lang.TempDir

class GradleDependenciesRewriterSpec extends Specification {

    @TempDir
    File tmp

    private File toml(String content) {
        File f = new File(tmp, 'libs.versions.toml')
        f.text = content
        return f
    }

    def "rewrites a version entry under [versions]"() {
        given:
        File f = toml('''\
[versions]
dataservices = "39.0.1"
spotless = "6.25.0"

[libraries]
dataservices-bom = { module = "com.mgmtp.a12.dataservices:dataservices-parent", version.ref = "dataservices" }
'''.stripIndent())

        when:
        boolean changed = GradleDependenciesRewriter.rewrite(f, 'dataservices', '39.2.0-build.20260405')

        then:
        changed
        f.text.contains('dataservices = "39.2.0-build.20260405"')
        f.text.contains('spotless = "6.25.0"')
        f.text.contains('version.ref = "dataservices"')
    }

    def "rewrites a version entry for any other A12 component key, e.g. prepare-models"() {
        given:
        File f = toml('''\
[versions]
prepare-models = "1.0.0"
spotless = "6.25.0"
'''.stripIndent())

        when:
        boolean changed = GradleDependenciesRewriter.rewrite(f, 'prepare-models', '1.2.0-build.20260710')

        then:
        changed
        f.text.contains('prepare-models = "1.2.0-build.20260710"')
        f.text.contains('spotless = "6.25.0"')
    }

    def "returns false when the key is absent"() {
        given:
        File f = toml('[versions]\nspotless = "6.25.0"\n')

        when:
        boolean changed = GradleDependenciesRewriter.rewrite(f, 'dataservices', '40.0.0')

        then:
        !changed
        f.text == '[versions]\nspotless = "6.25.0"\n'
    }

    def "returns false when the file does not exist"() {
        expect:
        !GradleDependenciesRewriter.rewrite(new File(tmp, 'missing.toml'), 'dataservices', '40.0.0')
    }
}
