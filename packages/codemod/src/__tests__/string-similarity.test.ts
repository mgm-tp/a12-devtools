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

import { describe, it, expect } from "vitest";

import { levenshteinDistance } from "../internal/utils.js";

describe("levenshteinDistance", () => {
	describe("identical strings", () => {
		it("should return 0 for identical strings", () => {
			expect(levenshteinDistance("hello", "hello")).toBe(0);
			expect(levenshteinDistance("migrate-api", "migrate-api")).toBe(0);
			expect(levenshteinDistance("", "")).toBe(0);
		});
	});

	describe("empty strings", () => {
		it("should return length of non-empty string when one string is empty", () => {
			expect(levenshteinDistance("", "hello")).toBe(5);
			expect(levenshteinDistance("hello", "")).toBe(5);
			expect(levenshteinDistance("", "a")).toBe(1);
		});
	});

	describe("single character operations", () => {
		it("should handle single insertion", () => {
			expect(levenshteinDistance("abc", "abcd")).toBe(1);
			expect(levenshteinDistance("cat", "cart")).toBe(1);
		});

		it("should handle single deletion", () => {
			expect(levenshteinDistance("abcd", "abc")).toBe(1);
			expect(levenshteinDistance("cart", "cat")).toBe(1);
		});

		it("should handle single substitution", () => {
			expect(levenshteinDistance("abc", "adc")).toBe(1);
			expect(levenshteinDistance("cat", "bat")).toBe(1);
			expect(levenshteinDistance("hello", "hallo")).toBe(1);
		});
	});

	describe("multiple operations", () => {
		it("should handle multiple insertions", () => {
			expect(levenshteinDistance("abc", "abcde")).toBe(2);
		});

		it("should handle multiple deletions", () => {
			expect(levenshteinDistance("abcde", "abc")).toBe(2);
		});

		it("should handle multiple substitutions", () => {
			expect(levenshteinDistance("abc", "xyz")).toBe(3);
		});

		it("should handle mixed operations", () => {
			expect(levenshteinDistance("kitten", "sitting")).toBe(3);
			expect(levenshteinDistance("saturday", "sunday")).toBe(3);
		});
	});

	describe("case sensitivity", () => {
		it("should be case-sensitive by default", () => {
			expect(levenshteinDistance("Hello", "hello")).toBe(1);
			expect(levenshteinDistance("ABC", "abc")).toBe(3);
		});
	});

	describe("special characters", () => {
		it("should handle strings with hyphens", () => {
			expect(levenshteinDistance("migrate-api", "migrate_api")).toBe(1);
		});

		it("should handle strings with underscores", () => {
			expect(levenshteinDistance("add_types", "add-types")).toBe(1);
		});

		it("should handle strings with spaces", () => {
			expect(levenshteinDistance("hello world", "hello_world")).toBe(1);
		});
	});

	describe("real-world examples", () => {
		it("should calculate distance for common typos", () => {
			expect(levenshteinDistance("migrate-api", "migrat-api")).toBe(1); // missing 'e'
			expect(levenshteinDistance("migrate-api", "migrte-api")).toBe(1); // missing 'a'
			expect(levenshteinDistance("migrate-api", "migratte-api")).toBe(1); // extra 't'
			expect(levenshteinDistance("add-types", "add-type")).toBe(1); // missing 's'
		});
	});
});
