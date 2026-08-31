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

/**
 * Explicit map from a BD integration component name to the concrete dependency
 * coordinates that carry its version in our repos.
 *
 * Each component lists:
 *  - {@code npm}: the exact npm coordinates (pnpm catalog keys) whose version tracks
 *    this component. Membership is exact - there is no scope or prefix matching.
 *  - {@code toml}: the exact {@code [versions]} keys in {@code gradle/libs.versions.toml}
 *    whose version tracks this component (usually empty; a component may have several).
 *
 * This is deliberately a flat, explicit map rather than a derivation rule (e.g. npm
 * scope matching), so that a package can be added to, or left out of, a component's
 * version bump without needing an exclusion list.
 */
class A12ComponentCatalog {

    static final Map<String, Map<String, List<String>>> COMPONENTS = [
        'base'            : [
            npm : ['@com.mgmtp.a12.base/base-model-api'],
            toml: [],
        ],
        'widgets'         : [
            npm : ['@com.mgmtp.a12.widgets/widgets-core'],
            toml: [],
        ],
        'client'          : [
            npm : [
                '@com.mgmtp.a12.client/client-core',
                '@com.mgmtp.a12.client/client-data',
                '@com.mgmtp.a12.client/client-application-model-migration',
            ],
            toml: [],
        ],
        'kernel'          : [
            npm : [
                '@com.mgmtp.a12.kernel/kernel-core-runtime-api-ts',
                '@com.mgmtp.a12.kernel/kernel-md-facade',
            ],
            toml: [],
        ],
        'expression'      : [
            npm : ['@com.mgmtp.a12.expression/expression-core'],
            toml: [],
        ],
        'data-services'   : [
            npm : [
                '@com.mgmtp.a12.dataservices/dataservices-access',
                '@com.mgmtp.a12.dataservices/dataservices-relationship-model-migration',
            ],
            toml: ['dataservices'],
        ],
        'migration-tool'  : [
            npm : [
                '@com.mgmtp.a12.migrationtool/migrationtool-core',
                '@com.mgmtp.a12.migrationtool/migrationtool-utils',
            ],
            toml: [],
        ],
        'content-engine'  : [
            npm : [
                '@com.mgmtp.a12.contentengine/contentengine-core',
                '@com.mgmtp.a12.contentengine/contentengine-default-element-library',
                '@com.mgmtp.a12.contentengine/contentengine-editor',
                '@com.mgmtp.a12.contentengine/contentengine-model-migration',
            ],
            toml: [],
        ],
        'overview-engine' : [
            npm : [
                '@com.mgmtp.a12.overviewengine/overviewengine-core',
                '@com.mgmtp.a12.overviewengine/overviewengine-model-migration',
                '@com.mgmtp.a12.querymodel/querymodel-core',
            ],
            toml: [],
        ],
        'form-engine'     : [
            npm : [
                '@com.mgmtp.a12.formengine/formengine-core',
                '@com.mgmtp.a12.formengine/formengine-model-migration',
            ],
            toml: [],
        ],
        'relationship-engine' : [
            npm : [
                '@com.mgmtp.a12.relationshipengine/relationshipengine-core',
                '@com.mgmtp.a12.relationshipengine/relationshipengine-model-migration',
                '@com.mgmtp.a12.crud/crud-core',
            ],
            toml: [],
        ],
        'tree-engine'     : [
            npm : [
                '@com.mgmtp.a12.treeengine/treeengine-core',
                '@com.mgmtp.a12.treeengine/treeengine-model-migration',
            ],
            toml: [],
        ],
        'localization'    : [
            npm : [
                '@com.mgmtp.a12.utils/utils-localization',
                '@com.mgmtp.a12.utils/utils-localization-react',
            ],
            toml: [],
        ],
        'logging'         : [
            npm : ['@com.mgmtp.a12.utils/utils-logging'],
            toml: [],
        ],
        'collections'     : [
            npm : ['@com.mgmtp.a12.utils/utils-collections'],
            toml: [],
        ],
        'utils-connector' : [
            npm : ['@com.mgmtp.a12.utils/utils-connector'],
            toml: [],
        ],
        'codemod'         : [
            npm : ['@com.mgmtp.a12.devtools/codemod'],
            toml: [],
        ],
        'tsconfig'        : [
            npm : ['@com.mgmtp.a12.devtools/tsconfig'],
            toml: [],
        ],
        'eslint-config'   : [
            npm : ['@com.mgmtp.a12.devtools/eslint-config'],
            toml: [],
        ],
        'prettier-config' : [
            npm : ['@com.mgmtp.a12.devtools/prettier-config'],
            toml: [],
        ],
        'react'           : [
            npm : ['@com.mgmtp.a12.devtools/react'],
            toml: [],
        ],
        'prepare-models'  : [
            npm : [],
            toml: ['prepare-models'],
        ],
        'build-tools'     : [
            npm : ['@com.mgmtp.a12.devtools/build-tools'],
            toml: [],
        ],
        'why-did-you-render' : [
            npm : ['@com.mgmtp.a12.devtools/why-did-you-render'],
            toml: [],
        ],
        'build-utilities' : [
            npm : [],
            toml: ['build-utilities'],
        ],
        'prepare-models-validation-converter' : [
            npm : [],
            toml: ['prepare-models-validation-converter'],
        ],
        'runtime-model-conversion' : [
            npm : [],
            toml: ['runtime-model-conversion'],
        ],
        'wcf'             : [
            npm : [],
            toml: ['wcf'],
        ],
        'validate-data-models' : [
            npm : [],
            toml: ['validate-data-models'],
        ],
    ].asImmutable()

    static boolean npmCoordinateMatches(String component, String coordinate) {
        return npmCoordinateMatches(component, coordinate, [:])
    }

    static boolean npmCoordinateMatches(String component, String coordinate,
                                        Map<String, Map<String, List<String>>> additional) {
        Map<String, List<String>> entry = additional[component] ?: COMPONENTS[component]
        return entry != null && entry.npm.contains(coordinate)
    }

    static List<String> gradleTomlKeys(String component) {
        return gradleTomlKeys(component, [:])
    }

    static List<String> gradleTomlKeys(String component,
                                       Map<String, Map<String, List<String>>> additional) {
        Map<String, List<String>> entry = additional[component] ?: COMPONENTS[component]
        return entry != null ? entry.toml : []
    }
}
