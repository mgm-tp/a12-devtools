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

import { deepStrictEqual, doesNotThrow } from "node:assert/strict";
import { join } from "node:path";
import { afterEach, describe, it, mock } from "node:test";

import {
	collectAllImportsFromFile,
	detectCycles as command,
	Graph,
	hasIgnoredFileExtension,
	hasQuerySuffix,
	stripQuerySuffix
} from "../main/detect-cycles.js";

describe("detect-cycles", () => {
	it("Graph.findCycle finds all cycles", () => {
		const graph = {
			1: ["2", "3", "5"],
			2: ["1"],
			3: ["1"],
			4: ["2"],
			5: ["2"]
		};
		deepStrictEqual(Graph.findCycles(graph), [
			["1", "2"],
			["1", "3"],
			["1", "5", "2"]
		]);
	});

	describe("collectAllImports", () => {
		const normalImportCount = 13;
		const typeOnlyImportCount = 16;

		it("finds all imports", () => {
			const file = join(
				import.meta.dirname,
				"..",
				"..",
				"resources",
				"detect-cycles",
				"files-with-all-kinds-of-import.ts"
			);
			deepStrictEqual(
				collectAllImportsFromFile(file, false),
				Array.from(Array(normalImportCount + typeOnlyImportCount)).map(
					(_, i) => `module${i + 1}`
				)
			);
		});

		it("does not find type-only imports when ignoreTypeImports=true", () => {
			const file = join(
				import.meta.dirname,
				"..",
				"..",
				"resources",
				"detect-cycles",
				"files-with-all-kinds-of-import.ts"
			);

			deepStrictEqual(
				collectAllImportsFromFile(file, true),
				Array.from(Array(normalImportCount)).map((_, i) => `module${i + 1}`)
			);
		});
	});

	describe("integration", () => {
		function stubConsoleAndExit() {
			const noop = () => {};
			const logStub = mock.method(console, "log", noop);
			const warnStub = mock.method(console, "warn", noop);
			const errorStub = mock.method(console, "error", noop);
			const processStub = mock.method(process, "exit", noop);

			return { logStub, warnStub, errorStub, processStub };
		}

		afterEach(() => mock.reset());

		["esm", "commonjs"].forEach(type => {
			describe(type, () => {
				describe("normal", () => {
					it("reports existing cycle", () => {
						const { processStub, errorStub, logStub, warnStub } =
							stubConsoleAndExit();

						doesNotThrow(() => {
							command.action(
								`./resources/detect-cycles/integration-test-files/${type}/normal/**/*.ts`
							);
						});

						// Return code is set correctly
						deepStrictEqual(processStub.mock.calls[0].arguments[0], 1);

						// Cycle was printed to stderr
						deepStrictEqual(
							errorStub.mock.calls[0].arguments[0],
							`resources/detect-cycles/integration-test-files/${type}/normal/module3/module4.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/normal/module2.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/normal/module3/index.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/normal/module3/module4.ts`
						);

						// Cycle count was printed to stdout
						deepStrictEqual(
							logStub.mock.calls[0].arguments[0],
							"Cycle Count: 1"
						);

						// No warnings
						deepStrictEqual(warnStub.mock.callCount(), 0);
					});

					it("does not report anything if no cycles exist", () => {
						const { processStub, errorStub, logStub } = stubConsoleAndExit();

						doesNotThrow(() => {
							command.action(
								`./resources/detect-cycles/integration-test-files/${type}/normal/module1.ts`
							);
						});

						// Return code is set correctly
						deepStrictEqual(processStub.mock.calls[0].arguments[0], 0);

						// No cycle was printed to stderr
						deepStrictEqual(errorStub.mock.callCount(), 0);

						// Cycle count was printed to stdout
						deepStrictEqual(
							logStub.mock.calls[0].arguments[0],
							"Cycle Count: 0"
						);
					});
				});

				describe("types", () => {
					it("reports existing cycle", () => {
						const { processStub, errorStub, logStub, warnStub } =
							stubConsoleAndExit();

						doesNotThrow(() => {
							command.action(
								`./resources/detect-cycles/integration-test-files/${type}/types/**/*.ts`
							);
						});

						// Return code is set correctly
						deepStrictEqual(processStub.mock.calls[0].arguments[0], 1);

						// Cycle was printed to stderr
						deepStrictEqual(
							errorStub.mock.calls[0].arguments[0],
							`resources/detect-cycles/integration-test-files/${type}/types/mod1.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/types/mod3.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/types/mod2.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/types/mod1.ts`
						);

						// Cycle count was printed to stdout
						deepStrictEqual(
							logStub.mock.calls[0].arguments[0],
							"Cycle Count: 1"
						);

						// No warnings
						deepStrictEqual(warnStub.mock.callCount(), 0);
					});

					it("does not reports cycle when ignoreTypeImports=true", () => {
						const { processStub, errorStub, logStub } = stubConsoleAndExit();

						doesNotThrow(() => {
							command.action(
								`./resources/detect-cycles/integration-test-files/${type}/types/**/*.ts`,
								undefined,
								{ ignoreTypeImports: true }
							);
						});

						// Return code is set correctly
						deepStrictEqual(processStub.mock.calls[0].arguments[0], 0);

						// No cycle was printed to stderr
						deepStrictEqual(errorStub.mock.callCount(), 0);

						// Cycle count was printed to stdout
						deepStrictEqual(
							logStub.mock.calls[0].arguments[0],
							"Cycle Count: 0"
						);
					});
				});

				describe("typings", () => {
					it("reports existing cycle", () => {
						const { processStub, errorStub, logStub, warnStub } =
							stubConsoleAndExit();

						doesNotThrow(() => {
							command.action(
								`./resources/detect-cycles/integration-test-files/${type}/typings/**/*`
							);
						});

						// Return code is set correctly
						deepStrictEqual(processStub.mock.calls[0].arguments[0], 1);

						// Cycle was printed to stderr
						deepStrictEqual(
							errorStub.mock.calls[0].arguments[0],
							`resources/detect-cycles/integration-test-files/${type}/typings/typings1.d.ts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/typings/typings3.d.mts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/typings/typings2.d.cts\n` +
								`  ↦ resources/detect-cycles/integration-test-files/${type}/typings/typings1.d.ts`
						);

						// Cycle count was printed to stdout
						deepStrictEqual(
							logStub.mock.calls[0].arguments[0],
							"Cycle Count: 1"
						);

						// No warnings
						deepStrictEqual(warnStub.mock.callCount(), 0);
					});
				});

				it("prints warning for missing files", () => {
					const { warnStub } = stubConsoleAndExit();

					doesNotThrow(() => {
						command.action(
							`./resources/detect-cycles/integration-test-files/${type}/normal/module1.ts`
						);
					});

					const fileExtension = type === "esm" ? ".js" : "";

					// A warning was printed due to missing files in glob
					deepStrictEqual(
						warnStub.mock.calls[0].arguments[0],
						`Cannot resolve the file for the import "./module2${fileExtension}" of` +
							` "resources/detect-cycles/integration-test-files/${type}/normal/module1.ts".`
					);
				});
			});
		});
	});

	describe("hasQuerySuffix", () => {
		it("detects ?raw suffix", () => {
			deepStrictEqual(hasQuerySuffix("./app.tsx?raw"), true);
		});

		it("detects ?url suffix", () => {
			deepStrictEqual(hasQuerySuffix("./image.png?url"), true);
		});

		it("returns false when no query suffix", () => {
			deepStrictEqual(hasQuerySuffix("./module.js"), false);
		});
	});

	describe("stripQuerySuffix", () => {
		it("strips ?raw suffix", () => {
			deepStrictEqual(stripQuerySuffix("./app.tsx?raw"), "./app.tsx");
		});

		it("strips ?url suffix", () => {
			deepStrictEqual(stripQuerySuffix("./image.png?url"), "./image.png");
		});

		it("returns path unchanged when no query suffix", () => {
			deepStrictEqual(stripQuerySuffix("./module.js"), "./module.js");
		});
	});

	describe("hasIgnoredFileExtension", () => {
		it("ignores .json", () => {
			deepStrictEqual(hasIgnoredFileExtension("./data.json"), true);
		});

		it("ignores .css", () => {
			deepStrictEqual(hasIgnoredFileExtension("./styles.css"), true);
		});

		it("ignores .md", () => {
			deepStrictEqual(hasIgnoredFileExtension("./readme.md"), true);
		});

		it("ignores .svg", () => {
			deepStrictEqual(hasIgnoredFileExtension("./icon.svg"), true);
		});

		it("does not ignore .ts", () => {
			deepStrictEqual(hasIgnoredFileExtension("./module.ts"), false);
		});

		it("does not ignore .tsx", () => {
			deepStrictEqual(hasIgnoredFileExtension("./component.tsx"), false);
		});

		it("does not ignore .js", () => {
			deepStrictEqual(hasIgnoredFileExtension("./module.js"), false);
		});
	});

	// test if groups to be collapsed are identified correctly
	describe("match collapsing groups", () => {
		// with an expression that matches two different folders,
		// the matching files are grouped into those two folders,
		// one file doesn't match any group and is ignored
		it("matches multiple groups", () => {
			const candidates = [
				"some/folder1/file1.ts",
				"some/folder1/file2.ts",
				"some/other/folder/file.ts",
				"some/folder1/file3.ts",
				"some/folder2/file1.ts",
				"some/folder2/file2.ts"
			];
			const expression = "^some/(folder1|folder2)/";
			const matchGroups = {
				folder1: [
					"some/folder1/file1.ts",
					"some/folder1/file2.ts",
					"some/folder1/file3.ts"
				],
				folder2: ["some/folder2/file1.ts", "some/folder2/file2.ts"]
			};
			const result = Graph.matchCollapsingGroups(candidates, expression);
			deepStrictEqual(result, matchGroups);
		});

		// if no file matches, no groups are created
		it("no match", () => {
			const candidates = [
				"some/other/folder/file.ts",
				"some/folder/file3.ts",
				"some/folder2/file1.ts",
				"some/folder2/file2.ts"
			];
			const expression = "^some/(folder1)/";
			const matchGroups = {};
			const result = Graph.matchCollapsingGroups(candidates, expression);
			deepStrictEqual(result, matchGroups);
		});

		// if no candidates are given, no groups are created
		it("no candidates", () => {
			const candidates: string[] = [];
			const expression = ".*";
			const matchGroups = {};
			const result = Graph.matchCollapsingGroups(candidates, expression);
			deepStrictEqual(result, matchGroups);
		});
	});

	// test node collapsing algorithm
	describe("collapse nodes", () => {
		// test what happens with nodes
		describe("contract nodes", () => {
			it("contracts given nodes, ignore others", () => {
				const graph: Graph = {
					a1: [],
					a2: [],
					b1: [],
					b2: []
				};
				const nodesToContract = ["a1", "a2"];
				const contractTo = "a";
				const contractedGraph: Graph = {
					a: [],
					b1: [],
					b2: []
				};
				deepStrictEqual(
					Graph.contract(graph, nodesToContract, contractTo),
					contractedGraph
				);
			});
			it("edge case: contract all nodes", () => {
				const graph: Graph = {
					a1: [],
					a2: [],
					a3: []
				};
				const nodesToContract = Object.keys(graph);
				const contractTo = "a";
				const contractedGraph: Graph = {
					a: []
				};
				deepStrictEqual(
					Graph.contract(graph, nodesToContract, contractTo),
					contractedGraph
				);
			});
			it("edge case: contract no node", () => {
				const graph: Graph = {
					a1: [],
					a2: []
				};
				const nodesToContract: string[] = [];
				const contractTo = "a";
				const contractedGraph = graph;
				deepStrictEqual(
					Graph.contract(graph, nodesToContract, contractTo),
					contractedGraph
				);
			});
			it("removes loops", () => {
				const graph: Graph = {
					a1: ["a2"],
					a2: ["a1"]
				};
				const contractedGraph: Graph = {
					a: []
				};
				deepStrictEqual(
					Graph.contract(graph, ["a1", "a2"], "a"),
					contractedGraph
				);
			});
		});

		// test what happens with edges
		describe("contract edges", () => {
			it("pointing to contracted nodes", () => {
				const graph: Graph = {
					a1: [],
					a2: [],
					b1: ["a1"],
					b2: ["a2"]
				};
				const nodesToContract = ["a1", "a2"];
				const contractTo = "a";
				const contractedGraph: Graph = {
					a: [],
					b1: ["a"],
					b2: ["a"]
				};
				deepStrictEqual(
					Graph.contract(graph, nodesToContract, contractTo),
					contractedGraph
				);
			});
		});
	});
});
