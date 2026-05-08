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

/* eslint-disable @typescript-eslint/no-explicit-any */

import FsSync from "node:fs";
import Fs from "node:fs/promises";
import path from "path";

import { execa } from "execa";
import stripAnsi from "strip-ansi";
import { afterEach, describe, expect, it } from "vitest";

import { generateUid } from "../internal/test-recipe.js";

const FIXTURES_DIR = path.join(import.meta.dirname, "__fixtures__");
const CLI_PATH = [path.join(FIXTURES_DIR, "test-cli.ts"), "--no-git-check"];
const TMP_DIR = path.join(process.cwd(), "temp");

describe("Factory Integration Tests", () => {
	const tmpDirs: string[] = [];

	afterEach(async () => {
		for (const dir of tmpDirs) {
			if (FsSync.existsSync(dir)) {
				await Fs.rm(dir, { recursive: true, force: true });
			}
		}

		tmpDirs.length = 0;
	});

	async function copyFixtureToTmp(
		fixtureName: string,
		testName: string
	): Promise<string> {
		const tmpTestDir = path.join(TMP_DIR, `${testName}-${generateUid()}`);
		const fixtureSource = path.join(FIXTURES_DIR, fixtureName);

		await Fs.cp(fixtureSource, tmpTestDir, { recursive: true });

		tmpDirs.push(tmpTestDir);

		return tmpTestDir;
	}

	describe("CLI --help", () => {
		it("should display help output", async () => {
			const { stdout } = await execa("tsx", [...CLI_PATH, "--help"]);
			const cleanOutput = stripAnsi(stdout);

			expect(cleanOutput).toMatchSnapshot("help-output");
		});

		it.skip("should show help when no arguments are provided", async () => {
			const { stdout } = await execa("tsx", [...CLI_PATH]);
			const cleanOutput = stripAnsi(stdout);

			expect(cleanOutput).toMatchSnapshot("help-output-no-args");
		});
	});

	describe("CLI --version", () => {
		it("should display the configured version", async () => {
			const { stdout } = await execa("tsx", [...CLI_PATH, "--version"]);

			expect(stdout.trim()).toBe("1.0.0-test");
		});
	});

	describe("CLI --list", () => {
		it("should list all recipes", async () => {
			const { stdout } = await execa("tsx", [...CLI_PATH, "--list"]);
			const cleanOutput = stripAnsi(stdout);

			expect(cleanOutput).toMatchSnapshot("list-output");
			expect(cleanOutput).toContain("migrate-api");
			expect(cleanOutput).toContain("add-types");
		});

		it("should list recipes with -l flag", async () => {
			const { stdout } = await execa("tsx", [...CLI_PATH, "-l"]);

			expect(stripAnsi(stdout)).toMatchSnapshot("list-output-short-flag");
		});
	});

	describe("CLI recipe execution", () => {
		it("should execute recipe and transform files", async () => {
			const testDir = await copyFixtureToTmp("source-code", "execute-recipe");

			const tsconfigPath = path.join(testDir, "tsconfig.json");
			const appFile = path.join(testDir, "app.ts");

			// Execute recipe
			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"migrate-api",
				tsconfigPath
			]);

			// Assert stdout
			expect(stripAnsi(stdout)).toMatchSnapshot("recipe-execution-stdout");

			// Assert file transformation
			const transformed = await Fs.readFile(appFile, "utf-8");
			expect(transformed).toMatchSnapshot("transformed-app-file");
			expect(transformed).toContain("newApi");
			expect(transformed).not.toContain("oldApi");
		});

		it("should handle multiple files", async () => {
			const testDir = await copyFixtureToTmp("multi-files", "multi-files");

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// Execute recipe
			await execa("tsx", [...CLI_PATH, "migrate-api", tsconfigPath]);

			// Verify all files transformed
			const file1 = await Fs.readFile(path.join(testDir, "file1.ts"), "utf-8");
			const file2 = await Fs.readFile(path.join(testDir, "file2.ts"), "utf-8");
			const file3 = await Fs.readFile(path.join(testDir, "file3.ts"), "utf-8");

			expect(file1).toMatchSnapshot("file1-transformed");
			expect(file2).toMatchSnapshot("file2-transformed");
			expect(file3).toMatchSnapshot("file3-transformed");

			expect(file1).toContain("newApi.method1()");
			expect(file2).toContain("newApi.method2()");
			expect(file3).toContain("newApi.method3()");
		});

		it("should recursively apply with recursive enabled", async () => {
			const testDir = await copyFixtureToTmp(
				"recursive-configs",
				"recursive-configs"
			);

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// recursive respects gitignore and the test dir is ignored, so we need a local repo to test if recursive works
			await execa("git", ["init"], {
				cwd: testDir
			});

			const recursiveExecution = [...CLI_PATH, "--recursive", "3"];
			// Execute recipe
			await execa("tsx", [...recursiveExecution, "migrate-api", tsconfigPath]);

			// Verify all files transformed
			const file1 = await Fs.readFile(path.join(testDir, "file1.ts"), "utf-8");
			const file2 = await Fs.readFile(
				path.join(testDir, "other", "file2.ts"),
				"utf-8"
			);
			const file3 = await Fs.readFile(
				path.join(testDir, "packages", "proj", "file3.ts"),
				"utf-8"
			);

			expect(file1).toMatchSnapshot("file1-transformed");
			expect(file2).toMatchSnapshot("file2-transformed");
			expect(file3).toMatchSnapshot("file3-transformed");

			expect(file1).toContain("newApi.method1()");
			expect(file2).toContain("newApi.method2()");
			expect(file3).toContain("newApi.method3()");
		});

		it("should respect gitignore with recursive enabled", async () => {
			const testDir = await copyFixtureToTmp(
				"respect-ignore",
				"respect-ignore"
			);

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// recursive respects gitignore and the test dir is ignored, so we need a local repo to test if recursive works
			await execa("git", ["init"], {
				cwd: testDir
			});

			const recursiveExecution = [...CLI_PATH, "--recursive", "3"];
			// Execute recipe
			await execa("tsx", [...recursiveExecution, "migrate-api", tsconfigPath]);

			// Verify all files transformed
			const file1 = await Fs.readFile(
				path.join(testDir, "other", "file1.ts"),
				"utf-8"
			);
			const file2 = await Fs.readFile(path.join(testDir, "file2.ts"), "utf-8");

			expect(file1).toMatchSnapshot("file1-non-transformed");
			expect(file2).toMatchSnapshot("file2-transformed");

			expect(file1).toContain("oldApi.method1()");
			expect(file2).toContain("newApi.method2()");
		});

		it("should log execution progress", async () => {
			const testDir = await copyFixtureToTmp("source-code", "execution-logs");

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"migrate-api",
				tsconfigPath
			]);
			const cleanOutput = stripAnsi(stdout);

			expect(cleanOutput).toMatchSnapshot("execution-progress-logs");
			expect(cleanOutput).toContain("→ Running recipe migrate-api");
			expect(cleanOutput).toContain("✓ Run recipe migrate-api successfully");
		});
	});

	describe("CLI error handling", () => {
		it("should show error for non-existent recipe", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"error-recipe-not-found"
			);

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			try {
				await execa("tsx", [...CLI_PATH, "non-existent-recipe", tsconfigPath]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				const cleanStderr = stripAnsi(error.stderr);
				expect(cleanStderr).toMatchSnapshot("error-recipe-not-found");
				expect(cleanStderr).toContain('Recipe "non-existent-recipe" not found');
			}
		});

		it("should suggest similar recipe for typo with 1 character difference", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"error-typo-one-char"
			);

			const tsconfigPath = path.join(testDir, "tsconfig.json");

			try {
				await execa("tsx", [...CLI_PATH, "migrat-api", tsconfigPath]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				expect(stripAnsi(error.stderr)).toMatchSnapshot(
					"error-typo-suggestion"
				);
			}
		});

		it("should show error when tsconfig-path is missing", async () => {
			try {
				await execa("tsx", [...CLI_PATH, "migrate-api"]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				const cleanStderr = stripAnsi(error.stderr);
				expect(cleanStderr).toMatchSnapshot("error-missing-tsconfig");
				expect(cleanStderr).toContain(
					"tsconfig path is required when running a recipe"
				);
			}
		});

		it("should show error for invalid tsconfig path", async () => {
			try {
				await execa("tsx", [
					...CLI_PATH,
					"migrate-api",
					"/non/existent/path/tsconfig.json"
				]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				const cleanStderr = stripAnsi(error.stderr);
				expect(cleanStderr).toMatchSnapshot("error-invalid-tsconfig-path");
			}
		});
	});

	describe("tsconfig path resolution", () => {
		it("should accept absolute path to tsconfig.json file", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"resolve-tsconfig-file"
			);
			const tsConfigPath = path.join(testDir, "tsconfig.json");
			const appFile = path.join(testDir, "app.ts");

			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"migrate-api",
				tsConfigPath
			]);

			expect(stripAnsi(stdout)).toContain(
				"Run recipe migrate-api successfully"
			);
			const transformed = await Fs.readFile(appFile, "utf-8");
			expect(transformed).toContain("newApi");
		});

		it("should accept absolute path to directory containing tsconfig.json", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"resolve-tsconfig-dir"
			);
			const appFile = path.join(testDir, "app.ts");

			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"migrate-api",
				testDir
			]);

			expect(stripAnsi(stdout)).toContain(
				"Run recipe migrate-api successfully"
			);
			const transformed = await Fs.readFile(appFile, "utf-8");
			expect(transformed).toContain("newApi");
		});

		it("should accept relative path to directory containing tsconfig.json", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"resolve-tsconfig-relative"
			);
			const appFile = path.join(testDir, "app.ts");
			const relativePath = path.relative(process.cwd(), testDir);

			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"migrate-api",
				relativePath
			]);

			expect(stripAnsi(stdout)).toContain(
				"Run recipe migrate-api successfully"
			);
			const transformed = await Fs.readFile(appFile, "utf-8");
			expect(transformed).toContain("newApi");
		});

		it("should show error if path does not exist", async () => {
			try {
				await execa("tsx", [...CLI_PATH, "migrate-api", "/nonexistent/path"]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				expect(stripAnsi(error.stderr)).toContain(
					"Path does not exist: /nonexistent/path"
				);
			}
		});

		it("should show error if directory does not contain tsconfig.json", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"resolve-no-tsconfig"
			);
			const emptyDir = path.join(testDir, "empty-subdir");
			await Fs.mkdir(emptyDir, { recursive: true });

			try {
				await execa("tsx", [...CLI_PATH, "migrate-api", emptyDir]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				expect(stripAnsi(error.stderr)).toContain(
					"tsconfig.json not found in directory:"
				);
			}
		});
	});

	describe("CLI version-based migration", () => {
		it("should run all matching recipes for a target version", async () => {
			const testDir = await copyFixtureToTmp(
				"version-migration",
				"version-migration"
			);
			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// Version 2.5.0 should match: migrate-api (^2.0.0), rename-imports (>=2.0.0 <3.0.0), update-config (>=2.5.0)
			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"2.5.0",
				tsconfigPath
			]);

			const cleanOutput = stripAnsi(stdout);
			expect(cleanOutput).toMatchSnapshot("version-migration-output");
			expect(cleanOutput).toContain("Found 3 recipe(s) for version 2.5.0");
			expect(cleanOutput).toContain("migrate-api");
			expect(cleanOutput).toContain("rename-imports");
			expect(cleanOutput).toContain("update-config");

			// Verify transformations
			const appFile = await Fs.readFile(path.join(testDir, "app.ts"), "utf-8");
			expect(appFile).toContain("newApi");
			expect(appFile).not.toContain("oldApi");
			expect(appFile).toContain("modernImport");
			expect(appFile).not.toContain("legacyImport");
			expect(appFile).toContain("newConfig");
			expect(appFile).not.toContain("oldConfig");
		});

		it("should run only matching recipes for version 2.0.0", async () => {
			const testDir = await copyFixtureToTmp(
				"version-migration",
				"version-migration-2.0"
			);
			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// Version 2.0.0 should match: migrate-api (^2.0.0), rename-imports (>=2.0.0 <3.0.0)
			// Should NOT match: update-config (>=2.5.0)
			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"2.0.0",
				tsconfigPath
			]);

			const cleanOutput = stripAnsi(stdout);
			expect(cleanOutput).toContain("Found 2 recipe(s) for version 2.0.0");
			expect(cleanOutput).toContain("migrate-api");
			expect(cleanOutput).toContain("rename-imports");
			expect(cleanOutput).not.toContain("update-config");
		});

		it("should run add-types recipe for version 3.2.0", async () => {
			const testDir = await copyFixtureToTmp(
				"source-code",
				"version-migration-3.2"
			);
			const tsconfigPath = path.join(testDir, "tsconfig.json");

			// Version 3.2.0 should match: add-types (^3.2.0), update-config (>=2.5.0)
			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"3.2.0",
				tsconfigPath
			]);

			const cleanOutput = stripAnsi(stdout);
			expect(cleanOutput).toContain("Found 2 recipe(s) for version 3.2.0");
			expect(cleanOutput).toContain("add-types");
			expect(cleanOutput).toContain("update-config");
		});

		it("should show error when no recipes match the target version", async () => {
			const testDir = await copyFixtureToTmp("source-code", "version-no-match");
			const tsconfigPath = path.join(testDir, "tsconfig.json");

			try {
				// Version 1.0.0 should not match any recipe
				await execa("tsx", [...CLI_PATH, "1.0.0", tsconfigPath]);
				expect.fail("Should have thrown error");
			} catch (error: any) {
				const cleanStderr = stripAnsi(error.stderr);
				expect(cleanStderr).toMatchSnapshot("error-no-recipes-for-version");
				expect(cleanStderr).toContain(
					'No recipes found for target version "1.0.0"'
				);
			}
		});

		it("should accept version with v prefix", async () => {
			const testDir = await copyFixtureToTmp(
				"version-migration",
				"version-with-v-prefix"
			);
			const tsconfigPath = path.join(testDir, "tsconfig.json");

			const { stdout } = await execa("tsx", [
				...CLI_PATH,
				"v2.5.0",
				tsconfigPath
			]);

			const cleanOutput = stripAnsi(stdout);
			expect(cleanOutput).toContain("Found 3 recipe(s) for version v2.5.0");
		});
	});
});
