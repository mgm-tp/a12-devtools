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
import Fs from "node:fs/promises";
import { builtinModules } from "node:module";

import { findUp } from "find-up";

import { checkDirectory } from "./utils.js";
import { MigrationResult } from "./migration-result.js";

function getPackageIdentities(specifier: string) {
	const namespace = specifier.startsWith("@");
	const segments = specifier.split("/");
	const cutPoint = namespace ? 2 : 1;
	const name = segments.slice(0, cutPoint).join("/");
	const subPath = segments.slice(cutPoint).join("/");

	return { name, subPath: subPath === "" ? "." : subPath };
}

export interface PackageJson {
	name: string;
	main: string | undefined;
	module: string | undefined;
	type: "module" | "commonjs" | undefined;
	exports:
		| string
		| Record<string, string | Record<string, string> | undefined>
		| undefined;
}

export type PackageInfo =
	| {
			esm: boolean;
			packageJson: PackageJson;
			name: string;
			subPath: string;
			absoluteLibraryPath: string;
			absoluteImportPath: string;
			builtin: false;
	  }
	| { builtin: true };

export async function getPackageInfo(
	specifier: string,
	filePath: string
): Promise<PackageInfo | MigrationResult> {
	if (builtinModules.includes(specifier)) {
		return { builtin: true };
	}

	const { name, subPath } = getPackageIdentities(specifier);
	const libraryPath = await findUp(
		async directory => {
			const path = Path.join(directory, "node_modules", name);

			if (await checkDirectory(path)) {
				return path;
			}

			return undefined;
		},
		{ cwd: filePath, type: "directory" }
	);

	if (!libraryPath) {
		return MigrationResult.Error(
			`Cannot find library with name '${name}'. Please verify that the package is declared at least as devDependency.`
		);
	}

	const content = await Fs.readFile(
		Path.join(libraryPath, "package.json"),
		"utf-8"
	);
	const packageJson = JSON.parse(content) as PackageJson;

	return {
		esm: packageJson.type === "module",
		builtin: false,
		name,
		subPath,
		packageJson,
		absoluteLibraryPath: libraryPath,
		absoluteImportPath: Path.join(libraryPath, subPath)
	};
}
