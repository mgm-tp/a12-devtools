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

import { MigrationResult } from "./migration-result.js";
import { type Context, type Options } from "./migrate.js";
import { migrateLibraryImport } from "./migrate-library-import.js";
import { migrateRelativeImport } from "./migrate-relative-import.js";

export async function migrateImport(
	context: Context,
	options: Options
): Promise<MigrationResult> {
	const { specifier, filePath, logger } = context;

	const extension = Path.extname(specifier);

	if (options.ignoreExtensions.includes(extension)) {
		return MigrationResult.Skip(`Detect ignored extension: "${extension}"`);
	}

	if (extension === ".js") {
		return MigrationResult.Skip(`Detect JS extension: "${extension}"`);
	}

	if (extension === ".json") {
		return MigrationResult.Skip(`Detect JSON extension: "${extension}"`);
	}

	if (extension) {
		logger.debug(`Detect unknown extension: "${extension}" => Continue`);
	}

	if (specifier.startsWith("node:")) {
		return MigrationResult.Skip(`Detect builtin Node module: ${specifier}`);
	}

	if (specifier.startsWith(".")) {
		logger.debug("Detect relative import");

		return migrateRelativeImport(context);
	}

	if (options.ignorePackages?.includes(specifier)) {
		return MigrationResult.Skip(`Detect ignore package: "${specifier}"`);
	}

	if (Object.keys(options.aliases).includes(specifier)) {
		logger.debug("Detect tsconfig alias");

		return migrateAlias(filePath, options.aliases[specifier]);
	}

	logger.debug("Detect library import");

	return migrateLibraryImport(context);
}

function migrateAlias(
	filePath: string,
	aliasFilePath: string
): MigrationResult {
	const relativePath = Path.relative(Path.dirname(filePath), aliasFilePath);

	return MigrationResult.Success(
		relativePath.replace(".tsx", ".js").replace(".ts", ".js")
	);
}
