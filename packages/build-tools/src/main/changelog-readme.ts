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

import type { Command } from "./interfaces.js";

export const CHANGELOG_README = `# Changelog generation

To generate a changelog for the release-info repository we use a modified conventional commits tagging approach.

For each branch one commit message has to be of the shape \`tag: [TicketID] TicketText\`, e.g., \`fixed: [PROJ-1234] fixed button placement\`.
As most commits to the release branches (master, release/XX) are squash commits, the PR title should contain the proposed commit message for the squash commit,
so that it can be reviewed as part of the code review.

There are six tags which are picked up for the changelog:

- fixed
- added
- changed
- deprecated
- removed
- security

These tags work the same as the respective [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) sections.
To allow tagging commits with these tags which do not belong in the customer facing changelog, you can add \`internal\` as a first word after the tag in the commit message. E.g., \`fixed: internal [PROJ-1234] re-enable devapp\`.

We further support some additional tags to keep the commit messages uniform:

- build - For changes which primarily change the build tooling (Gradle, pnpm, webpack)
- ci - For CI/CD pipeline changes (Jenkins, GitHub Actions)
- deps - For dependency version updates
- docs - For documentation-only changes
- perf - For performance improvements
- release - For version bumps and release preparation
- refactor - For internal restructuring without behavior change
- test - For test-only changes
`;

export const changelogReadme: Command = {
	name: "readme",
	description:
		"Prints the commit message conventions used by 'devtools changelog' to generate CHANGELOG.md",
	action: () => {
		console.log(CHANGELOG_README);
	}
};
