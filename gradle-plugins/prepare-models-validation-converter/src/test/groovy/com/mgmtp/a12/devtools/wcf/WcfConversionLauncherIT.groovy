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
package com.mgmtp.a12.devtools.wcf

import com.mgmtp.a12.dataservices.wcf.WorkspaceConversionService
import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter

import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

import spock.lang.Specification

/**
 * Boots the real Spring context the launcher uses and verifies the full converter pipeline is
 * discovered and ordered. This is the key library-mode risk: the context must boot cleanly with the
 * real classpath (kernel pulls Groovy; the CLI's split-classloader GroovyBeanDefinitionReader failure
 * must NOT recur on a flat classpath), and every @WcfConverter bean must be found.
 *
 * Requires the real wcf-core / rmc:conversion / kernel-md-facade artifacts to be resolvable.
 */
class WcfConversionLauncherIT extends Specification {

    def "boots the context and discovers RMC's pipeline plus our validation converter"() {
        when:
        def ctx = new SpringApplicationBuilder(WcfConversionLauncher)
                .web(WebApplicationType.NONE)
                .run()

        then:
        ctx.getBean(WorkspaceConversionService) != null

        and: "all expected @WcfConverter beans are present"
        def names = ctx.getBeanProvider(WorkspaceConverter).stream()
                .map { it.getClass().simpleName }
                .toList()
        names.containsAll([
                'ExclusionsConverter',
                'KernelModelsConverter',
                'MetadataConverter',
                'ValidationCodeConverter',
                'WorkspaceStructureConverter',
        ])

        cleanup:
        ctx?.close()
    }

    def "validation converter is ordered after KernelModelsConverter and before WorkspaceStructureConverter"() {
        when:
        def ctx = new SpringApplicationBuilder(WcfConversionLauncher)
                .web(WebApplicationType.NONE)
                .run()
        def ordered = ctx.getBeanProvider(WorkspaceConverter).orderedStream()
                .map { it.getClass().simpleName }
                .toList()
        def validationIdx = ordered.indexOf('ValidationCodeConverter')
        def kernelIdx = ordered.indexOf('KernelModelsConverter')
        def structureIdx = ordered.indexOf('WorkspaceStructureConverter')

        then: "all three are present (guard against indexOf -1 passing the comparisons vacuously)"
        validationIdx >= 0 && kernelIdx >= 0 && structureIdx >= 0

        and: "order values: KernelModels=50 < ValidationCode=55 < WorkspaceStructure=999"
        validationIdx > kernelIdx
        validationIdx < structureIdx

        cleanup:
        ctx?.close()
    }
}
