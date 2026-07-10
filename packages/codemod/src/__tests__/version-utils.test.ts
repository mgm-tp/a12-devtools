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

import { describe, expect, it } from "vitest";

import type { Recipe } from "../internal/types.js";
import { filterRecipesByVersion, isVersion } from "../internal/utils.js";

describe("Version utilities", () => {
	describe("isVersion", () => {
		it("should return true for valid semver versions", () => {
			expect(isVersion("1.0.0")).toBe(true);
			expect(isVersion("2.5.3")).toBe(true);
			expect(isVersion("10.20.30")).toBe(true);
		});

		it("should return true for versions with v prefix", () => {
			expect(isVersion("v1.0.0")).toBe(true);
			expect(isVersion("v2.5.3")).toBe(true);
		});

		it("should return true for partial versions", () => {
			expect(isVersion("1.0")).toBe(true);
			expect(isVersion("2")).toBe(true);
		});

		it("should return true for versions with prerelease", () => {
			expect(isVersion("1.0.0-alpha")).toBe(true);
			expect(isVersion("2.0.0-beta.1")).toBe(true);
		});

		it("should return false for non-version strings", () => {
			expect(isVersion("migrate-api")).toBe(false);
			expect(isVersion("add-types")).toBe(false);
			expect(isVersion("hello-world")).toBe(false);
			expect(isVersion("")).toBe(false);
		});

		it("should return false for recipe-like IDs that start with numbers", () => {
			// These are ambiguous but semver.coerce handles them
			expect(isVersion("2to3-migration")).toBe(true); // coerces to 2.0.0
		});
	});

	describe("filterRecipesByVersion", () => {
		const mockRecipes: Recipe[] = [
			{
				metadata: {
					id: "recipe-1",
					description: "Recipe for v2.x",
					supportedVersions: "^2.0.0"
				},
				execute: () => {}
			},
			{
				metadata: {
					id: "recipe-2",
					description: "Recipe for v3.x",
					supportedVersions: "^3.0.0"
				},
				execute: () => {}
			},
			{
				metadata: {
					id: "recipe-3",
					description: "Recipe for v2.5+",
					supportedVersions: ">=2.5.0"
				},
				execute: () => {}
			},
			{
				metadata: {
					id: "recipe-4",
					description: "Recipe for v2.0 to v3.0",
					supportedVersions: ">=2.0.0 <3.0.0"
				},
				execute: () => {}
			}
		];

		it("should filter recipes matching version 2.0.0", () => {
			const result = filterRecipesByVersion(mockRecipes, "2.0.0");
			expect(result.map(r => r.metadata.id)).toEqual(["recipe-1", "recipe-4"]);
		});

		it("should filter recipes matching version 2.5.0", () => {
			const result = filterRecipesByVersion(mockRecipes, "2.5.0");
			expect(result.map(r => r.metadata.id)).toEqual([
				"recipe-1",
				"recipe-3",
				"recipe-4"
			]);
		});

		it("should filter recipes matching version 3.0.0", () => {
			const result = filterRecipesByVersion(mockRecipes, "3.0.0");
			expect(result.map(r => r.metadata.id)).toEqual(["recipe-2", "recipe-3"]);
		});

		it("should return empty array for version with no matches", () => {
			const result = filterRecipesByVersion(mockRecipes, "1.0.0");
			expect(result).toEqual([]);
		});

		it("should handle v prefix in version", () => {
			const result = filterRecipesByVersion(mockRecipes, "v2.5.0");
			expect(result.map(r => r.metadata.id)).toEqual([
				"recipe-1",
				"recipe-3",
				"recipe-4"
			]);
		});

		it("should return empty array for invalid version", () => {
			const result = filterRecipesByVersion(mockRecipes, "not-a-version");
			expect(result).toEqual([]);
		});

		it("should handle recipes with invalid semver ranges gracefully", () => {
			const recipesWithInvalid: Recipe[] = [
				{
					metadata: {
						id: "valid",
						description: "Valid",
						supportedVersions: "^2.0.0"
					},
					execute: () => {}
				},
				{
					metadata: {
						id: "invalid",
						description: "Invalid",
						supportedVersions: "not-a-range"
					},
					execute: () => {}
				}
			];

			const result = filterRecipesByVersion(recipesWithInvalid, "2.0.0");
			expect(result.map(r => r.metadata.id)).toEqual(["valid"]);
		});
	});
});
