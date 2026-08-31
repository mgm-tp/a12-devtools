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

import { join, resolve } from "node:path";
import { cwd } from "node:process";
import { pathToFileURL } from "node:url";

import { convertFile } from "asciidoctor";

import { buildAttributes } from "./buildAttributes.js";
import { copyAssets } from "./copyAssets.js";
import type { DocuConfig, DocuPackageJson, DocuResult } from "./interfaces.js";

/**
 * Builds the documentation rooted at the current working directory: converts
 * `<srcDir>/index.adoc` to `<outDir>/index.html` with asciidoctor, then copies
 * `<srcDir>/assets`. `srcDir`/`outDir` default to `src`/`build`; `revnumber` and
 * `author` are read from the local `package.json`.
 */
export async function buildDocs(config?: DocuConfig): Promise<DocuResult> {
	const root = cwd();

	const srcDir = resolve(root, config?.srcDir ?? "src");
	const outDir = resolve(root, config?.outDir ?? "build");

	const { default: pkg } = (await import(
		pathToFileURL(join(root, "package.json")).href,
		{ with: { type: "json" } }
	)) as { default: DocuPackageJson };

	const attributes = buildAttributes(
		{
			revnumber: pkg.version ?? "",
			author: pkg.author ?? "",
			docinfodir: srcDir
		},
		config?.attributes
	);

	await convertFile(join(srcDir, "index.adoc"), {
		to_dir: outDir,
		mkdirs: true,
		safe: "unsafe",
		attributes
	});

	await copyAssets(srcDir, outDir);

	return { outDir };
}
