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

import groovy.yaml.YamlSlurper
import spock.lang.Specification
import spock.lang.TempDir

class PnpmCatalogRewriterSpec extends Specification {

    @TempDir
    File tmp

    private File workspace(String content) {
        File f = new File(tmp, 'pnpm-workspace.yaml')
        f.text = content
        return f
    }

    /** Re-parses the rewritten file: formatting is intentionally not preserved, so we assert on data. */
    private Object parsed(File f) {
        return new YamlSlurper().parse(f)
    }

    def "rewrites matching entries in the default catalog and both named catalogs, leaves others untouched"() {
        given:
        File f = workspace('''\
packages:
  - "core"
catalog:
  "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
  "@types/node": 24.10.4
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
    "@com.mgmtp.a12.base/base-model-api": 30.0.1
  a12ranges:
    "@com.mgmtp.a12.widgets/widgets-core": ^39.0.0
    "@com.mgmtp.a12.base/base-model-api": ^30.0.0
'''.stripIndent())

        when:
        int n = PnpmCatalogRewriter.rewrite(f, ['widgets': '39.1.1-build.20260405'])

        then: 'the matching coordinate is bumped in the default catalog and both named catalogs'
        n == 3

        and:
        def ws = parsed(f)
        ws.catalog.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
        ws.catalogs.a12ranges.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'

        and: 'unmatched entries are left as they were (non-A12 coordinate, and a component not passed)'
        ws.catalog.'@types/node' == '24.10.4'
        ws.catalogs.a12.'@com.mgmtp.a12.base/base-model-api' == '30.0.1'
        ws.catalogs.a12ranges.'@com.mgmtp.a12.base/base-model-api' == '^30.0.0'
    }

    def "leaves a named catalog untouched when it is not in the catalogs list"() {
        given:
        File f = workspace('''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
  myCatalog:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent())

        when: 'myCatalog is not passed, so only the default catalogs are rewritten'
        int n = PnpmCatalogRewriter.rewrite(f, ['widgets': '39.1.1-build.20260405'])

        then:
        n == 1

        and:
        def ws = parsed(f)
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
        ws.catalogs.myCatalog.'@com.mgmtp.a12.widgets/widgets-core' == '39.0.1'
    }

    def "rewrites an extra named catalog when it is included in the catalogs list"() {
        given:
        File f = workspace('''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
  myCatalog:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent())

        when: 'myCatalog is passed alongside the built-in defaults'
        int n = PnpmCatalogRewriter.rewrite(f, ['widgets': '39.1.1-build.20260405'],
            PnpmCatalogRewriter.DEFAULT_CATALOGS + ['myCatalog'])

        then: 'the built-in a12 catalog and the extra myCatalog are both rewritten'
        n == 2

        and:
        def ws = parsed(f)
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
        ws.catalogs.myCatalog.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
    }

    def "does not touch coordinates in arbitrary top-level keys other than catalogs and overrides"() {
        given:
        File f = workspace('''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
peerDependencyRules:
  "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent())

        when:
        int n = PnpmCatalogRewriter.rewrite(f, ['widgets': '40.0.0'])

        then:
        n == 1

        and:
        def ws = parsed(f)
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '40.0.0'
        ws.peerDependencyRules.'@com.mgmtp.a12.widgets/widgets-core' == '39.0.1'
    }

    def "returns zero and leaves the file unchanged when nothing matches"() {
        given:
        String original = 'catalogs:\n  a12:\n    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1\n'
        File f = workspace(original)

        when:
        int n = PnpmCatalogRewriter.rewrite(f, ['kernel': '31.2.0'])

        then:
        n == 0
        f.text == original
    }

    def "rewrites matching entries in the overrides section"() {
        given:
        File f = workspace('''\
catalogs:
  a12:
    "@com.mgmtp.a12.dataservices/dataservices-access": 39.0.1
    "@com.mgmtp.a12.utils/utils-collections": 7.0.1
  a12ranges:
    "@com.mgmtp.a12.dataservices/dataservices-access": ^39.0.1
overrides:
  "@com.mgmtp.a12.dataservices/dataservices-access": 39.0.1
  "@com.mgmtp.a12.utils/utils-collections": 7.0.1
  "@types/node": 24.10.4
'''.stripIndent())

        when:
        int n = PnpmCatalogRewriter.rewrite(f, ['data-services': '39.1.0-build.20260710'])

        then: 'catalog + a12ranges + overrides entry all rewritten'
        n == 3

        and:
        def ws = parsed(f)
        ws.catalogs.a12.'@com.mgmtp.a12.dataservices/dataservices-access' == '39.1.0-build.20260710'
        ws.catalogs.a12ranges.'@com.mgmtp.a12.dataservices/dataservices-access' == '39.1.0-build.20260710'
        ws.overrides.'@com.mgmtp.a12.dataservices/dataservices-access' == '39.1.0-build.20260710'

        and: 'non-matching overrides are untouched'
        ws.overrides.'@com.mgmtp.a12.utils/utils-collections' == '7.0.1'
        ws.overrides.'@types/node' == '24.10.4'
    }

    def "returns zero when the workspace file does not exist"() {
        given:
        File f = new File(tmp, 'pnpm-workspace.yaml')

        expect:
        PnpmCatalogRewriter.rewrite(f, ['widgets': '39.1.1-build.20260405']) == 0
        !f.exists()
    }

    def "rewrites an entry even when a full-line comment sits inside the catalogs block"() {
        given: 'a stray column-0 comment that a line-based parser would mistake for the end of the block'
        File f = workspace('''\
catalogs:
  a12:
# temporary pin, remove after the next release
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent())

        when:
        int n = PnpmCatalogRewriter.rewrite(f, ['widgets': '39.1.1-build.20260405'])

        then:
        n == 1
        parsed(f).catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260405'
    }
}
