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
package com.mgmtp.a12.devtools.wcf;

import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConversionService;

/**
 * Minimal programmatic entry point for the WCF conversion pipeline, used by the prepare-models Gradle
 * plugin in place of the dataservices-wcf CLI.
 *
 * <p>Deliberately tiny and shaped as a single {@code process(in, out)} call: if the WCF team later
 * exposes a reusable entry point (for example {@code WcfRunner.run(in, out)}), this class is deleted and
 * the plugin points {@code mainClass} at theirs.
 *
 * <p>Converters are discovered exactly as the CLI does — Spring component-scan over {@code com.mgmtp.a12}
 * picks up every {@code @WcfConverter} (RMC's pipeline plus our {@code ValidationCodeConverter}) and orders
 * them by {@code @WcfConverter(order = ...)}. Unlike the CLI, the converters are already on the launch
 * classpath (resolved by Gradle), so there is no runtime classloader augmentation and no {@code -c} jar.
 *
 * <p>Arguments: {@code args[0]} = input workspace directory, {@code args[1]} = output seed-data directory.
 */
@SpringBootApplication(scanBasePackages = "com.mgmtp.a12")
public class WcfConversionLauncher {

    public static void main(String[] args) {
        if (args.length < 2) {
            // Wiring invariant: the prepare-models Gradle task always passes both paths. A failure here
            // means the plugin's javaexec was misconfigured, hence IllegalStateException (not -Argument-).
            throw new IllegalStateException(
                    "Usage: WcfConversionLauncher <inputWorkspaceDir> <outputSeedDataDir>");
        }
        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        try (ConfigurableApplicationContext ctx = SpringApplication.run(WcfConversionLauncher.class)) {
            ctx.getBean(WorkspaceConversionService.class).process(inputDir, outputDir);
        }
        System.out.println("Conversion finished. Output: " + outputDir);
    }
}
