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

import React from "react";

const WHY_DID_YOU_RENDER_ENABLED_FLAG = "whyDidYouRender";

/**
 * Returns whether WDYR is active, which only happens when:
 * - current code runs in development mode
 * - WDYR was toggled via {@link toggleWdyr}
 */
export function isWdyrEnabled(): boolean {
	return (
		process.env.NODE_ENV === "development" &&
		localStorage.getItem(WHY_DID_YOU_RENDER_ENABLED_FLAG) !== null
	);
}

/**
 * Toggle activation of WDYR
 *
 * This function will cause a full page reload (which is necessary because WDYR patches react)
 */
export function toggleWdyr(): void {
	// eslint-disable-next-line @typescript-eslint/no-unused-expressions
	isWdyrEnabled()
		? localStorage.removeItem(WHY_DID_YOU_RENDER_ENABLED_FLAG)
		: localStorage.setItem(WHY_DID_YOU_RENDER_ENABLED_FLAG, "");

	location.reload();
}

/**
 * Conditionally sets up WhyDidYouRender for usage in dev apps. Requires:
 * - explicit tracking of interesting components (performance-wise) with `whyDidYouRender = true`
 *
 * *NOTE: WDYR monkey-patches react, therefore this function needs to be called before any other react import!*
 *
 * See https://github.com/welldone-software/why-did-you-render
 *
 * For usage in render tests, use {@link setupWhyDidYouRenderForTest} instead.
 *
 * @param additionalHooksToTrack Other hooks to track
 */
export async function setupWhyDidYouRender(
	additionalHooksToTrack?: [unknown, string][]
): Promise<void> {
	if (isWdyrEnabled()) {
		// WDYR should only be loaded when actually needed
		const whyDidYouRender = (
			await import("@welldone-software/why-did-you-render")
		).default;

		//@ts-expect-error typing is wrong here
		whyDidYouRender(React, {
			trackExtraHooks: additionalHooksToTrack,
			collapseGroups: true
		});
	}
}
