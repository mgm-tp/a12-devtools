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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.WorkspaceFactory;
import com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter;
import com.mgmtp.a12.dataservices.wcf.domain.ModelTuple;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

/**
 * Generates JS validation code for every document model in the workspace and adds it as a
 * {@code data/code/<modelId>.validation.js} FileTuple.
 *
 * <p>Opt-in: does nothing unless the system property {@code validation.codegen.enabled} is
 * {@code "true"}. This keeps normal WcfCli runs (where this converter is on the classpath but
 * disabled) byte-for-byte unchanged. Runs at order 55 — after expansion (50), before metadata (60).
 */
@WcfConverter(order = 55, description = "Generates validation JS code for document models")
public final class ValidationCodeConverter implements WorkspaceConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationCodeConverter.class);

    static final String ENABLED_PROPERTY = "validation.codegen.enabled";
    static final String DOCUMENT_MODEL_TYPE = "document";
    static final String OUTPUT_PREFIX = "data/code/";
    static final String OUTPUT_SUFFIX = ".validation.js";

    private final boolean enabled;
    private final ValidationJsGenerator generator;
    private final FileTupleFactory fileTupleFactory;

    /** Production constructor used by WcfCli's Spring component scan. */
    public ValidationCodeConverter() {
        this(
                "true".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY)),
                new KernelValidationJsGenerator(),
                (path, content) -> WorkspaceFactory.getInstance().createFileTuple(path, content));
    }

    ValidationCodeConverter(boolean enabled, ValidationJsGenerator generator,
            FileTupleFactory fileTupleFactory) {
        this.enabled = enabled;
        this.generator = generator;
        this.fileTupleFactory = fileTupleFactory;
    }

    @Override
    public Workspace convert(Workspace workspace) {
        if (!enabled) {
            return workspace;
        }
        for (ModelTuple model : workspace.getModels().values()) {
            if (!DOCUMENT_MODEL_TYPE.equals(model.getHeader().getModelType())) {
                continue;
            }
            String modelId = model.getHeader().getId();
            byte[] js = generator.generate(model.getContent());
            String outputPath = OUTPUT_PREFIX + modelId + OUTPUT_SUFFIX;
            workspace.getFiles().put(outputPath, fileTupleFactory.create(outputPath, js));
            LOGGER.debug("Generated validation code for model {} -> {}", modelId, outputPath);
        }
        return workspace;
    }
}
