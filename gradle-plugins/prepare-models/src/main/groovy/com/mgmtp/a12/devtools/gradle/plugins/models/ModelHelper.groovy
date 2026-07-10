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
package com.mgmtp.a12.devtools.gradle.plugins.models

class ModelHelper {
    static final boolean isFormModel(final File file) {
        return isModelType(file, 'form')
    }

    static final boolean isDocumentModel(final File file) {
        return isModelType(file, 'document')
    }

    static final boolean isModelType(final File file, final String modelType, int numberOfLines = 10) {
        boolean isType = false

        // file.isFile() guards against paths that do not exist (or are directories): Gradle
        // continuous build evaluates content-reading input/exclude filters against deleted
        // files when reacting to a delete event, and an unguarded read would throw
        // FileNotFoundException on the file-watch thread, killing the watcher.
        if (file.name.endsWith('.json') && file.isFile()) {
            file.withReader { reader ->
                def line = null
                int lineCount = 0
                while ((line = reader.readLine()) != null && (lineCount++ < numberOfLines)) {
                    if (line.contains('"modelType"')) {
                        isType = line.contains('"' + modelType + '"')
                        return
                    }
                }
            }
        }
        return isType
    }
}
