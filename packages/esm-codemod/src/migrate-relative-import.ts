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

import Path from "node:path";

import { type Context } from "./migrate.js";
import { MigrationResult } from "./migration-result.js";
import { checkDirectory, checkFile } from "./utils.js";

export const tsExtensions = [".ts", ".tsx"];

export async function migrateRelativeImport(
	context: Context
): Promise<MigrationResult> {
	const { specifier, filePath, logger } = context;
	const baseFilePath = Path.resolve(Path.dirname(filePath), specifier);

	for (const ext of tsExtensions) {
		const checkingFilePath = Path.join(baseFilePath + ext);
		logger.debug("Checking file: " + checkingFilePath);

		if (await checkFile(checkingFilePath)) {
			logger.debug("Found file: " + checkingFilePath);

			return MigrationResult.Success(specifier + ".js");
		}
	}

	const isDir = await checkDirectory(baseFilePath);

	if (isDir) {
		for (const ext of tsExtensions) {
			const indexFilePath = Path.join(baseFilePath, "index" + ext);
			logger.debug("Checking file: " + indexFilePath);

			if (await checkFile(indexFilePath)) {
				logger.debug("Found file: " + indexFilePath);

				return MigrationResult.Success(`${specifier}/index.js`);
			}
		}
	}

	return MigrationResult.Error(
		`Can not find the actual tsx? file for ${specifier} in ${filePath}`
	);
}
