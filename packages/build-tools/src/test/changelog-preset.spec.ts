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

import { deepStrictEqual } from "node:assert/strict";
import { describe, it } from "node:test";

import preset from "../main/changelog-preset.js";
import type { WriterOptions } from "../main/changelog-preset.js";

type TemplateContext = Parameters<
	NonNullable<WriterOptions["headerPartial"]>
>[0];

// FinalTemplateContext requires commit/issue/date/*Partial fields even where the function
// under test ignores them - fill them with inert stubs so tests only spell out what matters.
function minimalContext(
	overrides: Partial<TemplateContext> = {}
): TemplateContext {
	return {
		commit: "commit",
		issue: "issues",
		date: "",
		headerPartial: () => "",
		preamblePartial: () => "",
		commitPartial: () => "",
		footerPartial: () => "",
		...overrides
	} as TemplateContext;
}

describe("changelog-preset", () => {
	it("tags.prefix is a RegExp, not a string", async () => {
		// @conventional-changelog/git-client's getSemverTags() only strips a *string* prefix
		// via a literal tag.startsWith(prefix) check, which can never match our variable
		// date/ext segment (e.g. "2026.02-ext0") - a RegExp gets .replace()'d instead, which
		// correctly strips it and leaves a valid semver suffix. A string here means every
		// tag silently fails to match and --release-count-based generation breaks.
		const { tags } = await preset;
		deepStrictEqual(tags.prefix instanceof RegExp, true);
	});

	describe("tags.prefix as used by conventional-changelog's tag matching", () => {
		// mirrors @conventional-changelog/git-client's getSemverTags(): prefix.test(tag), then
		// tag.replace(prefix, "") to strip the variable segment and recover the semver suffix.
		async function extractVersion(tag: string) {
			const { tags } = await preset;
			const prefix = tags.prefix as RegExp;
			return prefix.test(tag) ? tag.replace(prefix, "") : null;
		}

		it("matches an ext0-style release tag", async () => {
			deepStrictEqual(
				await extractVersion("release/2026.06-ext0/17.0.0"),
				"17.0.0"
			);
		});

		it("matches an ext.N-style release tag", async () => {
			deepStrictEqual(
				await extractVersion("release/2025.06-ext.4/16.2.0"),
				"16.2.0"
			);
		});

		it("does not match the pre-ext plain release scheme", async () => {
			deepStrictEqual(await extractVersion("release/9.1.0"), null);
		});
	});

	describe("writer.headerPartial", () => {
		it("includes the date when present", async () => {
			const { writer } = await preset;
			deepStrictEqual(
				writer?.headerPartial?.(
					minimalContext({ version: "17.0.0", date: "2026-07-20" })
				),
				"## [17.0.0] - 2026-07-20\n"
			);
		});

		it("omits the date when absent", async () => {
			const { writer } = await preset;
			deepStrictEqual(
				writer?.headerPartial?.(minimalContext({ version: "17.0.0" })),
				"## [17.0.0]\n"
			);
		});

		it("prefers the release tag over the context version", async () => {
			// the section's key commit is the dropped `release:` commit, so the context still
			// carries the root package version - the tag decoration is the release's identity
			const { writer } = await preset;
			deepStrictEqual(
				writer?.headerPartial?.(
					minimalContext({
						version: "17.1.0-SNAPSHOT",
						date: "2026-08-17",
						gitTags: "(tag: release/2026.06-ext1/17.0.1)"
					} as Partial<TemplateContext>)
				),
				"## [17.0.1] - 2026-08-17\n"
			);
		});

		it("picks the release tag out of a multi-tag decoration", async () => {
			const { writer } = await preset;
			deepStrictEqual(
				writer?.headerPartial?.(
					minimalContext({
						version: "17.1.0-SNAPSHOT",
						gitTags:
							"(HEAD -> master, tag: latest, tag: release/2026.06-ext1/17.0.1)"
					} as Partial<TemplateContext>)
				),
				"## [17.0.1]\n"
			);
		});
	});

	describe("writer.generateOn", () => {
		// the release tag sits on a `release:` commit, which transform() drops; the writer then
		// falls back to the raw commit as the section's key commit, so the boundary has to be
		// recognizable from the tag decoration alone - `version` is never set on it.
		async function generateOn(commit: Record<string, unknown>) {
			const { writer } = await preset;
			return writer?.generateOn?.(
				commit as never,
				[],
				{} as never,
				{} as never
			);
		}

		it("opens a section on a dropped 'release:' commit carrying a release tag", async () => {
			deepStrictEqual(
				await generateOn({
					type: "release",
					header: "release: [PROJ-0] Set release version 17.0.1",
					gitTags: "(tag: release/2026.06-ext1/17.0.1)"
				}),
				true
			);
		});

		it("does not open a section on an untagged commit", async () => {
			deepStrictEqual(
				await generateOn({
					type: "fixed",
					header: "fixed: [PROJ-2] fix button",
					gitTags: "(HEAD -> master)"
				}),
				false
			);
		});

		it("does not open a section on a non-release tag", async () => {
			deepStrictEqual(
				await generateOn({
					type: "fixed",
					header: "fixed: [PROJ-2] fix button",
					gitTags: "(tag: nightly)"
				}),
				false
			);
		});
	});

	describe("writer.template", () => {
		it("renders commit and breaking-change entries with a '-' bullet", async () => {
			const { writer } = await preset;
			const context = minimalContext({
				version: "17.0.0",
				date: "2026-07-20",
				linkReferences: false,
				headerPartial: writer?.headerPartial,
				commitPartial: writer?.commitPartial,
				preamblePartial: writer?.preamblePartial,
				footerPartial: writer?.footerPartial,
				// scope/subject and note.commit exist at runtime (set by the parser/whatBump
				// pipeline) but aren't part of the shared CommitKnownProps/CommitNote types
				commitGroups: [
					{
						title: "Added",
						commits: [
							{
								type: "added",
								subject: "[PROJ-1] add thing",
								scope: null,
								hash: "abc1234def",
								header: "added: [PROJ-1] add thing",
								references: [],
								notes: []
							}
						]
					}
				] as unknown as TemplateContext["commitGroups"],
				noteGroups: [
					{
						title: "BREAKING CHANGES",
						notes: [
							{
								title: "BREAKING CHANGES",
								text: "big change",
								commit: { scope: null }
							}
						]
					}
				] as unknown as TemplateContext["noteGroups"]
			});
			const rendered = await writer?.template?.(context);
			deepStrictEqual(rendered?.includes("- big change"), true);
			deepStrictEqual(rendered?.includes("- [PROJ-1] add thing"), true);
			deepStrictEqual(rendered?.includes("* "), false);
		});
	});

	describe("writer.transform", () => {
		const context = { linkReferences: false };

		it("drops commits tagged as internal", async () => {
			const { writer } = await preset;
			const commit = {
				type: "fixed",
				subject: "internal [PROJ-1] re-enable devapp",
				header: "fixed: internal [PROJ-1] re-enable devapp",
				scope: null,
				notes: [],
				references: []
			};
			deepStrictEqual(writer?.transform?.(commit, context, {}), null);
		});

		it("defaults commits without a recognized type to 'Untagged'", async () => {
			const { writer } = await preset;
			const commit = {
				type: null,
				subject: "some raw commit message",
				header: "some raw commit message",
				scope: null,
				notes: [],
				references: []
			};
			const result = writer?.transform?.(commit, context, {}) as
				| Record<string, unknown>
				| null
				| undefined;
			deepStrictEqual(result?.type, "Untagged");
		});

		it("rewrites the ticket-prefix separator and clears the hash", async () => {
			const { writer } = await preset;
			const commit = {
				type: "fixed",
				subject: "[PROJ-2] fix button",
				header: "fixed: [PROJ-2] fix button",
				scope: null,
				notes: [],
				references: []
			};
			const result = writer?.transform?.(commit, context, {}) as
				| Record<string, unknown>
				| null
				| undefined;
			deepStrictEqual(result?.subject, "[PROJ-2] - fix button");
			deepStrictEqual(result?.hash, null);
		});

		it("extracts the version from a matching release tag decoration", async () => {
			const { writer } = await preset;
			const commit = {
				type: "fixed",
				subject: "[PROJ-2] fix button",
				header: "fixed: [PROJ-2] fix button",
				scope: null,
				notes: [],
				references: [],
				gitTags: "(HEAD -> master, tag: release/2026.02-ext0/1.1.0)"
			};
			const result = writer?.transform?.(commit, context, {}) as
				| Record<string, unknown>
				| null
				| undefined;
			deepStrictEqual(result?.version, "1.1.0");
		});

		it("leaves version unset for commits without a matching release tag", async () => {
			const { writer } = await preset;
			const commit = {
				type: "fixed",
				subject: "[PROJ-2] fix button",
				header: "fixed: [PROJ-2] fix button",
				scope: null,
				notes: [],
				references: [],
				gitTags: "(HEAD -> master)"
			};
			const result = writer?.transform?.(commit, context, {}) as
				| Record<string, unknown>
				| null
				| undefined;
			deepStrictEqual(result?.version, undefined);
		});
	});
});
