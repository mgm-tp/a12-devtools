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

/** The asciidoctor attributes shared by every docu build. */
const DEFAULT_ATTRIBUTES: Readonly<Record<string, unknown>> = Object.freeze({
	doctype: "article",
	encoding: "utf-8",
	lang: "en",
	icons: "font",
	// asciidoctor's built-in client-side highlighter (loads highlight.js from a CDN)
	"source-highlighter": "highlightjs",
	"source-linenums-option": true,
	toc: "left",
	toclevels: 2,
	"toc-title": "Table of Contents",
	docinfo: "shared",
	tabsize: 2,
	sectnums: true,
	sectanchors: true,
	sectlinks: true,
	experimental: true,
	sectids: true,
	fragment: true,
	xrefstyle: "short",
	standalone: true
});

/** Per-run values injected into the attribute map by {@link buildDocs}. */
export interface AttributeContext {
	readonly revnumber: string;
	readonly author: string;
	readonly docinfodir: string;
}

/**
 * Builds the asciidoctor attribute map, precedence low→high:
 * frozen defaults ← per-run context ← caller overrides.
 */
export function buildAttributes(
	context: AttributeContext,
	overrides: Readonly<Record<string, unknown>> = {}
): Record<string, unknown> {
	return {
		...DEFAULT_ATTRIBUTES,
		revnumber: context.revnumber,
		author: context.author,
		docinfodir: context.docinfodir,
		...overrides
	};
}
