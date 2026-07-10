#!/usr/bin/env node
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

import Yargs from "yargs";
import { hideBin } from "yargs/helpers";

import { migrate } from "./migrate.js";

const argv = Yargs(hideBin(process.argv))
	.usage(
		"Usage: $0 <path> [options]\n\n" +
			`<path> can be an absolute path to the migrating file or directory`
	)
	.demandCommand(1)
	.option("alias", {
		type: "string",
		describe:
			"Comma-separated tsconfig alias mappings: alias1=absoluteFilePath1,alias2=absoluteFilePath2",
		coerce: (val: string | undefined) => {
			const map: Record<string, string> = {};

			for (const item of val?.split(",") ?? []) {
				const [key, value] = item.split("=");

				if (!key || !value) {
					throw new Error(`Invalid alias format: ${item}`);
				}

				map[key] = value;
			}

			return map;
		}
	})
	.option("ignore-packages", {
		type: "string",
		describe: "Comma-separated list of import packages to ignore",
		coerce: (ignore: string | string[] | undefined) => {
			if (Array.isArray(ignore)) {
				return ignore;
			}

			return ignore?.split(",") ?? [];
		},
		default: []
	})
	.option("ignore-files", {
		type: "string",
		description:
			"Comma-separated file patterns to ignore. E.g: '/**/*.skip-test/*'",
		coerce: (val: string) => {
			if (Array.isArray(val)) {
				return val;
			}

			return val.split(",").map(p => p.trim());
		},
		default: []
	})
	.option("ignore-extensions", {
		type: "string",
		description:
			"Comma-separated list of import extensions to ignore. E.g: '.png,.svg'",
		coerce: (val: string) => {
			if (Array.isArray(val)) {
				return val;
			}

			return val.split(",").map(p => p.trim());
		},
		default: ".css,.svg"
	})
	.option("debug", {
		type: "boolean",
		describe: "Enable debug mode",
		default: false
	})
	.locale("en")
	.help()
	.parseSync();

migrate(argv._[0] as string, {
	aliases: argv.alias ?? {},
	ignorePackages: argv.ignorePackages || [],
	ignoreFiles: argv.ignoreFiles || [],
	debug: argv.debug,
	ignoreExtensions: argv.ignoreExtensions
}).then(() => {
	console.log("Done");
});
