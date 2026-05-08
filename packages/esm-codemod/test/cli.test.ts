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

import Fs from "node:fs/promises";
import Path from "node:path";
import { beforeEach, describe, test, type TestContext } from "node:test";

import { execa } from "execa";
import stripAnsi from "strip-ansi";

describe("esm-codemod", () => {
	const fixtureDir = Path.join(import.meta.dirname, "fixture");
	const tempDir = Path.join(fixtureDir, "temp");

	function snapshot(t: TestContext, header: string, content: string) {
		t.assert.snapshot(
			`// ${header}\n${stripAnsi(content.replaceAll(fixtureDir, "ROOT"))}`,
			{ serializers: [(value: unknown) => value] }
		);
	}

	async function copySource(sourceDirName: string) {
		await Fs.cp(
			Path.join(fixtureDir, "src", sourceDirName),
			Path.join(tempDir, sourceDirName),
			{
				recursive: true
			}
		);
	}

	beforeEach(async () => {
		await Fs.rm(tempDir, { recursive: true, force: true });
	});

	test("help", async t => {
		const { stdout } = await execa`./bin/esm-codemod --help`;

		snapshot(t, "Stdout", stdout);
	});

	test("basic", async t => {
		await copySource("basic");

		const { stdout } = await execa({
			env: { FORCE_COLOR: "0" }
		})`./bin/esm-codemod ${tempDir}`;

		snapshot(t, "Stdout", stdout);

		for await (const entry of Fs.glob(`${tempDir}/**/*.*`)) {
			snapshot(
				t,
				`File: ./${Path.relative(tempDir, entry)}`,
				await Fs.readFile(entry, "utf-8")
			);
		}
	});

	test("debug", async t => {
		await copySource("basic");

		const { stdout } = await execa`./bin/esm-codemod ${tempDir} --debug`;

		snapshot(t, "Stdout", stdout);
	});

	test("not installed package", async t => {
		await copySource("error");

		const { stdout } = await execa`./bin/esm-codemod ${tempDir} --debug`;

		snapshot(t, "Stdout", stdout);
	});
});
