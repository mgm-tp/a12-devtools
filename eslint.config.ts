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
import { readFile } from "node:fs/promises";
import { EOL } from "node:os";

import notice from "eslint-plugin-notice";
import { defineConfig, globalIgnores } from "eslint/config";

import { strict } from "@com.mgmtp.a12.devtools/eslint-config";

const licenseHeader = await readFile("license_header.txt", "utf-8");
const licenseHeaderWithInterpreterLine = `#!/usr/bin/env node${EOL}${licenseHeader}`;

export default defineConfig([
	globalIgnores(
		["**/{lib,build}", "packages/build-tools/resources/detect-cycles"],
		"workspace-ignores"
	),
	...strict,
	{
		name: "general",
		plugins: {
			notice
		},
		languageOptions: {
			parserOptions: {
				projectService: true
			}
		},
		rules: {
			"@typescript-eslint/consistent-type-exports": "error",
			"@typescript-eslint/consistent-type-imports": "error",
			"@typescript-eslint/no-unused-vars": [
				"error",
				{
					args: "all",
					argsIgnorePattern: "^_",
					caughtErrors: "all",
					caughtErrorsIgnorePattern: "^_",
					destructuredArrayIgnorePattern: "^_",
					varsIgnorePattern: "^_",
					ignoreRestSiblings: true
				}
			],
			"notice/notice": [
				"error",
				{
					template: licenseHeader,
					onNonMatchingHeader: "replace",
					chars: licenseHeader.length
				}
			]
		}
	},
	{
		name: "files-with-license-header-and-interpreter-line",
		files: ["**/cli.ts"],
		rules: {
			"notice/notice": [
				"error",
				{
					template: licenseHeaderWithInterpreterLine,
					onNonMatchingHeader: "replace",
					chars: licenseHeaderWithInterpreterLine.length
				}
			]
		}
	}
]);
