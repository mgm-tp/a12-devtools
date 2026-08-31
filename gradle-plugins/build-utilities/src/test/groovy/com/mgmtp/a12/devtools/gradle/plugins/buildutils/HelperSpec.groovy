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

import spock.lang.Specification

class HelperSpec extends Specification {

    def 'computeBuildVersion stamps the integration variant with a UTC timestamp'() {
        when:
        String version = Helper.computeBuildVersion('3.0.1-SNAPSHOT', 'integration')

        then:
        version ==~ /3\.0\.1-build\.\d{14}\.integration/
    }

    def 'computeBuildVersion leaves a released version untouched'() {
        expect:
        Helper.computeBuildVersion('3.0.1', 'integration') == '3.0.1'
    }

    def 'computeBuildVersion ignores an unrecognized use'() {
        expect:
        Helper.computeBuildVersion('3.0.1-SNAPSHOT', 'something-else') == '3.0.1-SNAPSHOT'
    }
}
