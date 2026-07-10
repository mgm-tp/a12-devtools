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
import java.util.Optional;

import com.mgmtp.a12.kernel.md.facade.DocumentModelServiceFactory;
import com.mgmtp.a12.kernel.md.model.api.IDocumentModel;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelSerializer;
import com.mgmtp.a12.kernel.md.model.api.services.IDocumentModelService;
import com.mgmtp.a12.kernel.md.model.api.services.IValidationCodeGeneratorConfig;
import com.mgmtp.a12.model.notification.RankedNotification;
import com.mgmtp.a12.model.notification.Severity;

/**
 * Generates plain JS validation code via the kernel codegen library.
 *
 * <p>Deserializes the (already expanded) document-model JSON into an {@link IDocumentModel},
 * then calls {@link IDocumentModelService#generateValidationCode}. The config selects
 * {@code JAVASCRIPT} + {@code STANDARD} + {@code isPlain()=true}, which makes the service return
 * the raw single-file JS source bytes (not a ZIP).
 */
final class KernelValidationJsGenerator implements ValidationJsGenerator {

    private static final DocumentModelServiceFactory FACTORY = new DocumentModelServiceFactory();

    private static final IValidationCodeGeneratorConfig CONFIG = new IValidationCodeGeneratorConfig() {
        @Override
        public IValidationCodeGeneratorConfig.ProgrammingLanguage getProgrammingLanguage() {
            return IValidationCodeGeneratorConfig.ProgrammingLanguage.JAVASCRIPT;
        }

        @Override
        public Optional<String> getPackageName() {
            return Optional.empty();
        }

        @Override
        public IValidationCodeGeneratorConfig.JsCodeGenConfig getJsCodeGenConfig() {
            return IValidationCodeGeneratorConfig.JsCodeGenConfig.STANDARD;
        }

        @Override
        public boolean isPlain() {
            return true;
        }
    };

    @Override
    public byte[] generate(String modelJson) {
        IDocumentModel documentModel = deserialize(modelJson);
        List<RankedNotification> notifications = new ArrayList<>();
        byte[] js = FACTORY.createDocumentModelService()
                .generateValidationCode(documentModel, CONFIG, null, notifications::add);
        failOnErrors(notifications);
        return js;
    }

    private IDocumentModel deserialize(String modelJson) {
        try {
            return FACTORY.createDocumentModelSerializer().deserialize(new StringReader(modelJson));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize document model for codegen", e);
        }
    }

    private void failOnErrors(List<RankedNotification> notifications) {
        // Fail-fast: surface any error-severity notification rather than emitting bad code.
        notifications.stream()
                .filter(KernelValidationJsGenerator::isError)
                .findFirst()
                .ifPresent(n -> {
                    throw new IllegalStateException(
                            "Validation code generation reported an error: " + n.getMessage());
                });
    }

    private static boolean isError(RankedNotification notification) {
        return notification.getSeverity() == Severity.ERROR;
    }
}
