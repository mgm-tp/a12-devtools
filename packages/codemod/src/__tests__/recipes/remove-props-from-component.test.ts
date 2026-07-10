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

import { removePropsFromComponent } from "../../internal/recipes/index.js";
import { generateUid } from "../../internal/test-recipe.js";

describe("removePropsFromComponent", () => {
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

	function transform(sourceText: string): string {
		const sourceFile = project.createSourceFile(
			`test-${generateUid()}.tsx`,
			sourceText
		);

		removePropsFromComponent(sourceFile, {
			packageName: "@example/pkg",
			namespaceName: "Components",
			componentName: "Button",
			removedProps: new Set(["deprecated", "oldProp", "children"])
		});

		return sourceFile.getFullText();
	}

	it("removes props from direct named imports", () => {
		const result = transform(`
import { Button } from "@example/pkg";

const view = <Button label="x" deprecated="a" oldProp={1} />;
`);

		expect(result).toContain('<Button label="x" />');
		expect(result).not.toContain("deprecated");
		expect(result).not.toContain("oldProp");
	});

	it("handles aliased direct imports", () => {
		const result = transform(`
import { Button as Btn } from "@example/pkg";

const view = <Btn deprecated="a" oldProp={1} label="x" />;
`);

		expect(result).toContain('<Btn label="x" />');
		expect(result).not.toContain("deprecated");
		expect(result).not.toContain("oldProp");
	});

	it("removes props from namespaced usages", () => {
		const result = transform(`
import { Components } from "@example/pkg";

const view = <Components.Button label="x" deprecated="a" oldProp={1} />;
`);

		expect(result).toContain('<Components.Button label="x" />');
		expect(result).not.toContain("deprecated");
		expect(result).not.toContain("oldProp");
	});

	it("handles aliased namespace imports", () => {
		const result = transform(`
import { Components as LegacyComponents } from "@example/pkg";

const view = <LegacyComponents.Button oldProp="x" label="y" deprecated />;
`);

		expect(result).toContain('<LegacyComponents.Button label="y" />');
		expect(result).not.toContain("deprecated");
		expect(result).not.toContain("oldProp");
	});

	it("does not affect usages from other packages or unknown components", () => {
		const result = transform(`
import { Button, Components } from "@example/other";
import { Card } from "@example/pkg";

const view = (
	<>
		<Button deprecated="a" />
		<Components.Button deprecated="a" />
		<Card deprecated="a" />
	</>
);
`);

		expect(result).toContain('<Button deprecated="a" />');
		expect(result).toContain('<Components.Button deprecated="a" />');
		expect(result).toContain('<Card deprecated="a" />');
	});

	it("removes all props when all are specified for removal", () => {
		const result = transform(`
import { Button } from "@example/pkg";

const view = <Button deprecated="a" oldProp={1} />;
`);

		expect(result).toContain("<Button />");
		expect(result).not.toContain("deprecated");
		expect(result).not.toContain("oldProp");
	});

	it("preserves spread attributes", () => {
		const result = transform(`
import { Button } from "@example/pkg";

const view = <Button {...props} deprecated="a" label="x" {...otherProps} />;
`);

		expect(result).toContain('<Button {...props} label="x" {...otherProps} />');
		expect(result).not.toContain("deprecated");
	});

	it("removes JSX children when children prop is removed", () => {
		const result = transform(`
import { Button, Text } from "@example/pkg";

const view = (
	<>
		<Button><Text>Click me</Text></Button>
		<Button label="x">Some content</Button>
	</>
);
`);

		expect(result).toContain("<Button />");
		expect(result).toContain('<Button label="x" />');
		expect(result).not.toContain("Click me");
		expect(result).not.toContain("Some content");
	});
});
