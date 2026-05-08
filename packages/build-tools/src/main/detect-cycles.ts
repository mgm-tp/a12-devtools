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

import { lstatSync, readFileSync } from "node:fs";
import { dirname, join, parse } from "node:path";

import { sync } from "glob";
import type { ExportDeclaration, ImportDeclaration, Node } from "typescript";
import {
	createSourceFile,
	forEachChild,
	identifierToKeywordKind,
	isCallExpression,
	isExportDeclaration,
	isIdentifier,
	isImportDeclaration,
	isNamedExports,
	isNamedImports,
	isStringTextContainingNode,
	isTypeOnlyExportDeclaration,
	isTypeOnlyImportDeclaration,
	ScriptTarget,
	SyntaxKind
} from "typescript";

import type { Command } from "./interfaces.js";

/**
 * Command definition for the CLI. This command will detect cycles inside of
 * Typescript compatible files. Therefore, a glob has to be provided.
 */
export const detectCycles: Command = {
	name: "detect-cycles <files> [collapse-pattern]",
	options: [
		[
			"-i, --ignore-type-imports",
			"Ignore any cycles caused by type-only imports"
		]
	],
	description: `detects cycles in typescript imports

	files: glob expression of files (see https://www.npmjs.com/package/glob)

	collapse-pattern: A regex which must contain exactly one capturing group which defines the groups of files that should be collapsed.

	--ignore-type-imports: Cycles only caused by type-only imports will not be reported (as they have no influence on the runtime)
	-s, --silent: reduce console output 
`,
	action: (typescriptFileGlob: string, collapsePattern: string, opts = {}) => {
		const { silent, ignoreTypeImports } = opts as Record<
			string,
			boolean | undefined
		>;

		const files = sync(typescriptFileGlob);

		let g = getLocalDependencyGraphFromFiles(
			files,
			!!ignoreTypeImports,
			!!silent
		);
		if (collapsePattern) {
			g = Graph.collapse(g, collapsePattern);
		}
		const cycles = Graph.findCycles(g);

		for (const cycle of cycles) {
			console.error([cycle[cycle.length - 1], ...cycle].join("\n  ↦ "));
		}

		if (!silent || cycles.length > 0) {
			console.log("Cycle Count: " + cycles.length);
		}
		process.exit(cycles.length);
	}
};

/**
 * Collects all local imports from a set of Typescript compatible files and
 * produces an import graph that states which files imports other files from the
 * set.
 *
 * Note: JSON file imports will be ignored.
 *
 * @param files a list of Typescript compatible files paths
 *
 * @return a map of Typescript compatible files paths and the referenced imports
 */
export function getLocalDependencyGraphFromFiles(
	files: ReadonlyArray<string>,
	ignoreTypeImport: boolean,
	silent = false
): Graph {
	let graph: Graph = {};

	for (const file of files) {
		if (!lstatSync(file).isFile()) {
			continue;
		}
		const contextPath = dirname(file);

		const referencedFiles: string[] = [];
		for (const rawImportPath of collectAllImportsFromFile(
			file,
			ignoreTypeImport
		)) {
			const importPath = stripQuerySuffix(rawImportPath);
			if (
				!importPath.startsWith(".") ||
				hasIgnoredFileExtension(importPath) ||
				hasQuerySuffix(rawImportPath)
			) {
				// It is not a local import if it does not start with "."
				// Non-JS assets do not contain any references. Therefore, they can be ignored
				// Imports with query suffixes (e.g. ?raw) are bundler-specific and not real module dependencies
				continue;
			}

			// esm imports contain ".js" at the end
			const resolvedImport = join(contextPath, importPath);
			const importMatch = filePathWithoutExtension(resolvedImport);

			// Match file with import statement
			const fileMatch = files.find(f => {
				const fileWithoutExtension = filePathWithoutExtension(f);
				return (
					importMatch === fileWithoutExtension ||
					// support directory imports in CommonJS
					join(resolvedImport, "index") === fileWithoutExtension
				);
			});

			if (fileMatch === undefined) {
				if (!silent) {
					console.warn(
						`Cannot resolve the file for the import "${importPath}" of "${file}".`
					);
				}
				continue;
			}

			referencedFiles.push(fileMatch);
		}

		graph = { ...graph, [file]: referencedFiles };
	}

	return graph;
}

/**
 * Removes the suffix (e.g. ".ts") from a given path.
 *
 * *Example*
 *
 * * `"my/path/to/a/typescript"` -> `"my/path/to/a/typescript"`
 * * `"my/path/to/a/typescript.ts"` -> `"my/path/to/a/typescript"`
 * * `"my/path/to/a/typescript.d.ts"` -> `"my/path/to/a/typescript"`
 *
 */
export function filePathWithoutExtension(path: string): string {
	const { dir, name } = parse(path);
	return join(
		dir,
		name.endsWith(".d") ? name.substring(0, name.length - 2) : name
	);
}

/**
 * Checks whether the import path has a query suffix (e.g. `?raw`, `?url`).
 * These are bundler-specific and indicate the import is not a real module dependency.
 */
export function hasQuerySuffix(path: string): boolean {
	return path.includes("?");
}

/**
 * Strips query suffixes (e.g. `?raw`, `?url`) from import paths.
 * These are bundler-specific and not part of the actual module path.
 */
export function stripQuerySuffix(path: string): string {
	const index = path.indexOf("?");
	return index >= 0 ? path.substring(0, index) : path;
}

/**
 * Checks whether the import has a file extension, that should be ignored,
 * because it cannot lead to cycles.
 */
export function hasIgnoredFileExtension(path: string): boolean {
	return [
		".json",
		".svg",
		".css",
		".md",
		".png",
		".jpg",
		".gif",
		".woff",
		".woff2",
		".ttf",
		".eot"
	].some(ext => path.endsWith(ext));
}

/**
 * Returns a list of local imports from a Typescript compatible files
 *
 * @param Typescript compatible files path
 *
 * @return list of import paths
 */
export function collectAllImportsFromFile(
	file: string,
	ignoreTypeImport: boolean
): ReadonlyArray<string> {
	const sourceFile = createSourceFile(
		file,
		readFileSync(file, { encoding: "utf-8" }),
		ScriptTarget.ES2024,
		true
	);

	const matches: string[] = [];

	function visiter(node: Node): void {
		if (
			// import * as X from "...";
			(isImportDeclaration(node) &&
				(!isTypeOnlyImport(node) || !ignoreTypeImport)) ||
			// export * from "...";
			(isExportDeclaration(node) &&
				(!isTypeOnlyExport(node) || !ignoreTypeImport))
		) {
			if (
				node.moduleSpecifier !== undefined &&
				isStringTextContainingNode(node.moduleSpecifier)
			) {
				matches.push(node.moduleSpecifier.text);
			}
		} else if (
			// const X = require("..."); or const X = import("...");
			isCallExpression(node)
		) {
			const [argument] = node.arguments;
			if (
				argument !== undefined &&
				isStringTextContainingNode(argument) &&
				// const X = import("...");
				(node.expression.kind === SyntaxKind.ImportKeyword ||
					(isIdentifier(node.expression) &&
						identifierToKeywordKind(node.expression) ===
							SyntaxKind.ImportKeyword) ||
					// const X = require("...");
					node.expression.kind === SyntaxKind.RequireKeyword ||
					(isIdentifier(node.expression) &&
						identifierToKeywordKind(node.expression) ===
							SyntaxKind.RequireKeyword))
			) {
				matches.push(argument.text);
			}
		}

		forEachChild(node, visiter);
	}

	forEachChild(sourceFile, visiter);

	return matches;
}

/**
 * Either the whole declaration is type-only or
 * it contains only type-only specifiers
 */
export function isTypeOnlyImport(node: ImportDeclaration) {
	return (
		node.importClause &&
		(isTypeOnlyImportDeclaration(node.importClause) ||
			(node.importClause.namedBindings &&
				isNamedImports(node.importClause.namedBindings) &&
				node.importClause.namedBindings.elements.every(
					isTypeOnlyImportDeclaration
				)))
	);
}

/**
 * Either the whole declaration is type-only or
 * it contains only type-only specifiers
 */
export function isTypeOnlyExport(node: ExportDeclaration) {
	return (
		isTypeOnlyExportDeclaration(node) ||
		(node.exportClause &&
			(isTypeOnlyExportDeclaration(node.exportClause) ||
				(isNamedExports(node.exportClause) &&
					node.exportClause.elements.every(isTypeOnlyExportDeclaration))))
	);
}

/**
 * A map from identifier (node) to others of the map. Thereby, the link is
 * a directed edge in graph theory.
 *
 * **Example**
 * ```
 * {
 *   "A": ["B", "C"],
 *   "B": ["C"],
 *   "C": []
 * }
 * ```
 * The directed edges are: A -> B, A -> C, B -> C
 *
 */
export interface Graph {
	readonly [node: string]: readonly string[] | undefined;
}

export namespace Graph {
	/** Is a path inside a {@link Graph} */
	export type NodePath = readonly string[];

	/**
	 * Finds all paths in a given directed graph that are cycles
	 */
	export function findCycles(graph: Graph): readonly NodePath[] {
		let result: readonly NodePath[] = [];

		// Keep track of visited edges from a node (key fo the object) to others (value list of a key)
		let visitedState: Graph = {};

		/**
		 * This algorithm assumes that every node is a potential starting node.
		 * In case that the node was already visited it will be ignored.
		 * This ensures that every node is visited in the end.
		 */
		for (const startNode of Object.keys(graph)) {
			const startNextNodes = graph[startNode];
			if (startNextNodes === undefined) {
				// Node does not contain any outgoing edges
				continue;
			}

			for (
				// The algorithm works on edges therefore the node path has to contain always two nodes (one edge)
				let [path, ...stack]: readonly NodePath[] = startNextNodes.map(
					nextNode => [startNode, nextNode]
				);
				path !== undefined;
				[path, ...stack] = stack
			) {
				// Get last added edge
				const [previousNode, node] = [
					path[path.length - 2],
					path[path.length - 1]
				];
				if (previousNode === undefined) {
					throw new Error(`Internal error: previous node is undefined!`);
				}

				const visitedNodes = visitedState[previousNode] ?? [];
				if (visitedNodes.some(visitedNode => visitedNode === node)) {
					// Go on if we already analysed this edge
					continue;
				}
				visitedState = {
					...visitedState,
					[previousNode]: [...visitedNodes, node]
				};

				// Check next edge
				for (const nextNode of graph[node] ?? []) {
					const index = path.indexOf(nextNode);
					if (index < 0) {
						// Next node is not already in the path
						stack = [[...path, nextNode], ...stack];
					} else {
						const cyclePath = path.slice(index);
						if (result.every(x => !isEqualCycle(x, cyclePath))) {
							// Add cycle if it is not a duplicate
							result = [...result, cyclePath];
						}
					}
				}
			}
		}
		return result;
	}

	/** Compares two cycles if they are equal */
	function isEqualCycle(c1: NodePath, c2: NodePath): boolean {
		if (c1.length !== c2.length) {
			return false;
		}

		// Normalize c2 to have the same start node as c1
		const index = c2.findIndex(node => node === c1[0]);
		if (index < 0) {
			return false;
		}
		const normalizedC2 = [...c2.slice(index), ...c2.slice(0, index)];

		for (let i = 0; i < c1.length; i++) {
			if (c1[i] !== normalizedC2[i]) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Collapse the nodes in the given graph based on the given regular expression.
	 *
	 * Only the nodes that match are collapsed and the name of the group is
	 * defined by the capture group in the regex (it must contain exactly one).
	 *
	 * See matchContractGroups for how the pattern is used in detail.
	 */
	export function collapse(g: Graph, pattern: string): Graph {
		const contractGroups = matchCollapsingGroups(Object.keys(g), pattern);
		let work = g;
		for (const [group, nodesInGroup] of Object.entries(contractGroups)) {
			work = contract(work, nodesInGroup ?? [], group);
		}
		return work;
	}

	/**
	 * Compute groups of nodes that should be collapsed. The result is a graph
	 * <group> -> <nodes in group>.
	 *
	 * The groups are computed based on a regular expression that must contain
	 * exactly one capture group that defines the group.
	 *
	 * Now for each node in the original graph, if and only if the node matches
	 * the regex -> add it to the group defined by the capture group.
	 *
	 * Only exported for testing
	 */
	export function matchCollapsingGroups(
		candidates: readonly string[],
		pattern: string
	): Graph {
		let groups: Graph = {};
		const regex = new RegExp(pattern);
		for (const node of candidates) {
			const match = regex.exec(node);
			if (match !== null) {
				if (match.length === 2) {
					const group = match[1];
					groups = addEdge(groups, group, node);
				} else {
					throw new Error("pattern must contain exactly one group");
				}
			}
		}
		return groups;
	}

	/**
	 * In the given graph, contract the given nodes to a single new node of the
	 * given name.
	 *
	 * If you are interested in the graph theory of this, please have a look at
	 * https://en.wikipedia.org/wiki/Edge_contraction#Vertex_identification
	 *
	 * Please be aware that Graph is an edge list representation of a directed
	 * graph and therefore both nodes and edges are represented by node names
	 * and therefore both are of type string. See JsDoc for Graph.
	 */
	export function contract(
		g: Graph,
		nodesToContract: readonly string[],
		contractTo: string
	): Graph {
		if (nodesToContract.length === 0) {
			return g;
		}

		// contract all edges from the nodes to contract into a single node
		const contractedNodes = {
			[contractTo]: contractEdges(contractTo, edgesOfNodesToContract())
		};

		// filter graph of remaining nodes NOT to contract
		// but we must contract the edges here as well
		const nonContractedNodes = Object.fromEntries(
			Object.entries(g)
				.filter(([node]) => !nodesToContract.includes(node))
				.map(([node, edges]) => [node, contractEdges(node, edges)])
		);

		// re-assemble graph = new, contracted node + remaining graph
		return {
			...nonContractedNodes,
			...contractedNodes
		};

		// 1 rename all edges nodesToContract -> contractTo
		// 2 dedupe
		// 3 remove loops (self references)
		function contractEdges(
			node: string,
			edges: readonly string[] = []
		): readonly string[] {
			return removeLoops(node, dedupe(renameContractedEdges(edges)));
		}

		// collect all edges of all nodes that should be contracted
		function edgesOfNodesToContract(): string[] {
			return nodesToContract.flatMap(n => g[n] ?? []);
		}

		// rename edges that point to nodes to be contracted
		function renameContractedEdges(edges: readonly string[]) {
			return edges.map(e => (nodesToContract.includes(e) ? contractTo : e));
		}

		// remove duplicates from edge list
		function dedupe(edges: readonly string[]): readonly string[] {
			return [...new Set(edges)];
		}

		// remove loop edges for the given node, i.e. edges that point back to the node
		function removeLoops(node: string, edges: readonly string[]) {
			return edges.filter(e => e !== node);
		}
	}

	export function addEdge(g: Graph, node: string, edge: string): Graph {
		return {
			...g,
			[node]: [...(g[node] ?? []), edge]
		};
	}
}
