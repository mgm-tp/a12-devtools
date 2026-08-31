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

import type { Preset } from "conventional-changelog";
import createPreset from "conventional-changelog-conventionalcommits";

export type WriterOptions = NonNullable<Preset["writer"]>;

// narrower than the real writer.transform's (commit, context, options) - subject/scope come
// from the parser at runtime but aren't part of the generic CommitKnownProps type
type LooseTransform = (
	commit: Record<string, unknown>,
	context: unknown,
	options: unknown
) => Record<string, unknown> | null | undefined;

// matches "release/2026.02-ext0/1.1.0" - the semver suffix after the date/ext segment.
// Must be a RegExp (not its source as a string): @conventional-changelog/git-client's
// getSemverTags() only strips a *string* prefix via a literal tag.startsWith(prefix) check,
// which never matches our variable date/ext segment - a RegExp gets .replace()'d instead,
// correctly stripping the variable segment and validating the remaining semver suffix.
const TAG_PREFIX = /release\/\d{4}\.\d{2}-ext[^/]*\//;

// conventional-changelog's own defaultCommitTransform (which sets commit.version, read by
// the writer's default generateOn to decide release-boundary flushes) naively interpolates
// tags.prefix into `new RegExp(\`tag:\\s*[v=]?${prefix}...\`)` - with a RegExp prefix this
// stringifies to its literal /source/flags form and never matches, so commit.version never
// gets set and multi-release regeneration collapses everything into one untitled block.
// Extract it ourselves from the raw git-tag decoration instead.
function extractVersion(commit: object): string | undefined {
	const { gitTags } = commit as { gitTags?: unknown };
	if (typeof gitTags !== "string") {
		return undefined;
	}
	// a commit can carry several tags - take the first one that is a release tag
	return gitTags
		.matchAll(/tag:\s*([^,)]+)/g)
		.map(([, tag]) => tag)
		.find(tag => TAG_PREFIX.test(tag))
		?.replace(TAG_PREFIX, "");
}

const preset = createPreset({
	types: [
		{ type: "added", section: "Added" },
		{ type: "changed", section: "Changed" },
		{ type: "deprecated", section: "Deprecated" },
		{ type: "removed", section: "Removed" },
		{ type: "fixed", section: "Fixed" },
		{ type: "security", section: "Security" },
		{ type: "Untagged", section: "Untagged" }
	]
}) as Preset;

// the writer builds the section context as `{ ...rootContext, ...keyCommit }`, so the key
// commit's git-tag decoration is readable here. Prefer it over context.version: for a
// release section the latter is still the root context's package version (e.g. the
// "-SNAPSHOT" one), because the release-boundary commit never gets a version of its own -
// see the generateOn comment below.
const headerPartial: NonNullable<WriterOptions["headerPartial"]> = context => {
	const version = extractVersion(context) ?? context.version;
	return `## [${version}]${context.date ? ` - ${context.date}` : ""}\n`;
};

// The release tag sits on a `release: ...` commit, whose type is not one of the changelog
// types, so transform() drops it. conventional-changelog-writer then falls back to the raw
// commit for the release boundary (`keyCommit = commit || chunk`) - and that raw commit
// never went through transform, so the default `generateOn` finds no `version` on it and
// never flushes a section. Every release would collapse into one block titled with the
// package version. Read the git-tag decoration instead, which is present either way.
const generateOn: NonNullable<WriterOptions["generateOn"]> = keyCommit =>
	extractVersion(keyCommit) !== undefined;

// the "-" bullet used to be baked into commitPartial's Handlebars string; in this writer
// version the bullet comes from a shared, non-configurable list() helper used for both
// commits and breaking-change notes, so patching commitPartial no longer has any effect -
// post-process the whole rendered section instead (this also normalizes note bullets to "-")
const template: NonNullable<WriterOptions["template"]> = context => {
	const rendered = preset.writer?.template?.(context);
	return typeof rendered === "string"
		? rendered.replace(/^\* /gm, "- ")
		: (rendered ?? "");
};

export default {
	...preset,
	tags: {
		prefix: TAG_PREFIX
	},
	writer: {
		...preset.writer,
		headerPartial,
		generateOn,
		template,
		transform(
			commit: Record<string, unknown>,
			context: unknown,
			options: unknown
		) {
			const transform = preset.writer?.transform as LooseTransform | undefined;
			const result = transform?.(commit, context, options);
			const type = result ? result.type : "Untagged";
			if (!result && commit.type) {
				return null;
			}
			const rawSubject = (
				result ? result.subject : (commit.subject ?? commit.header ?? "")
			) as string | undefined;
			const subject = rawSubject?.replace(/^(\[.+?\]) /, "$1 - ");
			if (result && /^\s*internal\b/i.test(subject ?? "")) {
				return null;
			}
			return {
				...(result ?? commit),
				type,
				hash: null,
				subject,
				version: extractVersion(commit) ?? commit.version
			};
		}
	}
};
