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

import { join } from "node:path";
import process from "node:process";

import { ConventionalChangelog, runProgram } from "conventional-changelog";

import { changelogReadme } from "./changelog-readme.js";
import type { Command } from "./interfaces.js";

export const changelog: Command = {
	name: "changelog",
	description:
		"Generates/updates CHANGELOG.md from conventional commit messages using the shared a12 preset",
	options: [
		["-i, --infile <file>", "changelog file to read/write"],
		["-r, --release-count <n>", "number of releases to regenerate, 0 for all"]
	],
	async action(options: { infile?: string; releaseCount?: string }) {
		const presetPath = join(import.meta.dirname, "changelog-preset.js");

		await runProgram(new ConventionalChangelog(process.cwd()), {
			infile: options.infile ?? "CHANGELOG.md",
			config: presetPath,
			releaseCount: options.releaseCount ? Number(options.releaseCount) : 1
		});
	},
	subCommands: [changelogReadme]
};
