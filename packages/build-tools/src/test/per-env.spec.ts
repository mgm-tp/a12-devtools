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

import { deepStrictEqual } from "node:assert/strict";
import { describe, it } from "node:test";

import { resolveEnvVarName, resolveSuffix } from "../main/per-env.js";

describe("per-env", () => {
	describe("resolveEnvVarName", () => {
		it("defaults to NODE_ENV when no argument was given", () => {
			deepStrictEqual(resolveEnvVarName(undefined), "NODE_ENV");
		});

		it("defaults to NODE_ENV for a non-string argument", () => {
			deepStrictEqual(resolveEnvVarName(42), "NODE_ENV");
		});

		it("uses the given argument as the variable name", () => {
			deepStrictEqual(resolveEnvVarName("REGION"), "REGION");
		});
	});

	describe("resolveSuffix", () => {
		it("defaults NODE_ENV to 'development' when unset", () => {
			deepStrictEqual(resolveSuffix("NODE_ENV", {}), "development");
		});

		it("uses NODE_ENV's value when set", () => {
			deepStrictEqual(
				resolveSuffix("NODE_ENV", { NODE_ENV: "production" }),
				"production"
			);
		});

		it("falls back to 'unset' for a custom variable that is unset", () => {
			deepStrictEqual(resolveSuffix("REGION", {}), "unset");
		});

		it("uses a custom variable's value when set", () => {
			deepStrictEqual(resolveSuffix("REGION", { REGION: "eu" }), "eu");
		});
	});
});
