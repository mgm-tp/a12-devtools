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

import eslintPlugin from "@eslint/js";
import type { ESLint, Linter } from "eslint";
import importPlugin from "eslint-plugin-import";
import reactPlugin from "eslint-plugin-react";
import reactHooksPlugin from "eslint-plugin-react-hooks";
import globals from "globals";
import tsEslintPlugin from "typescript-eslint";

type Config = Linter.Config;

function createBaseConfig(strict: boolean): readonly Config[] {
	return [
		eslintPlugin.configs.recommended,
		...tsEslintPlugin.configs[strict ? "strict" : "recommended"],
		importPlugin.flatConfigs.typescript,
		{
			name: `devtools/${strict ? "strict" : "recommended"}`,
			languageOptions: {
				globals: { ...globals.browser, ...globals.node }
			},
			settings: {
				"import/internal-regex": "^@com.mgmtp.a12"
			},
			rules: {
				curly: "error",
				"@typescript-eslint/no-namespace": "off",
				"import/order": [
					"error",
					{
						groups: [
							"builtin",
							"external",
							"internal",
							"parent",
							"sibling",
							"index"
						],
						pathGroups: [
							{
								pattern: "../**",
								group: "parent",
								position: "after"
							}
						],
						"newlines-between": "always"
					}
				],
				"import/newline-after-import": ["error", { count: 1 }]
			}
		}
	];
}

function createReactConfig(strict: boolean): Config {
	return {
		...reactPlugin.configs.flat.recommended,
		name: `devtools/react-${strict ? "strict" : "recommended"}`,
		settings: {
			react: {
				version: "detect"
			}
		},
		plugins: {
			...reactPlugin.configs.flat.recommended.plugins,
			"react-hooks": reactHooksPlugin as ESLint.Plugin
		},
		rules: {
			...reactPlugin.configs.flat.recommended.rules,
			"react/display-name": "off",
			"react/prop-types": "off",

			...reactHooksPlugin.configs.recommended.rules,
			"react-hooks/exhaustive-deps": strict ? "error" : "warn"
		}
	};
}

/**
 * The base config for A12 products. It includes the recommended rules for JavaScript and TypeScript, as well as some additional rules and settings.
 */
export const recommended = createBaseConfig(false);

/**
 * The base config for A12 products using react. Same as `recommended`, but using additional rules for react.
 */
export const reactRecommended = [
	...createBaseConfig(false),
	createReactConfig(false)
];
/**
 * Same as `recommended`, but using the strict presets instead.
 */
export const strict = createBaseConfig(true);

/**
 * Same as `reactRecommended`, but using the strict presets instead.
 */
export const reactStrict = [...createBaseConfig(true), createReactConfig(true)];
