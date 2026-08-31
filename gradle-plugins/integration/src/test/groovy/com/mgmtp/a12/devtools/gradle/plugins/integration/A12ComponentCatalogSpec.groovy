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

class A12ComponentCatalogSpec extends Specification {

    def "matches a component's exact npm coordinates"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('widgets', '@com.mgmtp.a12.widgets/widgets-core')
        A12ComponentCatalog.npmCoordinateMatches('base', '@com.mgmtp.a12.base/base-model-api')
        A12ComponentCatalog.npmCoordinateMatches('kernel', '@com.mgmtp.a12.kernel/kernel-md-facade')

        and: 'does not match a coordinate belonging to a different component'
        !A12ComponentCatalog.npmCoordinateMatches('widgets', '@com.mgmtp.a12.base/base-model-api')
    }

    def "client matches core, data and the application-model-migration"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('client', '@com.mgmtp.a12.client/client-core')
        A12ComponentCatalog.npmCoordinateMatches('client', '@com.mgmtp.a12.client/client-data')
        A12ComponentCatalog.npmCoordinateMatches('client', '@com.mgmtp.a12.client/client-application-model-migration')
    }

    def "overview-engine matches its core, model-migration and the querymodel coordinate"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('overview-engine', '@com.mgmtp.a12.overviewengine/overviewengine-core')
        A12ComponentCatalog.npmCoordinateMatches('overview-engine', '@com.mgmtp.a12.overviewengine/overviewengine-model-migration')

        and: 'querymodel ships with the overview-engine release despite its own scope'
        A12ComponentCatalog.npmCoordinateMatches('overview-engine', '@com.mgmtp.a12.querymodel/querymodel-core')
    }

    def "form-engine matches its core and model-migration coordinates"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('form-engine', '@com.mgmtp.a12.formengine/formengine-core')
        A12ComponentCatalog.npmCoordinateMatches('form-engine', '@com.mgmtp.a12.formengine/formengine-model-migration')

        and: 'the form-engine codemod is a dev-only package, not part of the release bump'
        !A12ComponentCatalog.npmCoordinateMatches('form-engine', '@com.mgmtp.a12.formengine/formengine-codemod')
    }

    def "relationship-engine matches core, model-migration and the crud coordinate"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('relationship-engine', '@com.mgmtp.a12.relationshipengine/relationshipengine-core')
        A12ComponentCatalog.npmCoordinateMatches('relationship-engine', '@com.mgmtp.a12.relationshipengine/relationshipengine-model-migration')

        and: 'crud ships with the relationship-engine release despite its own scope'
        A12ComponentCatalog.npmCoordinateMatches('relationship-engine', '@com.mgmtp.a12.crud/crud-core')
    }

    def "typescript-fsa-redux-5-compat is simply absent from the client component, so it never matches"() {
        expect:
        !A12ComponentCatalog.npmCoordinateMatches('client', '@com.mgmtp.a12.client/typescript-fsa-redux-5-compat')
    }

    def "does not match a coordinate under a different component's scope even for a same-scope lookup"() {
        expect:
        !A12ComponentCatalog.npmCoordinateMatches('base', '@com.mgmtp.a12.widgets/widgets-core')
    }

    def "maps the utils-family components to their explicit coordinates"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('localization', '@com.mgmtp.a12.utils/utils-localization')
        A12ComponentCatalog.npmCoordinateMatches('localization', '@com.mgmtp.a12.utils/utils-localization-react')
        A12ComponentCatalog.npmCoordinateMatches('logging', '@com.mgmtp.a12.utils/utils-logging')
        A12ComponentCatalog.npmCoordinateMatches('collections', '@com.mgmtp.a12.utils/utils-collections')
        A12ComponentCatalog.npmCoordinateMatches('utils-connector', '@com.mgmtp.a12.utils/utils-connector')

        and: 'localization does not accidentally match logging'
        !A12ComponentCatalog.npmCoordinateMatches('localization', '@com.mgmtp.a12.utils/utils-logging')
    }

    def "matches the devtools components individually, since devtools itself is not a BD component"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('codemod', '@com.mgmtp.a12.devtools/codemod')
        A12ComponentCatalog.npmCoordinateMatches('tsconfig', '@com.mgmtp.a12.devtools/tsconfig')
        A12ComponentCatalog.npmCoordinateMatches('eslint-config', '@com.mgmtp.a12.devtools/eslint-config')
        A12ComponentCatalog.npmCoordinateMatches('prettier-config', '@com.mgmtp.a12.devtools/prettier-config')
        A12ComponentCatalog.npmCoordinateMatches('react', '@com.mgmtp.a12.devtools/react')

        and: 'there is no bare "devtools" component'
        !A12ComponentCatalog.npmCoordinateMatches('devtools', '@com.mgmtp.a12.devtools/codemod')
    }

    def "matches multi-coordinate components fully"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('kernel', '@com.mgmtp.a12.kernel/kernel-core-runtime-api-ts')
        A12ComponentCatalog.npmCoordinateMatches('kernel', '@com.mgmtp.a12.kernel/kernel-md-facade')
        A12ComponentCatalog.npmCoordinateMatches('migration-tool', '@com.mgmtp.a12.migrationtool/migrationtool-core')
        A12ComponentCatalog.npmCoordinateMatches('migration-tool', '@com.mgmtp.a12.migrationtool/migrationtool-utils')
        A12ComponentCatalog.npmCoordinateMatches('content-engine', '@com.mgmtp.a12.contentengine/contentengine-core')
        A12ComponentCatalog.npmCoordinateMatches('content-engine', '@com.mgmtp.a12.contentengine/contentengine-default-element-library')
        A12ComponentCatalog.npmCoordinateMatches('content-engine', '@com.mgmtp.a12.contentengine/contentengine-editor')
        A12ComponentCatalog.npmCoordinateMatches('content-engine', '@com.mgmtp.a12.contentengine/contentengine-model-migration')
        A12ComponentCatalog.npmCoordinateMatches('expression', '@com.mgmtp.a12.expression/expression-core')
        A12ComponentCatalog.npmCoordinateMatches('data-services', '@com.mgmtp.a12.dataservices/dataservices-access')
        A12ComponentCatalog.npmCoordinateMatches('data-services', '@com.mgmtp.a12.dataservices/dataservices-relationship-model-migration')
    }

    def "tree-engine matches its core and model-migration coordinates"() {
        expect:
        A12ComponentCatalog.npmCoordinateMatches('tree-engine', '@com.mgmtp.a12.treeengine/treeengine-core')
        A12ComponentCatalog.npmCoordinateMatches('tree-engine', '@com.mgmtp.a12.treeengine/treeengine-model-migration')
    }

    def "returns false for an unknown component regardless of coordinate"() {
        expect:
        !A12ComponentCatalog.npmCoordinateMatches('unknown-x', 'whatever')
    }

    def "exposes the gradle toml keys for components that have them"() {
        expect:
        A12ComponentCatalog.gradleTomlKeys('data-services') == ['dataservices']
        A12ComponentCatalog.gradleTomlKeys('prepare-models') == ['prepare-models']

        and: 'components without a gradle toml key return an empty list'
        A12ComponentCatalog.gradleTomlKeys('widgets') == []
        A12ComponentCatalog.gradleTomlKeys('unknown-x') == []
    }

    def "prepare-models has no npm coordinates, only a gradle toml key"() {
        expect:
        A12ComponentCatalog.gradleTomlKeys('prepare-models') == ['prepare-models']
        !A12ComponentCatalog.npmCoordinateMatches('prepare-models', 'anything')
    }

    def "additional components are recognized by npmCoordinateMatches"() {
        given:
        def extra = ['my-custom': [npm: ['@com.mgmtp.a12.custom/custom-lib'], toml: ['my-custom']]]

        expect:
        A12ComponentCatalog.npmCoordinateMatches('my-custom', '@com.mgmtp.a12.custom/custom-lib', extra)

        and: 'built-in components still work'
        A12ComponentCatalog.npmCoordinateMatches('widgets', '@com.mgmtp.a12.widgets/widgets-core', extra)

        and: 'additional component does not match other coordinates'
        !A12ComponentCatalog.npmCoordinateMatches('my-custom', '@com.mgmtp.a12.widgets/widgets-core', extra)
    }

    def "additional components expose gradle toml keys"() {
        given:
        def extra = ['my-custom': [npm: ['@com.mgmtp.a12.custom/custom-lib'], toml: ['my-custom']]]

        expect:
        A12ComponentCatalog.gradleTomlKeys('my-custom', extra) == ['my-custom']

        and: 'built-in components still work'
        A12ComponentCatalog.gradleTomlKeys('data-services', extra) == ['dataservices']
    }

    def "additional components override built-in entries with the same name"() {
        given:
        def extra = ['widgets': [npm: ['@com.mgmtp.a12.widgets/widgets-extended'], toml: ['widgets']]]

        expect: 'the override replaces the built-in npm coordinates'
        A12ComponentCatalog.npmCoordinateMatches('widgets', '@com.mgmtp.a12.widgets/widgets-extended', extra)
        !A12ComponentCatalog.npmCoordinateMatches('widgets', '@com.mgmtp.a12.widgets/widgets-core', extra)

        and: 'the override adds a toml key not present in the built-in'
        A12ComponentCatalog.gradleTomlKeys('widgets', extra) == ['widgets']
    }

    def "matches the additional devtools components: build-tools, why-did-you-render, build-utilities, prepare-models-validation-converter"() {
        expect: 'npm-only components'
        A12ComponentCatalog.npmCoordinateMatches('build-tools', '@com.mgmtp.a12.devtools/build-tools')
        A12ComponentCatalog.npmCoordinateMatches('why-did-you-render', '@com.mgmtp.a12.devtools/why-did-you-render')

        and: 'toml-only components'
        A12ComponentCatalog.gradleTomlKeys('build-utilities') == ['build-utilities']
        A12ComponentCatalog.gradleTomlKeys('prepare-models-validation-converter') == ['prepare-models-validation-converter']

        and: 'they do not have coordinates they should not'
        !A12ComponentCatalog.npmCoordinateMatches('build-utilities', 'anything')
        !A12ComponentCatalog.npmCoordinateMatches('prepare-models-validation-converter', 'anything')
        A12ComponentCatalog.gradleTomlKeys('build-tools') == []
        A12ComponentCatalog.gradleTomlKeys('why-did-you-render') == []
    }

    def "matches the gradle-only java components: runtime-model-conversion and wcf"() {
        expect: 'both are toml-only, keyed by their release-plan component names'
        A12ComponentCatalog.gradleTomlKeys('runtime-model-conversion') == ['runtime-model-conversion']
        A12ComponentCatalog.gradleTomlKeys('wcf') == ['wcf']

        and: 'neither publishes npm packages'
        !A12ComponentCatalog.npmCoordinateMatches('runtime-model-conversion', 'anything')
        !A12ComponentCatalog.npmCoordinateMatches('wcf', 'anything')

        and: 'the old abbreviated rmc name is not a component'
        A12ComponentCatalog.gradleTomlKeys('rmc-conversion') == []
    }

    def "matches validate-data-models as a toml-only gradle java component"() {
        expect:
        A12ComponentCatalog.gradleTomlKeys('validate-data-models') == ['validate-data-models']

        and: 'it does not publish npm packages'
        !A12ComponentCatalog.npmCoordinateMatches('validate-data-models', 'anything')
    }
}
