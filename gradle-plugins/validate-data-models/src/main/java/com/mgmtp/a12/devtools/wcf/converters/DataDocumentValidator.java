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
package com.mgmtp.a12.devtools.wcf.converters;

import java.util.List;

/**
 * Validates a single data document against its document model.
 *
 * <p>Seam so {@link DataDocumentValidationConverter} can be unit-tested without a real kernel
 * {@code DocumentServiceFactory} on the classpath.
 */
@FunctionalInterface
interface DataDocumentValidator {

    /**
     * Deserializes {@code docJson} against the document model identified by {@code dmId} and
     * returns all notification messages produced during deserialization.
     *
     * <p>Precondition: {@code docJson} is the raw document JSON (the value of the {@code "document"}
     * key in a data file), not the full wrapper object. {@code dmId} must be a non-blank document
     * model id resolvable by the underlying resolver.
     *
     * @param docJson the raw document JSON string
     * @param dmId    the document model id
     * @return notification messages — empty when the document is valid, non-empty when it has
     *         unknown fields, groups, or other deserialization issues
     */
    List<String> validate(String docJson, String dmId);
}
