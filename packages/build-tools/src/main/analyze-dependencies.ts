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

import { exit } from "node:process";

import * as Glob from "glob";

import { getLocalDependencyGraphFromFiles, Graph } from "./detect-cycles.js";
import type { Command } from "./interfaces.js";

/**
 * Basically the same as detect-cycles, but output the dependency graph for
 * visualization, e.g. using yEd.
 *
 * The output format is "adjacency matrix in CSV".
 */
export const analyzeDependencies: Command = {
	name: "analyze-dependencies <files> [collapse-pattern]",
	description: "analyzes typescript import dependencies",
	action(typescriptFileGlob: string, collapsePattern: string) {
		const files = Glob.sync(typescriptFileGlob);

		// when collapsePattern is given, consider all cycles (including type-only)
		let g = getLocalDependencyGraphFromFiles(files, !collapsePattern);
		if (collapsePattern) {
			g = Graph.collapse(g, collapsePattern);
		}
		console.log(GraphCsv.toString(GraphCsv.toAdjacencyMatrix(g)));
		exit(0);
	}
};

export const GraphCsv = {
	toAdjacencyMatrix,
	toString
};

function toAdjacencyMatrix(g: Graph): readonly string[][] {
	return [
		["", ...Object.keys(g)],
		...Object.keys(g).map(row => [
			row,
			...Object.keys(g).map(col => (g[row]?.includes(col) ? "1" : "0"))
		])
	];
}

function toString(csv: readonly string[][]): string {
	return csv.map(row => row.map(s => `"${s}"`).join(",")).join("\n");
}
