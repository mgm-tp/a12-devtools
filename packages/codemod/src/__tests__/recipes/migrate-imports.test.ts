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

import { ModuleKind, Project, ScriptTarget } from "ts-morph";
import { beforeEach, describe, expect, it } from "vitest";

import {
	migrateImports,
	type ImportMigrationConfiguration,
	type NormalizedEntityMigrationConfiguration,
	type SourceEntityMigrationConfiguration,
	type TargetEntityMigrationConfiguration
} from "../../internal/recipes/index.js";
import { generateUid } from "../../internal/test-recipe.js";

function cartesianProduct<T>(...groups: Partial<T>[][]): T[] {
	let results: Partial<T>[] = [{}];

	for (const group of groups) {
		const nextResult: Partial<T>[] = [];

		for (const base of results) {
			for (const patch of group) {
				nextResult.push({ ...base, ...patch });
			}
		}

		results = nextResult;
	}

	return results as T[];
}

describe("migrateImports", () => {
	let project: Project;

	beforeEach(() => {
		project = new Project({
			useInMemoryFileSystem: true,
			compilerOptions: {
				target: ScriptTarget.ESNext,
				module: ModuleKind.ESNext
			}
		});
	});

	function testMigrateImports(
		sourceText: string,
		config: ImportMigrationConfiguration,
		tsx = false,
		extension?: string
	): string {
		const ext = extension ?? (tsx ? "tsx" : "ts");
		const sourceFile = project.createSourceFile(
			`test-${generateUid()}.${ext}`,
			sourceText
		);
		migrateImports(sourceFile, config);

		return sourceFile.getText();
	}

	function createSnapshotName(
		code: string,
		from: SourceEntityMigrationConfiguration,
		to: TargetEntityMigrationConfiguration
	): string {
		const config = `
package: ${[from.packageName, from.subPath].filter(Boolean).join("/")} -> ${[to.packageName, to.subPath].filter(Boolean).join("/")}
entity: ${[...(from.namespaces ?? []), from.entity ?? "*"].join(".")} -> ${[...(to.namespaces ?? []), to.entity ?? "*"].join(".")}
`;

		return `\n=== Source ===\n${code}\n=== Config ===${config}`;
	}

	it("should migrate imports according to the provided instructions", () => {
		const migrationSources =
			cartesianProduct<NormalizedEntityMigrationConfiguration>(
				[
					{ packageName: "legacy-package" },
					{ packageName: "@legacy-namespace/legacy-package" }
				],
				[
					{ subPath: "" },
					{ subPath: "sub-path" },
					{ subPath: "nested/sub-path" }
				],
				[{ entity: undefined }, { entity: "sum" }],
				[{ namespaces: [] }]
			);

		const migrationTargets =
			cartesianProduct<NormalizedEntityMigrationConfiguration>(
				[
					{ packageName: "new-package" },
					{ packageName: "@new-namespace/new-package" }
				],
				[
					{ subPath: undefined },
					{ subPath: "" },
					{ subPath: "new-sub-path" },
					{ subPath: "new-nested/new-sub-path" }
				],
				[{ entity: undefined }, { entity: "newSum" }],
				[{ namespaces: [] }, { namespaces: ["NewOuterNamespace"] }]
			);

		migrationSources.forEach(from => {
			migrationTargets.forEach(to => {
				const code = `
import { sum, product, OuterNamespace } from "${from.subPath ? `${from.packageName}/${from.subPath}` : from.packageName}";
sum(1, 2);
product(3, 4);
`;

				try {
					const result = testMigrateImports(code, {
						entityMigrations: [{ from, to }]
					});
					expect(result).toMatchSnapshot(createSnapshotName(code, from, to));
				} catch (e) {
					expect((e as Error).message).matchSnapshot(
						createSnapshotName(code, from, to)
					);
				}
			});
		});
	});

	it("nested namespace", () => {
		const migrationSources =
			cartesianProduct<NormalizedEntityMigrationConfiguration>(
				[{ packageName: "legacy-package" }],
				[{ subPath: "sub-path" }],
				[{ entity: undefined }, { entity: "sum" }],
				[
					{ namespaces: ["OuterNamespace"] },
					{ namespaces: ["OuterNamespace", "InnerNamespace"] }
				]
			);

		const migrationTargets =
			cartesianProduct<NormalizedEntityMigrationConfiguration>(
				[{ packageName: "new-package" }],
				[{ subPath: "" }],
				[{ entity: undefined }, { entity: "newSum" }],
				[
					{ namespaces: ["NewOuterNamespace"] },
					{ namespaces: ["NewOuterNamespace", "NewInnerNamespace"] }
				]
			);

		migrationSources.forEach(from => {
			migrationTargets.forEach(to => {
				const code = `
import { sum, product, OuterNamespace } from "${from.subPath ? `${from.packageName}/${from.subPath}` : from.packageName}";
sum(1, 2);
product(3, 4);
OuterNamespace.sum(1,2);
OuterNamespace.product(1,2);
OuterNamespace.InnerNamespace.sum(1,2);
OuterNamespace.InnerNamespace.product(1,2);
`;

				try {
					const result = testMigrateImports(code, {
						entityMigrations: [{ from, to }]
					});
					expect(result).toMatchSnapshot(createSnapshotName(code, from, to));
				} catch (e) {
					expect((e as Error).message).matchSnapshot(
						createSnapshotName(code, from, to)
					);
				}
			});
		});
	});

	describe("package name validation", () => {
		it("should accept valid scoped packages", () => {
			const code = `import { sum } from "@scope/package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/package" },
							to: { packageName: "@new-scope/new-package" }
						}
					]
				});
			}).not.toThrow();
		});

		it("should accept valid non-scoped packages", () => {
			const code = `import { sum } from "legacy-package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "legacy-package" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).not.toThrow();
		});

		it("should reject scoped packages with multiple slashes", () => {
			const code = `import { sum } from "@scope/package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/package/extra" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).toThrow(/scoped packages must have exactly one slash/);
		});

		it("should reject scoped packages without a slash", () => {
			const code = `import { sum } from "@scope";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).toThrow(/scoped packages must have exactly one slash/);
		});

		it("should reject non-scoped packages with slashes", () => {
			const code = `import { sum } from "legacy";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "legacy/package" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).toThrow(/non-scoped packages cannot contain slashes/);
		});

		it("should reject scoped packages ending with a slash", () => {
			const code = `import { sum } from "@scope/package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).toThrow(/cannot end with a slash/);
		});

		it("should reject scoped packages with empty scope name", () => {
			const code = `import { sum } from "@/package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@/package" },
							to: { packageName: "new-package" }
						}
					]
				});
			}).toThrow(/must have a scope name after @/);
		});

		it("should validate target package names as well", () => {
			const code = `import { sum } from "legacy-package";`;

			expect(() => {
				testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "legacy-package" },
							to: { packageName: "new/package" }
						}
					]
				});
			}).toThrow(/non-scoped packages cannot contain slashes/);
		});
	});

	describe("pathRewrites", () => {
		it("should rewrite simple path", () => {
			const code = `import { sum } from "old-package";`;
			const result = testMigrateImports(code, {
				pathMigrations: [{ from: "old-package", to: "new-package" }]
			});
			expect(result).toBe(`import { sum } from "new-package";`);
		});

		it("should rewrite path with single wildcard (*)", () => {
			const code = `import { sum } from "@scope/old-package/lib/utils";`;
			const result = testMigrateImports(code, {
				pathMigrations: [
					{ from: "@scope/old-package/lib/*", to: "@scope/new-package/dist/$1" }
				]
			});
			expect(result).toBe(
				`import { sum } from "@scope/new-package/dist/utils";`
			);
		});

		it("should rewrite path with double wildcard (**)", () => {
			const code = `import { sum } from "@scope/old-package/lib/deep/nested/module";`;
			const result = testMigrateImports(code, {
				pathMigrations: [
					{ from: "@scope/old-package/**", to: "@scope/new-package/$1" }
				]
			});
			expect(result).toBe(
				`import { sum } from "@scope/new-package/lib/deep/nested/module";`
			);
		});

		it("should respect exclude patterns", () => {
			const code1 = `import { sum } from "@scope/old-package/lib/utils";`;
			const code2 = `import { internal } from "@scope/old-package/lib/internal";`;

			const config: ImportMigrationConfiguration = {
				pathMigrations: [
					{
						from: "@scope/old-package/lib/*",
						to: "@scope/new-package/dist/$1",
						exclude: "@scope/old-package/lib/internal"
					}
				]
			};

			expect(testMigrateImports(code1, config)).toBe(
				`import { sum } from "@scope/new-package/dist/utils";`
			);
			expect(testMigrateImports(code2, config)).toBe(
				`import { internal } from "@scope/old-package/lib/internal";`
			);
		});

		it("should respect exclude patterns as array", () => {
			const codeUtils = `import { sum } from "@scope/old-package/lib/utils";`;
			const codeInternal = `import { internal } from "@scope/old-package/lib/internal";`;
			const codePrivate = `import { priv } from "@scope/old-package/lib/private";`;

			const config: ImportMigrationConfiguration = {
				pathMigrations: [
					{
						from: "@scope/old-package/lib/*",
						to: "@scope/new-package/dist/$1",
						exclude: [
							"@scope/old-package/lib/internal",
							"@scope/old-package/lib/private"
						]
					}
				]
			};

			expect(testMigrateImports(codeUtils, config)).toBe(
				`import { sum } from "@scope/new-package/dist/utils";`
			);
			expect(testMigrateImports(codeInternal, config)).toBe(
				`import { internal } from "@scope/old-package/lib/internal";`
			);
			expect(testMigrateImports(codePrivate, config)).toBe(
				`import { priv } from "@scope/old-package/lib/private";`
			);
		});

		it("should keep aliases when rewriting paths", () => {
			const code = `import { sum as mySum } from "old-package";\nmySum(1, 2);`;
			const result = testMigrateImports(code, {
				pathMigrations: [{ from: "old-package", to: "new-package" }]
			});
			expect(result).toBe(
				`import { sum as mySum } from "new-package";\nmySum(1, 2);`
			);
		});

		it("should support glob patterns in exclude", () => {
			const codeUtils = `import { sum } from "@scope/old-package/lib/utils";`;
			const codeInternalFoo = `import { foo } from "@scope/old-package/lib/internal/foo";`;

			const config: ImportMigrationConfiguration = {
				pathMigrations: [
					{
						from: "@scope/old-package/**",
						to: "@scope/new-package/$1",
						exclude: "@scope/old-package/lib/internal/**"
					}
				]
			};

			expect(testMigrateImports(codeUtils, config)).toBe(
				`import { sum } from "@scope/new-package/lib/utils";`
			);
			expect(testMigrateImports(codeInternalFoo, config)).toBe(
				`import { foo } from "@scope/old-package/lib/internal/foo";`
			);
		});

		it("should apply first matching rule", () => {
			const code = `import { sum } from "@scope/old-package/lib/utils";`;
			const result = testMigrateImports(code, {
				pathMigrations: [
					{
						from: "@scope/old-package/lib/utils",
						to: "@scope/special-package/utils"
					},
					{ from: "@scope/old-package/**", to: "@scope/new-package/$1" }
				]
			});
			expect(result).toBe(
				`import { sum } from "@scope/special-package/utils";`
			);
		});
	});

	describe("edge cases", () => {
		it("edge case 1", () => {
			const code = `
import { type Result } from "@scope/old-package/lib/utils";
import { Result as OtherResult } from "other-package/utils";

`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/old-package",
							entity: "Result",
							subPath: "lib/utils"
						},
						to: {
							packageName: "@scope/new-package",
							entity: "Result",
							subPath: "",
							namespaces: ["NS"]
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { NS } from "@scope/new-package";
				import { Result as OtherResult } from "other-package/utils";
			`);
		});

		it("edge case 2", () => {
			const code = `
import {
type Column,
} from "@scope/old-package/lib/utils";
import {
LayoutGrid
} from "widgets";

const Comp = (props) => {
return <LayoutGrid.Grid >
<LayoutGrid.Row>
<LayoutGrid.Column >
<CandidateTable {...props} />
</LayoutGrid.Column>
</LayoutGrid.Row>
</LayoutGrid.Grid>
}
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								entity: "Column",
								subPath: "lib/utils"
							},
							to: {
								packageName: "@scope/new-package",
								entity: "Column",
								subPath: "",
								namespaces: ["NS"]
							}
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(`
				import { NS } from "@scope/new-package";
				import {
				LayoutGrid
				} from "widgets";

				const Comp = (props) => {
				return <LayoutGrid.Grid >
				<LayoutGrid.Row>
				<LayoutGrid.Column >
				<CandidateTable {...props} />
				</LayoutGrid.Column>
				</LayoutGrid.Row>
				</LayoutGrid.Grid>
				}
			`);
		});

		it("edge case 3", () => {
			const code = `
import {
type OverviewModel,
ReferenceColumn
} from "@scope/old-package/lib/utils";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								entity: "OverviewModel",
								subPath: "lib/utils"
							},
							to: { packageName: "@scope/new-package", subPath: "" }
						},
						{
							from: {
								packageName: "@scope/old-package",
								entity: "ReferenceColumn",
								subPath: "lib/utils"
							},
							to: {
								packageName: "@scope/new-package",
								subPath: "",
								namespaces: ["OverviewModel"]
							}
						}
					]
				},
				true
			);
			// ReferenceColumn should become OverviewModel.ReferenceColumn, so only one import of OverviewModel is needed
			expect(result).toMatchInlineSnapshot(
				`import { OverviewModel } from "@scope/new-package";`
			);
		});

		it("edge case 4", () => {
			const code = `
import { sum1 } from "@scope/old-package/lib/utils";
import { sum2 } from "@scope/old-package/lib/utils";
import { sum4 } from "@other-package";
import { sum3 } from "@scope/old-package/lib/utils";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								entity: "sum1",
								subPath: "lib/utils"
							},
							to: { packageName: "@scope/new-package", subPath: "" }
						},
						{
							from: {
								packageName: "@scope/old-package",
								entity: "sum2",
								subPath: "lib/utils"
							},
							to: { packageName: "@scope/new-package", subPath: "" }
						},
						{
							from: {
								packageName: "@scope/old-package",
								entity: "sum3",
								subPath: "lib/utils"
							},
							to: { packageName: "@scope/new-package", subPath: "" }
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(`
				import { sum1, sum2, sum3 } from "@scope/new-package";
				import { sum4 } from "@other-package";
			`);
		});

		it("edge case 4", () => {
			const code = `
import { sum1 } from "@scope/old-package/lib/utils/sum1.js";
import { sum2 } from "@scope/old-package/lib/utils/sum2.js";
import { sum4 } from "@other-package";
import { sum3 } from "@scope/old-package/lib/utils/sum3.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@scope/old-package/lib/**/*.js",
							to: "@scope/new-package"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(`
				import { sum1, sum2, sum3 } from "@scope/new-package";
				import { sum4 } from "@other-package";
			`);
		});

		it("edge case 5", () => {
			const code = `
import { sum as sum1 } from "@scope/old-package/lib/utils/sum.js";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								subPath: "lib/utils/sum.js",
								entity: "sum"
							},
							to: { packageName: "@scope/new-package", subPath: "" }
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { sum as sum1 } from "@scope/new-package";`
			);
		});

		it("edge case 6", () => {
			const code = `
import { sum as sum1 } from "@scope/old-package/lib/utils/sum.js";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								subPath: "lib/utils/sum.js",
								entity: "sum"
							},
							to: {
								packageName: "@scope/new-package",
								subPath: "",
								entity: "newSum"
							}
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { newSum as sum1 } from "@scope/new-package";`
			);
		});

		it("edge case 7", () => {
			const code = `
import { product } from "@scope/old-package/lib/utils/sum.js";
import { sum as sum1 } from "@scope/old-package/lib/utils/sum.js";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								subPath: "lib/utils/sum.js",
								entity: "sum"
							},
							to: {
								packageName: "@scope/new-package",
								subPath: "",
								entity: "newSum"
							}
						}
					],
					pathMigrations: [
						{
							from: "@scope/old-package/**/*.js",
							to: "@scope/new-package"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { product, newSum as sum1 } from "@scope/new-package";`
			);
		});

		it("edge case 8", () => {
			const code = `
import { product } from "@scope/old-package/lib/utils/sum.js";
import { sum as sum1, multipleSum } from "@scope/old-package/lib/utils/sum.js";
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@scope/old-package",
								subPath: "lib/utils/sum.js",
								entity: "sum"
							},
							to: {
								packageName: "@scope/new-package",
								subPath: "",
								entity: "newSum"
							}
						}
					],
					pathMigrations: [
						{
							from: "@scope/old-package/**/*.js",
							to: "@scope/new-package"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { product, multipleSum, newSum as sum1 } from "@scope/new-package";`
			);
		});

		it("edge case 9", () => {
			const code = `
import type { ButtonProps } from "@com.mgmtp.a12.widgets/widgets-core/lib/button/index.js";
import { Button } from "@com.mgmtp.a12.widgets/widgets-core/lib/button/index.js";
import { ButtonGroup } from "@com.mgmtp.a12.widgets/widgets-core/lib/button-group/index.js";
import { Icon } from "@com.mgmtp.a12.widgets/widgets-core/lib/icon/index.js";
import { QuickAccessButton } from "@com.mgmtp.a12.widgets/widgets-core/lib/quick-access-button/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { type ButtonProps, Button, ButtonGroup, Icon, QuickAccessButton } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 10", () => {
			const code = `
import { Checkbox as WidgetCheckbox } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { Checkbox as WidgetCheckbox } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 11", () => {
			const code = `
import { ActionContentbox, ContentBoxElements } from "@com.mgmtp.a12.widgets/widgets-core/lib/contentbox/index.js";
import { Button } from "@com.mgmtp.a12.widgets/widgets-core/lib/button/index.js";
import { ButtonGroup } from "@com.mgmtp.a12.widgets/widgets-core/lib/button-group/index.js";
import type { CheckboxProps } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
import { Checkbox as WidgetCheckbox } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
import { Icon } from "@com.mgmtp.a12.widgets/widgets-core/lib/icon/index.js";
import { LayoutGrid } from "@com.mgmtp.a12.widgets/widgets-core/lib/layout/layout-grid/index.js";
import { Link } from "@com.mgmtp.a12.widgets/widgets-core/lib/link/index.js";
import type { TextFieldProps } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/text-field/index.js";
import { TextField as WidgetsTextField } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/text-field/index.js";
import { Typography } from "@com.mgmtp.a12.widgets/widgets-core/lib/typography/index.js";
import { provider } from "@com.mgmtp.a12.widgets/widgets-core/lib/common/main/device-detector.js";
import { ValidationBar } from "@com.mgmtp.a12.widgets/widgets-core/lib/validation-bar/index.js";
import { QuickAccessButton } from "@com.mgmtp.a12.widgets/widgets-core/lib/quick-access-button/index.js";
import { BulletList } from "@com.mgmtp.a12.widgets/widgets-core/lib/bullet-list/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { ActionContentbox, ContentBoxElements, Button, ButtonGroup, type CheckboxProps, Checkbox as WidgetCheckbox, Icon, LayoutGrid, Link, type TextFieldProps, TextField as WidgetsTextField, Typography, provider, ValidationBar, QuickAccessButton, BulletList } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 12", () => {
			const code = `
import { type CheckboxProps } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { type CheckboxProps } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 13", () => {
			const code = `
import type { CheckboxProps } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import type { CheckboxProps } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 14", () => {
			const code = `
import { type CheckboxProps, Checkbox } from "@com.mgmtp.a12.widgets/widgets-core/lib/input/checkbox/index.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: "@com.mgmtp.a12.widgets/widgets-core/**/*.js",
							to: "@com.mgmtp.a12.widgets/widgets-core"
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(
				`import { type CheckboxProps, Checkbox } from "@com.mgmtp.a12.widgets/widgets-core";`
			);
		});

		it("edge case 15", () => {
			const code = `
import { BodyContent } from "@com.mgmtp.a12.widgets/widgets-core/lib/tree-table/main/tree-table.view.js";

const Content = () => <BodyContent />
`;
			const result = testMigrateImports(
				code,
				{
					entityMigrations: [
						{
							from: {
								packageName: "@com.mgmtp.a12.widgets/widgets-core",
								subPath: "/lib/tree-table/main/tree-table.view.js",
								entity: "BodyContent"
							},
							to: { subPath: "", entity: "TreeTableBodyContent" }
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(`
				import { TreeTableBodyContent as BodyContent } from "@com.mgmtp.a12.widgets/widgets-core";

				const Content = () => <BodyContent />
			`);
		});

		it("edge case 16", () => {
			const packageName = "@com.mgmtp.a12.print/print-model-api";
			const code = `
import * as ModelAPI from "@com.mgmtp.a12.print/print-model-api/lib/model/index.js";
import { SegmentType } from "@com.mgmtp.a12.print/print-model-api/lib/model/index.js";
import * as GeneratedDTO from "@com.mgmtp.a12.print/print-model-api/lib/generated/internal/dto/PrintModelDTO.js";
`;
			const result = testMigrateImports(
				code,
				{
					pathMigrations: [
						{
							from: `${packageName}/lib/errors/**`,
							to: `${packageName}/errors`
						},
						{
							from: `${packageName}/lib/generated/**`,
							to: `${packageName}/generated`
						},
						{
							from: `${packageName}/lib/input-source/**`,
							to: `${packageName}/input-source`
						},
						{
							from: `${packageName}/lib/generator/**`,
							to: `${packageName}/generator`
						},
						{
							from: `${packageName}/lib/model/**`,
							to: `${packageName}/model`
						},
						{
							from: `${packageName}/lib/utils/**`,
							to: `${packageName}/utils`
						},
						{
							from: `${packageName}/lib/walker/**`,
							to: `${packageName}/walker`
						}
					]
				},
				true
			);
			expect(result).toMatchInlineSnapshot(`
				import * as ModelAPI from "@com.mgmtp.a12.print/print-model-api/model";
				import { SegmentType } from "@com.mgmtp.a12.print/print-model-api/model";
				import * as GeneratedDTO from "@com.mgmtp.a12.print/print-model-api/generated";
			`);
		});
	});

	describe("default import migration", () => {
		describe("validation", () => {
			it("should reject when both entity and defaultImport are specified", () => {
				const code = `import Something from "@scope/package";`;

				expect(() => {
					testMigrateImports(code, {
						entityMigrations: [
							{
								from: {
									packageName: "@scope/package",
									entity: "Something",
									defaultImport: true
								},
								to: { entity: "SomethingElse" }
							}
						]
					});
				}).toThrow(/cannot specify both 'entity' and 'defaultImport'/);
			});

			it("should reject when neither entity nor defaultImport is specified for entity migration", () => {
				const code = `import Something from "@scope/package";`;

				expect(() => {
					testMigrateImports(code, {
						entityMigrations: [
							{
								from: {
									packageName: "@scope/package"
								},
								to: { entity: "SomethingElse" }
							}
						]
					});
				}).toThrow(/must specify either 'entity' or 'defaultImport'/);
			});
		});

		describe("migration", () => {
			it("should migrate default import to named import", () => {
				const code = `import Something from "@scope/package";\nSomething.doStuff();`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/package", defaultImport: true },
							to: { entity: "SomethingElse" }
						}
					]
				});

				expect(result).toMatchInlineSnapshot(`
					import { SomethingElse } from "@scope/package";

					SomethingElse.doStuff();
				`);
			});

			it("should migrate default import to named import with different package", () => {
				const code = `import OldThing from "@scope/old-package";\nOldThing.call();`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/old-package", defaultImport: true },
							to: { packageName: "@scope/new-package", entity: "NewThing" }
						}
					]
				});
				expect(result).toMatchInlineSnapshot(`
					import { NewThing } from "@scope/new-package";

					NewThing.call();
				`);
			});

			it("should migrate default import to namespace import", () => {
				const code = `import Helper from "@scope/package";\nHelper.method();`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/package", defaultImport: true },
							to: { entity: "method", namespaces: ["Utils"] }
						}
					]
				});
				expect(result).toMatchInlineSnapshot(`
					import { Utils } from "@scope/package";

					Utils.method();
				`);
			});

			it("should preserve other named imports when migrating default import", () => {
				const code = `import DefaultExport, { namedExport } from "@scope/package";\nDefaultExport.call();\nnamedExport();`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: { packageName: "@scope/package", defaultImport: true },
							to: { entity: "NewDefault" }
						}
					]
				});
				expect(result).toMatchInlineSnapshot(`
					import { namedExport, NewDefault } from "@scope/package";
					NewDefault.call();
					namedExport();
				`);
			});

			it("should handle default import with subpath", () => {
				const code = `import Component from "@scope/package/lib/component";\nComponent.render();`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: {
								packageName: "@scope/package",
								subPath: "lib/component",
								defaultImport: true
							},
							to: { subPath: "", entity: "Component" }
						}
					]
				});
				expect(result).toMatchInlineSnapshot(`
					import { Component } from "@scope/package";

					Component.render();
				`);
			});

			it("should handle mixed with named import", () => {
				const code = `import Component, {utils} from "@scope/package/lib/component";
Component.render();
utils.doSomething();
`;
				const result = testMigrateImports(code, {
					entityMigrations: [
						{
							from: {
								packageName: "@scope/package",
								subPath: "lib/component",
								defaultImport: true
							},
							to: { subPath: "", entity: "Component" }
						},
						{
							from: {
								packageName: "@scope/package",
								subPath: "lib/component",
								entity: "utils"
							},
							to: { subPath: "moved", entity: "Utilities" }
						}
					]
				});
				expect(result).toMatchInlineSnapshot(`
					import { Utilities as utils } from "@scope/package/moved";
					import { Component } from "@scope/package";

					Component.render();
					utils.doSomething();
				`);
			});
		});
	});

	describe("CJS-style imports (without extensions)", () => {
		it("should match import without .js extension", () => {
			const code = `
import { sum, product } from "@scope/package/lib/main/utils";
sum(1, 2);
product(3, 4);
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/package",
							subPath: "/lib/main/utils.js",
							entity: "sum"
						},
						to: {
							packageName: "@new-scope/new-package",
							entity: "newSum"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { newSum as sum } from "@new-scope/new-package/lib/main/utils";
				import { product } from "@scope/package/lib/main/utils";
				sum(1, 2);
				product(3, 4);
			`);
		});

		it("should match folder import (index.js)", () => {
			const code = `
import { sum, product } from "@scope/package/lib/main/utils";
sum(1, 2);
product(3, 4);
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/package",
							subPath: "/lib/main/utils/index.js",
							entity: "sum"
						},
						to: {
							packageName: "@new-scope/new-package",
							entity: "newSum"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { newSum as sum } from "@new-scope/new-package/lib/main/utils/index";
				import { product } from "@scope/package/lib/main/utils";
				sum(1, 2);
				product(3, 4);
			`);
		});

		it("should prefer exact match over CJS-style match", () => {
			const code = `
import { sum } from "@scope/package/lib/utils.js";
sum(1, 2);
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/package",
							subPath: "/lib/utils.js",
							entity: "sum"
						},
						to: {
							packageName: "@new-scope/new-package",
							entity: "newSum"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { newSum as sum } from "@new-scope/new-package/lib/utils.js";

				sum(1, 2);
			`);
		});

		it("should handle path-only migration with CJS-style import", () => {
			const code = `
import { sum, product } from "legacy-package/lib/utils";
sum(1, 2);
product(3, 4);
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "legacy-package",
							subPath: "/lib/utils.js"
						},
						to: {
							packageName: "new-package",
							subPath: "/lib/new-utils.js"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { sum, product } from "new-package/lib/new-utils";
				sum(1, 2);
				product(3, 4);
			`);
		});

		it("should handle namespace migration with CJS-style import", () => {
			const code = `
import { Utils } from "@scope/package/lib/main";
Utils.sum(1, 2);
Utils.product(3, 4);
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/package",
							subPath: "/lib/main/index.js",
							namespaces: ["Utils"],
							entity: "sum"
						},
						to: {
							packageName: "@new-scope/new-package",
							namespaces: ["NewUtils"],
							entity: "newSum"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { NewUtils } from "@new-scope/new-package/lib/main/index";
				import { Utils } from "@scope/package/lib/main";
				NewUtils.newSum(1, 2);
				Utils.product(3, 4);
			`);
		});

		it("should handle default import migration with CJS-style import", () => {
			const code = `
import MyDefault from "@scope/package/lib/component";
MyDefault.render();
`;
			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/package",
							subPath: "/lib/component.js",
							defaultImport: true
						},
						to: {
							packageName: "@new-scope/new-package",
							entity: "NewComponent"
						}
					}
				]
			});
			expect(result).toMatchInlineSnapshot(`
				import { NewComponent } from "@new-scope/new-package/lib/component";

				NewComponent.render();
			`);
		});
	});

	describe("aliased specifier merging", () => {
		it("should keep both specifiers when the same name is merged under a different alias", () => {
			const code = [
				`import { Props } from "@scope/pkg/lib/foo";`,
				`import { Props as BarProps } from "@scope/pkg/lib/bar";`
			].join("\n");

			const result = testMigrateImports(code, {
				pathMigrations: [{ from: "@scope/pkg/lib/**", to: "@scope/pkg" }]
			});

			expect(result).toMatchInlineSnapshot(
				`import { Props, Props as BarProps } from "@scope/pkg";`
			);
		});

		it("should keep the unaliased specifier when the aliased one is merged first", () => {
			const code = [
				`import { Props as FooProps } from "@scope/pkg/lib/foo";`,
				`import { Props } from "@scope/pkg/lib/bar";`
			].join("\n");

			const result = testMigrateImports(code, {
				pathMigrations: [{ from: "@scope/pkg/lib/**", to: "@scope/pkg" }]
			});

			expect(result).toMatchInlineSnapshot(
				`import { Props as FooProps, Props } from "@scope/pkg";`
			);
		});

		it("should alias a renamed entity when the target name is already imported", () => {
			const code = [
				`import { New } from "@scope/pkg";`,
				`import { Old } from "@scope/pkg/lib/foo";`,
				`console.log(New, Old);`
			].join("\n");

			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: {
							packageName: "@scope/pkg",
							subPath: "/lib/foo",
							entity: "Old"
						},
						to: { packageName: "@scope/pkg", subPath: "", entity: "New" }
					}
				]
			});

			expect(result).toContain("New as Old");
		});

		it("should still collapse a genuinely identical specifier", () => {
			const code = [
				`import { Props } from "@scope/pkg/lib/foo";`,
				`import { Props } from "@scope/pkg/lib/bar";`
			].join("\n");

			const result = testMigrateImports(code, {
				pathMigrations: [{ from: "@scope/pkg/lib/**", to: "@scope/pkg" }]
			});

			expect(result).toMatchInlineSnapshot(
				`import { Props } from "@scope/pkg";`
			);
		});

		it("should un-alias a single specifier that cannot collide", () => {
			const code = [
				`import { Props as FooProps } from "@scope/pkg/lib/foo";`,
				`export type X = FooProps;`
			].join("\n");

			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: { packageName: "@scope/pkg", subPath: "/lib/foo" },
						to: { packageName: "@scope/pkg", subPath: "" }
					}
				]
			});

			expect(result).toContain(`import { Props } from "@scope/pkg"`);
			expect(result).toContain("export type X = Props;");
			expect(result).not.toContain("FooProps");
		});

		it("should document the known un-aliasing collision of migratePathOnly", () => {
			// KNOWN PRE-EXISTING DEFECT, tracked as a separate follow-up and deliberately
			// not fixed on this branch: `migratePathOnly` keys the merge on the imported
			// name while `replaceUsages` rewrites usages to that same name. When the alias
			// collides with a local binding that already exists on the target module, the
			// merge emits a duplicate identifier (which does not compile) and silently
			// rebinds the second usage. The snapshot below pins the current, defective
			// output so a change to it is noticed - it does not endorse that output.
			const code = [
				`import { Alpha as Props } from "@scope/pkg";`,
				`import { Props as FooProps } from "@scope/pkg/lib/foo";`,
				`export type A = Props;`,
				`export type B = FooProps;`
			].join("\n");

			const result = testMigrateImports(code, {
				entityMigrations: [
					{
						from: { packageName: "@scope/pkg", subPath: "/lib/foo" },
						to: { packageName: "@scope/pkg", subPath: "" }
					}
				]
			});

			expect(result).toMatchInlineSnapshot(`
				import { Alpha as Props, Props } from "@scope/pkg";
				export type A = Props;
				export type B = Props;
			`);
		});
	});

	describe("extensionless deep imports", () => {
		const jsOnly = {
			pathMigrations: [{ from: "@scope/pkg/lib/**/*.js", to: "@scope/pkg" }]
		};

		it("should rewrite an extensionless deep import against a .js rule", () => {
			const code = `import { DefaultThemeType } from "@scope/pkg/lib/theme";`;

			expect(testMigrateImports(code, jsOnly)).toBe(
				`import { DefaultThemeType } from "@scope/pkg";`
			);
		});

		it("should rewrite a folder import against a .js rule", () => {
			const code = `import { A } from "@scope/pkg/lib/theme/flat";`;

			expect(testMigrateImports(code, jsOnly)).toBe(
				`import { A } from "@scope/pkg";`
			);
		});

		it("should still rewrite an explicit .js deep import", () => {
			const code = `import { Other } from "@scope/pkg/lib/other.js";`;

			expect(testMigrateImports(code, jsOnly)).toBe(
				`import { Other } from "@scope/pkg";`
			);
		});

		it("should not rewrite a specifier outside the rule", () => {
			const code = `import { X } from "@scope/other/lib/thing";`;

			expect(testMigrateImports(code, jsOnly)).toBe(code);
		});

		it("should honour a .js exclude when the source specifier is extensionless", () => {
			const config: ImportMigrationConfiguration = {
				pathMigrations: [
					{
						from: "@scope/pkg/lib/**/*.js",
						to: "@scope/pkg",
						exclude: "@scope/pkg/lib/main/overview-model.js"
					}
				]
			};

			const excluded = `import { M } from "@scope/pkg/lib/main/overview-model";`;
			const rewritten = `import { A } from "@scope/pkg/lib/main/other";`;

			expect(testMigrateImports(excluded, config)).toBe(excluded);
			expect(testMigrateImports(rewritten, config)).toBe(
				`import { A } from "@scope/pkg";`
			);
		});

		it("should take captures from the synthesised candidate that matched", () => {
			// Matches only via the synthesised "@scope/old-package/lib/utils.js" candidate:
			// the rule requires the .js extension the source specifier omits.
			const code = `import { sum } from "@scope/old-package/lib/utils";`;

			expect(
				testMigrateImports(code, {
					pathMigrations: [
						{
							from: "@scope/old-package/lib/*.js",
							to: "@scope/new-package/dist/$1"
						}
					]
				})
			).toBe(`import { sum } from "@scope/new-package/dist/utils";`);
		});

		it("should not leak a synthesised /index.js suffix into a captured path", () => {
			// Matches only via the synthesised "@scope/old/lib/index.js" candidate, so $1
			// captures "index.js" - a suffix the source specifier never had.
			const code = `import { a } from "@scope/old/lib";`;

			expect(
				testMigrateImports(code, {
					pathMigrations: [{ from: "@scope/old/lib/*", to: "@scope/new/$1" }]
				})
			).toBe(`import { a } from "@scope/new";`);
		});

		it("should keep a literal .js suffix in the target when the source has no extension", () => {
			// "to" has no $n placeholders at all, so the ".js" here is written by the rule
			// author, not synthesised - it must survive even though the match itself only
			// succeeded via the synthesised ".js" candidate.
			const code = `import { A } from "@scope/pkg/lib/foo";`;

			expect(
				testMigrateImports(code, {
					pathMigrations: [
						{ from: "@scope/pkg/lib/**/*.js", to: "@scope/other/index.js" }
					]
				})
			).toBe(`import { A } from "@scope/other/index.js";`);
		});

		it("should keep a literal .js suffix in the target when the match is via the synthesised /index.js candidate", () => {
			const code = `import { A } from "@scope/pkg/lib/foo";`;

			expect(
				testMigrateImports(code, {
					pathMigrations: [
						{ from: "@scope/pkg/lib/**/index.js", to: "@scope/other/index.js" }
					]
				})
			).toBe(`import { A } from "@scope/other/index.js";`);
		});

		it("should strip a synthesised .js suffix from a capture that spans the extension", () => {
			// The extglob capture group spans the whole "utils.js", including the extension,
			// while the plain "utils" specifier does not match the rule on its own - so this
			// only matches via the synthesised ".js" candidate. `picomatch.makeRe` compiles
			// this rule's "from" to /^(?:@scope\/old\/lib\/(([^/]*?)\.js))$/, so the
			// synthesised ".js" candidate matches with $1 === "utils.js".
			const code = `import { a } from "@scope/old/lib/utils";`;

			expect(
				testMigrateImports(code, {
					pathMigrations: [
						{ from: "@scope/old/lib/@(*.js)", to: "@scope/new/$1" }
					]
				})
			).toBe(`import { a } from "@scope/new/utils";`);
		});

		it("should substitute a non-participating capture group with an empty string", () => {
			// "@scope/old/lib/theme.js" matches with the "**" group unmatched, so $1 must
			// not survive as a literal in the rewritten specifier.
			const code = `import { A } from "@scope/old/lib/theme";`;
			const result = testMigrateImports(code, {
				pathMigrations: [
					{ from: "@scope/old/lib/**/*.js", to: "@scope/new/$1/$2" }
				]
			});

			expect(result).not.toContain("$1");
			expect(result).toBe(`import { A } from "@scope/new/theme";`);
		});
	});

	describe("non-JavaScript deep imports", () => {
		const jsOnly: ImportMigrationConfiguration = {
			pathMigrations: [{ from: "@scope/pkg/lib/**/*.js", to: "@scope/pkg" }]
		};

		it("should leave a side-effect .css import untouched", () => {
			// A side-effect import has no binding, so rewriting it used to delete the
			// whole declaration instead of merging anything.
			const code = `import "@scope/pkg/lib/theme/basic.css";`;

			expect(testMigrateImports(code, jsOnly)).toBe(code);
		});

		it("should leave a side-effect .css import untouched next to a barrel import", () => {
			const code = [
				`import { Theme } from "@scope/pkg";`,
				`import "@scope/pkg/lib/theme/basic.css";`
			].join("\n");

			expect(testMigrateImports(code, jsOnly)).toBe(code);
		});

		it("should leave a .json default import untouched", () => {
			const code = `import model from "@scope/pkg/lib/data/model.json";`;

			expect(testMigrateImports(code, jsOnly)).toBe(code);
		});

		it("should not pre-empt a later rule's .css exclude", () => {
			// The two-rule shape used by the widgets recipe: a .js rule first, then a
			// catch-all that excludes stylesheets.
			const config: ImportMigrationConfiguration = {
				pathMigrations: [
					{ from: "@scope/pkg/lib/**/*.js", to: "@scope/pkg" },
					{
						from: "@scope/pkg/lib/**",
						to: "@scope/pkg",
						exclude: "@scope/pkg/lib/**/*.css"
					}
				]
			};

			const excluded = `import "@scope/pkg/lib/theme/basic.css";`;
			const rewritten = `import { A } from "@scope/pkg/lib/theme";`;

			expect(testMigrateImports(excluded, config)).toBe(excluded);
			expect(testMigrateImports(rewritten, config)).toBe(
				`import { A } from "@scope/pkg";`
			);
		});
	});

	describe("declaration files", () => {
		it("should rewrite deep imports in a .d.ts file", () => {
			const code = [
				`import { DefaultThemeType } from "@scope/pkg/lib/theme";`,
				`export type T = DefaultThemeType;`
			].join("\n");

			const result = testMigrateImports(
				code,
				{ pathMigrations: [{ from: "@scope/pkg/lib/**", to: "@scope/pkg" }] },
				false,
				"d.ts"
			);

			expect(result).toContain(`from "@scope/pkg"`);
			expect(result).not.toContain("/lib/theme");
		});
	});
});
