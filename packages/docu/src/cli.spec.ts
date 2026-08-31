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

import { match } from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const run = promisify(execFile);
// compiled test lives at lib/cli.spec.js → bin is ./cli.js (same dir)
const CLI = join(dirname(fileURLToPath(import.meta.url)), "cli.js");

test("docu bin converts index.adoc in its cwd", async () => {
	const dir = await mkdtemp(join(tmpdir(), "docu-cli-"));
	const src = join(dir, "src");
	await mkdir(src, { recursive: true });
	await writeFile(
		join(dir, "package.json"),
		JSON.stringify({ name: "fx", version: "1.0.0", author: "t@a12" })
	);
	await writeFile(
		join(src, "docinfo.html"),
		`<link rel="stylesheet" href="highlight-theme.css">`
	);
	await writeFile(
		join(src, "index.adoc"),
		["= Fx", "", "[source,ts]", "----", "let n = 1;", "----", ""].join("\n")
	);

	const { stdout } = await run(process.execPath, [CLI], { cwd: dir });
	match(stdout, /docu: wrote/);

	const html = await readFile(join(dir, "build", "index.html"), "utf8");
	match(html, /highlightjs|highlight\.js|hljs/i);
});
