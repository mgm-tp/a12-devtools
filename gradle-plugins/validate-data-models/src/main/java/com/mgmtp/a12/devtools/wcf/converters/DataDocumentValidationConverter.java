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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgmtp.a12.dataservices.wcf.WorkspaceConverter;
import com.mgmtp.a12.dataservices.wcf.annotations.WcfConverter;
import com.mgmtp.a12.dataservices.wcf.domain.Workspace;

/**
 * Validates all data documents in the workspace against their document models.
 *
 * <p>Reads the {@code workspacedata_items.json} index from the workspace input directory, then for
 * each registered data document deserializes it against its document model using the kernel
 * {@code IDocumentV2Serializer}. Any deserialization notifications (unknown fields, unknown groups,
 * type mismatches, etc.) are treated as validation failures.
 *
 * <p>Collects <em>all</em> failures before throwing, so a single run surfaces every broken document
 * rather than stopping at the first.
 *
 * <p>Throws {@link IllegalStateException} iff one or more documents are invalid. The message lists
 * every broken file and its notifications, matching the format of the Gradle
 * {@code validateDataDocuments} script this converter supersedes.
 *
 * <p>File content is read directly from the workspace input directory on disk.
 * {@link com.mgmtp.a12.dataservices.wcf.domain.FileTuple} content is always null for files loaded
 * by the WCF supplier — the workspace is used only to discover which files exist and to access
 * {@link Workspace#getInputDir()}.
 *
 * <p>Opt-in: does nothing unless the system property {@code data.document.validation.enabled} is
 * {@code "true"}. This ensures the converter is inert on classpaths where it is present but
 * validation has not been requested. Runs at order 200 — after the full RMC conversion pipeline.
 */
@WcfConverter(order = 200, description = "Validates data documents against their document models")
public final class DataDocumentValidationConverter implements WorkspaceConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataDocumentValidationConverter.class);

    static final String ENABLED_PROPERTY = "data.document.validation.enabled";
    static final String WORKSPACE_DATA_ITEMS_SUFFIX = "workspacedata_items.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final DataDocumentValidator validator;

    /** Production constructor used by WcfCli's Spring component scan. */
    public DataDocumentValidationConverter() {
        this.enabled = "true".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY));
        // validator is built lazily in convert() once we have the workspace models
        this.validator = null;
    }

    DataDocumentValidationConverter(boolean enabled, DataDocumentValidator validator) {
        this.enabled = enabled;
        this.validator = validator;
    }

    @Override
    public Workspace convert(Workspace workspace) {
        if (!enabled) {
            return workspace;
        }

        Path inputDir = Path.of(workspace.getInputDir());
        String indexKey = findWorkspaceDataItemsKey(workspace);
        if (indexKey == null) {
            LOGGER.debug("No {} found in workspace — skipping data document validation", WORKSPACE_DATA_ITEMS_SUFFIX);
            return workspace;
        }

        Map<String, String> docEntries = parseDocumentEntries(inputDir, indexKey);
        if (docEntries.isEmpty()) {
            LOGGER.info("Data document validation: no documents registered in {} — nothing to validate",
                    WORKSPACE_DATA_ITEMS_SUFFIX);
            return workspace;
        }

        DataDocumentValidator effectiveValidator = validator != null
                ? validator
                : new KernelDataDocumentValidator(workspace.getModels());

        Map<String, List<String>> failures = validateAll(inputDir, docEntries, effectiveValidator);

        if (!failures.isEmpty()) {
            throw new IllegalStateException(buildErrorMessage(failures));
        }

        LOGGER.info("Data document validation passed: all {} data document(s) checked successfully.",
                docEntries.size());
        return workspace;
    }

    private static String findWorkspaceDataItemsKey(Workspace workspace) {
        return workspace.getFiles().keySet().stream()
                .filter(k -> k.endsWith(WORKSPACE_DATA_ITEMS_SUFFIX))
                .findFirst()
                .orElse(null);
    }

    /**
     * Parses the {@code documents} map from the index file.
     *
     * @return map from {@code fileName} (project-root-relative data file path) to {@code dmId}
     */
    private static Map<String, String> parseDocumentEntries(Path inputDir, String indexKey) {
        Path indexFile = inputDir.resolve(indexKey);
        try {
            String json = Files.readString(indexFile);
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode documents = root.path("documents");
            if (documents.isMissingNode() || documents.isNull()) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            documents.fields().forEachRemaining(entry -> {
                String key = entry.getKey(); // "<dmId>/<uuid>"
                String fileName = entry.getValue().path("fileName").asText();
                String dmId = key.substring(0, key.indexOf('/'));
                result.put(fileName, dmId);
            });
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + WORKSPACE_DATA_ITEMS_SUFFIX, e);
        }
    }

    private static Map<String, List<String>> validateAll(
            Path inputDir,
            Map<String, String> docEntries,
            DataDocumentValidator effectiveValidator) {
        // Data file paths in workspacedata_items.json are relative to the project root (parent of inputDir).
        Path projectDir = inputDir.getParent();
        Map<String, List<String>> failures = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : docEntries.entrySet()) {
            String fileName = entry.getKey();
            String dmId = entry.getValue();
            List<String> messages = validateOne(projectDir, fileName, dmId, effectiveValidator);
            if (!messages.isEmpty()) {
                failures.put(fileName, messages);
            }
        }
        return failures;
    }

    private static List<String> validateOne(
            Path projectDir,
            String fileName,
            String dmId,
            DataDocumentValidator effectiveValidator) {
        Path filePath = projectDir.resolve(fileName);
        try {
            String fileJson = Files.readString(filePath);
            JsonNode root = OBJECT_MAPPER.readTree(fileJson);
            JsonNode documentNode = root.path("document");
            String docJson = documentNode.isMissingNode()
                    ? fileJson
                    : OBJECT_MAPPER.writeValueAsString(documentNode);
            return effectiveValidator.validate(docJson, dmId);
        } catch (NoSuchFileException e) {
            return List.of("data document file not found: " + filePath);
        } catch (IOException e) {
            return List.of("Failed to parse file: " + e.getMessage());
        }
    }

    private static String buildErrorMessage(Map<String, List<String>> failures) {
        String details = failures.entrySet().stream()
                .map(e -> "  " + e.getKey() + ":\n"
                        + e.getValue().stream()
                                .map(m -> "    - " + m)
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"));
        return "Data document validation failed — " + failures.size()
                + " file(s) cannot be fully deserialized:\n" + details
                + "\n\nEnsure data documents are in sync with their document models.";
    }
}
