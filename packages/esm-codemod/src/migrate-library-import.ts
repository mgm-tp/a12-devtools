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
import { getPackageInfo, type PackageInfo } from "./package.js";
import { assert, checkDirectory, checkFile } from "./utils.js";

export async function migrateLibraryImport(
	context: Context
): Promise<MigrationResult> {
	const { specifier, filePath, logger } = context;
	const packageInfo = await getPackageInfo(specifier, filePath);

	if (MigrationResult.isInstance(packageInfo)) {
		return packageInfo;
	}

	if (packageInfo.builtin) {
		logger.debug(`Detect builtin Node module => Prepend "node:"`);

		return MigrationResult.Success(`node:${specifier}`);
	}

	const { packageJson, absoluteImportPath } = packageInfo;

	if (packageJson.exports !== undefined) {
		logger.debug("Detect 'exports' field");

		return checkAgainstExportsField(packageInfo);
	}

	if (specifier === packageJson.name) {
		logger.debug("Import from package name => Skip");

		return MigrationResult.Skip(`Import from package name`);
	}

	const jsFilePath = Path.join(absoluteImportPath + ".js");

	logger.debug("Checking js file: " + jsFilePath);

	if (await checkFile(jsFilePath)) {
		logger.debug(`Found js file: ${jsFilePath} => Append '.js'`);

		return MigrationResult.Success(`${specifier}.js`);
	}

	logger.debug("Checking directory: " + absoluteImportPath);

	if (await checkDirectory(absoluteImportPath)) {
		const indexFilePath = Path.join(absoluteImportPath, "index.js");

		if (await checkFile(indexFilePath)) {
			logger.debug("Found index.js file => Append '/index.js'");

			return MigrationResult.Success(`${specifier}/index.js`);
		}
	}

	return MigrationResult.Error(`Cannot migrate import: ${specifier}`);
}

function checkAgainstExportsField(packageInfo: PackageInfo) {
	assert(!packageInfo.builtin);
	const {
		packageJson: { exports, name },
		subPath
	} = packageInfo;

	assert(!!exports);

	const convertToExportsKey = subPath === "." ? "." : `./${subPath}`;

	if (Object.keys(exports).includes(convertToExportsKey)) {
		return MigrationResult.Skip(
			`Detect expose path in 'exports' field for path: '${convertToExportsKey}'`
		);
	}

	const availablePath = Object.keys(exports)
		.map(e => `'${e}'`)
		.join(", ");

	return MigrationResult.Error(
		`Can not find expose API with path '${convertToExportsKey}' in package '${name}', available paths: ${availablePath}`
	);
}
