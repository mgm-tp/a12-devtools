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

import type { Project } from "ts-morph";

import { createCodemodCLI } from "../../../src/index.js";

const recipes = [
	{
		metadata: {
			id: "migrate-api",
			name: "Migrate API",
			description: "Migrates oldApi to newApi",
			supportedVersions: "^2.0.0",
			notes: "Updates all API calls"
		},
		execute: async (project: Project) => {
			const sourceFiles = project.getSourceFiles();

			for (const file of sourceFiles) {
				const text = file.getText();

				if (text.includes("oldApi")) {
					file.replaceWithText(text.replace(/oldApi/g, "newApi"));
				}
			}
		}
	},
	{
		metadata: {
			id: "add-types",
			name: "Add Type Annotations",
			description: "Adds type annotations",
			supportedVersions: "^3.2.0"
		},
		execute: async (project: Project) => {
			const sourceFiles = project.getSourceFiles();

			for (const file of sourceFiles) {
				const functions = file.getFunctions();

				for (const fn of functions) {
					fn.addJsDoc({ description: "Type checked" });
				}
			}
		}
	},
	{
		metadata: {
			id: "rename-imports",
			name: "Rename Imports",
			description: "Renames legacy imports to modern imports",
			supportedVersions: ">=2.0.0 <3.0.0"
		},
		execute: async (project: Project) => {
			const sourceFiles = project.getSourceFiles();

			for (const file of sourceFiles) {
				const text = file.getText();

				if (text.includes("legacyImport")) {
					file.replaceWithText(text.replace(/legacyImport/g, "modernImport"));
				}
			}
		}
	},
	{
		metadata: {
			id: "update-config",
			name: "Update Config",
			description: "Updates configuration format",
			supportedVersions: ">=2.5.0"
		},
		execute: async (project: Project) => {
			const sourceFiles = project.getSourceFiles();

			for (const file of sourceFiles) {
				const text = file.getText();

				if (text.includes("oldConfig")) {
					file.replaceWithText(text.replace(/oldConfig/g, "newConfig"));
				}
			}
		}
	}
];

createCodemodCLI({
	name: "test-codemod",
	version: "1.0.0-test",
	description: "Test codemod CLI for integration testing",
	recipes
});
