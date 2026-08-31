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

import { equal, match } from "node:assert/strict";
import { mkdir, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { chdir, cwd } from "node:process";
import { test } from "node:test";

import { buildDocs } from "./buildDocs.js";

async function fixture(author = "test@example.com"): Promise<string> {
	const dir = await mkdtemp(join(tmpdir(), "docu-build-"));
	const src = join(dir, "src");
	await mkdir(src, { recursive: true });
	await writeFile(
		join(dir, "package.json"),
		JSON.stringify({ name: "fixture", version: "9.9.9", author })
	);
	await writeFile(
		join(src, "docinfo.html"),
		`<meta name="docu-fixture" content="1">`
	);
	await writeFile(
		join(src, "index.adoc"),
		[
			"= Fixture",
			"",
			"[source,typescript]",
			"----",
			"const x: number = 1;",
			"----",
			""
		].join("\n")
	);
	return dir;
}

/** buildDocs reads process.cwd(); run it with cwd temporarily switched to `dir`. */
async function buildIn(dir: string, config?: Parameters<typeof buildDocs>[0]) {
	const previous = cwd();
	chdir(dir);
	try {
		return await buildDocs(config);
	} finally {
		chdir(previous);
	}
}

test("buildDocs builds HTML with asciidoctor's highlightjs wired", async () => {
	const dir = await fixture();
	const result = await buildIn(dir);

	const html = await readFile(join(result.outDir, "index.html"), "utf8");
	match(
		html,
		/const x: number = 1;/,
		"expected the source block to be rendered"
	);
	match(html, /9\.9\.9/, "expected revnumber from package.json");
	// asciidoctor's built-in `highlightjs` source-highlighter wires client-side
	// highlight.js (a CDN <script> + init); highlighting is no longer baked in.
	match(html, /highlightjs|highlight\.js|hljs/i, "expected highlightjs wiring");
});

test("buildDocs applies config attribute overrides", async () => {
	const dir = await fixture();
	const result = await buildIn(dir, {
		attributes: { author: "Cfg Override Author" }
	});

	const html = await readFile(join(result.outDir, "index.html"), "utf8");
	match(
		html,
		/Cfg Override Author/,
		"expected config.attributes override to win over the package.json author"
	);
});

test("buildDocs honours configured srcDir and outDir", async () => {
	const dir = await mkdtemp(join(tmpdir(), "docu-dirs-"));
	const src = join(dir, "documentation");
	await mkdir(src, { recursive: true });
	await writeFile(
		join(dir, "package.json"),
		JSON.stringify({ name: "fixture", version: "1.0.0", author: "x" })
	);
	await writeFile(join(src, "index.adoc"), "= Custom Dirs\n\nhello\n");

	const result = await buildIn(dir, { srcDir: "documentation", outDir: "out" });

	equal(result.outDir, join(dir, "out"));
	const html = await readFile(join(dir, "out", "index.html"), "utf8");
	match(html, /Custom Dirs/, "expected the doc from the configured srcDir");
});
