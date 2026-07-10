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

import whyDidYouRender, {
	type WhyDidYouRenderOptions
} from "@welldone-software/why-did-you-render";
import React from "react";

const wdyrLogs: string[] = [];

function safeStringify(value: object | null): string {
	try {
		return JSON.stringify(value);
	} catch {
		// if the object contains cycles, it wouldn't be helpful to log anyway, so we just ignore it
		return "";
	}
}

/**
 * Removes formatting characters and handles objects
 */
function cleanArgs(...args: unknown[]): string[] {
	return args
		.map(arg =>
			typeof arg === "object"
				? safeStringify(arg)
				: typeof arg === "string"
					? arg.replace(/%c/g, "")
					: ""
		)
		.filter(arg => !arg.startsWith("color:") && arg !== "{}");
}

/**
 * Collect everything that WDYR wants to log
 *
 * Note: the `notifier` property of WDYR could also be used here,
 * but would require implementing the logic ("was this re-render an error or not") manually
 *
 * Since the default notifier of WDYR already does this, we re-use it here by "spying" on the logging output
 */
function consoleSpy(...args: unknown[]): void {
	wdyrLogs.push(cleanArgs(...args).join(" "));
}

/**
 * Asserts that WhyDidYouRender collected exactly `expected` logs since the last call.
 *
 * Also resets collected re-rendering information, which allows this function to be called repeatedly (e.g. in afterEach)
 *
 * Note: Requires {@link setupWhyDidYouRenderForTest} to be called in the test setup.
 *
 * #### Note about usage
 * Prefer using {@link assertNoUnnecessaryRenderings} instead, as *any* log output almost always signals a mistake.
 * This function should only be used as a last resort for re-renderings you can't control (e.g. produced by 3rd party libs)
 */
export function assertWhyDidYouRenderLogCount(expected: number): void {
	const collected = wdyrLogs.splice(0);

	if (collected.length !== expected) {
		throw new Error(
			`Expected ${expected} logs, got ${collected.length}:\n${collected.join(
				"\n"
			)}`
		);
	}
}

/**
 * When called, throws an error if WhyDidYouRender collected re-rendering information until now.
 *
 * Also resets collected re-rendering information, which allows this function to be called repeatedly (e.g. in afterEach)
 *
 * Note: Requires {@link setupWhyDidYouRenderForTest} to be called in the test setup.
 */
export function assertNoUnnecessaryRenderings(): void {
	const errorMessage = wdyrLogs.splice(0).join("\n");

	if (errorMessage.length) {
		throw new Error(errorMessage);
	}
}

/**
 * Setup WhyDidYouRender with a custom console logging to collect re-rendering information. Requires:
 * - tests that render react components (e.g. RTL)
 * - explicit tracking of interesting components (performance-wise) with `whyDidYouRender = true`
 *
 * *NOTE: WDYR monkey-patches react, therefore this function needs to be called before any other react import!*
 *
 * See https://github.com/welldone-software/why-did-you-render
 *
 * When setup, re-rendering asserts can be used in tests.
 *
 * @param additionalHooksToTrack Other hooks to track
 */
export function setupWhyDidYouRenderForTest(
	additionalHooksToTrack?: [unknown, string][]
): void {
	//@ts-expect-error typing is wrong here
	whyDidYouRender(React, {
		trackExtraHooks: additionalHooksToTrack,
		consoleGroup: consoleSpy,
		consoleLog: consoleSpy
	} as WhyDidYouRenderOptions);
}
