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

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.mgmtp.a12.kernel.md.document.api.services.DocumentDeserializationConfig;
import com.mgmtp.a12.kernel.md.document.apiV2.services.IDocumentV2Serializer;
import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.facade.DocumentServiceFactory;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelResolver;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelSerializer;
import com.mgmtp.a12.model.notification.RankedNotification;

/**
 * Production {@link DataDocumentValidator} that uses the kernel {@code DocumentServiceFactory} to
 * deserialize each data document against its document model.
 *
 * <p>The document model content is deserialized once per unique model id and then reused for all
 * documents of that model via an {@link IDocumentModelResolver} backed by the workspace models map.
 */
final class KernelDataDocumentValidator implements DataDocumentValidator {

    private static final DocumentDeserializationConfig DESER_CONFIG = DocumentDeserializationConfig.builder().build();

    private final IDocumentV2Serializer docSerializer;

    KernelDataDocumentValidator(java.util.Map<String, com.mgmtp.a12.dataservices.wcf.domain.ModelTuple> workspaceModels) {
        IDocumentModelResolver resolver = buildResolver(workspaceModels);
        this.docSerializer = new DocumentServiceFactory(resolver).createDocumentV2Serializer();
    }

    @Override
    public List<String> validate(String docJson, String dmId) {
        List<String> messages = new ArrayList<>();
        docSerializer.deserializeV2(new StringReader(docJson), dmId, DESER_CONFIG,
                (RankedNotification n) -> messages.add(n.getMessage()));
        return messages;
    }

    private static IDocumentModelResolver buildResolver(
            java.util.Map<String, com.mgmtp.a12.dataservices.wcf.domain.ModelTuple> workspaceModels) {
        IDocumentModelSerializer dmSerializer = new DocumentModelServiceFactory().createDocumentModelSerializer();
        java.util.Map<String, IDocumentModel> modelCache = new java.util.HashMap<>();

        for (java.util.Map.Entry<String, com.mgmtp.a12.dataservices.wcf.domain.ModelTuple> entry : workspaceModels.entrySet()) {
            if (!"document".equals(entry.getValue().getHeader().getModelType())) {
                continue;
            }
            String id = entry.getValue().getHeader().getId();
            try {
                IDocumentModel dm = dmSerializer.deserialize(new StringReader(entry.getValue().getContent()));
                modelCache.put(id, dm);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to deserialize document model '" + id + "'", e);
            }
        }

        return id -> {
            IDocumentModel dm = modelCache.get(id);
            if (dm == null) {
                throw new IllegalArgumentException("Document model not found in workspace: " + id);
            }
            return dm;
        };
    }
}
