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

import Path from "node:path";

import { glob } from "glob";
import { Project } from "ts-morph";

import { Reporter } from "./reporter.js";
import { migrateAdHoc } from "./migrate-ad-hoc.js";
import { migrateImport } from "./migrate-import.js";
import { MigrationType } from "./migration-result.js";
import { checkFile, checkDirectory } from "./utils.js";
import { type Logger, createLogger } from "./logger.js";
import { tsExtensions } from "./migrate-relative-import.js";

export interface Context {
	readonly logger: Logger;
	readonly filePath: string;
	readonly specifier: string;
	readonly lineNumber: number;
}

export interface Options {
	readonly debug: boolean;
	readonly ignoreFiles: string[];
	readonly ignorePackages: string[];
	readonly ignoreExtensions: string[];
	readonly aliases: Record<string, string>;
}

export async function migrate(filePath: string, options: Options) {
	const logger = createLogger(options.debug);
	const reporter = new Reporter();
	logger.debug(JSON.stringify(options, null, 2));

	if (!filePath) {
		logger.error("Usage: migrator <path-to-file-or-directory>");
		process.exit(1);
	}

	const project = new Project();

	if (await checkFile(filePath)) {
		project.addSourceFileAtPath(filePath);
	} else if (await checkDirectory(filePath)) {
		for (const extension of tsExtensions) {
			const pattern = Path.join(filePath, "**", `*${extension}`)
				.split(Path.sep)
				.join("/");

			for await (const scanFilePath of await glob(pattern, {
				ignore: ["/**/node_modules/**", ...options.ignoreFiles]
			})) {
				project.addSourceFileAtPath(scanFilePath);
			}
		}
	}

	for (const sourceFile of project.getSourceFiles()) {
		const filePath = sourceFile.getFilePath();
		console.log();

		logger.log(`=== Migrating file ${filePath} ===`);

		for (const importDecl of sourceFile.getImportDeclarations()) {
			const specifier = importDecl.getModuleSpecifierValue();
			const lineNumber = importDecl.getStartLineNumber();

			logger.debug(`Migrating import: "${specifier}"`);

			const result = await migrateImport(
				{ logger, filePath, specifier, lineNumber },
				options
			);
			reporter.add(filePath, lineNumber, result);

			if (result.type === MigrationType.Error) {
				logger.error(result.errorMessage);
			}

			if (result.type === MigrationType.Success) {
				logger.debug(`Migrated import:  ${result.newSpecifier}`);
				importDecl.setModuleSpecifier(result.newSpecifier);
			}

			migrateAdHoc(importDecl);
		}

		for (const exportDecl of sourceFile.getExportDeclarations()) {
			const specifier = exportDecl.getModuleSpecifierValue();
			const lineNumber = exportDecl.getStartLineNumber();

			if (!specifier) {
				continue;
			}

			logger.debug(`Migrating export: "${specifier}"`);
			const result = await migrateImport(
				{ logger, filePath, specifier, lineNumber },
				options
			);
			reporter.add(filePath, lineNumber, result);

			if (result.type === MigrationType.Error) {
				logger.error(result.errorMessage);
			}

			if (result.type === MigrationType.Success) {
				logger.debug(`Migrated export:  ${result.newSpecifier}`);
				exportDecl.setModuleSpecifier(result.newSpecifier);
			}
		}
	}

	await project.save();

	reporter.report();
}
