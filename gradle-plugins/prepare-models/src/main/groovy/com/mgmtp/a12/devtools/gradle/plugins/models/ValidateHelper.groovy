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
 * 1. Open-Source License – EUPL v1.2
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
 * THIS SOFTWARE IS PROVIDED “AS IS” AND WITHOUT WARRANTY OF ANY KIND,
 * WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT, EXCEPT WHERE SUCH DISCLAIMERS ARE HELD TO BE
 * LEGALLY INVALID. SEE THE RESPECTIVE LICENSE TEXT FOR DETAILS.
 */
package com.mgmtp.a12.devtools.gradle.plugins.models

import org.gradle.api.file.FileCollection

class ValidateHelper {
    static final String getFileName(final File file) {
        return file.name.take(file.name.lastIndexOf('.'))
    }

    static final void validateAndPreparePaths(File inputDir, File outputDir) {
        if (!inputDir.exists()) {
            throw new IllegalStateException(
                "Input directory does not exist: ${inputDir.absolutePath}. " +
                "Please ensure the directory exists before running this task."
            )
        }
        if (!inputDir.isDirectory()) {
            throw new IllegalStateException(
                "Input path is not a directory: ${inputDir.absolutePath}"
            )
        }

        // Create output directory if it doesn't exist
        outputDir.mkdirs()
    }

    static final void validateKernelLibrary(final String requiredClass, final String libraryName, final FileCollection classpath) {
        final def urls = classpath.files.collect { it.toURI().toURL() } as URL[]
        final def classLoader = new URLClassLoader(urls, (ClassLoader) null)

        try {
            Class.forName(requiredClass, false, classLoader)
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Required kernel library '${libraryName}' is missing from the runtime classpath. " +
                "Please add it as a dependency in your build.gradle.",
                e
            )
        } finally {
            classLoader.close()
        }
    }
}
