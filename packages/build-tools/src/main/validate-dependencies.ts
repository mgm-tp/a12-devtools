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

import { readFileSync } from "node:fs";
import { join } from "node:path";

import type { Command, Config } from "./interfaces.js";

export const validateDependencies: Command = {
	name: "validate-dependencies <CONFIG_FILE>",
	description:
		"validates the dependencies of the package.json in the current directory",
	action: (configFilePath: string) => {
		const configJSONFile = readFileSync(configFilePath, "utf8");
		const { versionDefinitions }: Config = JSON.parse(configJSONFile);

		// read package json
		const packageJSONFile = readFileSync(
			join(process.cwd(), "package.json"),
			"utf8"
		);
		const packageJSON = JSON.parse(packageJSONFile);

		// define the names of dependency objects in a packageJSON
		const dependencyTypes = [
			"dependencies",
			"devDependencies",
			"peerDependencies",
			"optionalDependencies"
		];

		let exitCode = 0;

		// filter which packages does not fulfill the requirements and print them afterwards
		dependencyTypes
			.map(type => {
				return !packageJSON[type]
					? []
					: Object.keys(packageJSON[type]).filter(
							depName =>
								versionDefinitions[depName] &&
								versionDefinitions[depName] !== packageJSON[type][depName]
						);
			})
			.forEach((errors, index) => {
				if (errors.length > 0) {
					const dependencyType = dependencyTypes[index];
					const dependencies = packageJSON[dependencyType];
					console.error(dependencyType);
					errors.forEach(depName => {
						console.error(
							`* ${depName}@${dependencies[depName]} does not fulfill ${versionDefinitions[depName]}`
						);
					});
					exitCode = 1;
				}
			});

		process.exit(exitCode);
	}
};
