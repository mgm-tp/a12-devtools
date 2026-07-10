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

import { MigrationType, type MigrationResult } from "./migration-result.js";

interface ReportError {
	filePath: string;
	lineNumber: number;
	errorMessage: string;
}

export class Reporter {
	readonly counter = new Map<string, Map<MigrationType, number>>();
	readonly errors: ReportError[] = [];

	public add(filePath: string, lineNumber: number, result: MigrationResult) {
		const current =
			this.counter.get(filePath) ?? new Map<MigrationType, number>();
		current.set(result.type, (current.get(result.type) ?? 0) + 1);
		this.counter.set(filePath, current);

		if (result.type === MigrationType.Error) {
			this.errors.push({
				filePath,
				lineNumber,
				errorMessage: result.errorMessage
			});
		}
	}

	public report() {
		console.log();

		for (const error of this.errors) {
			console.log(
				`${error.filePath}:${error.lineNumber}:0 ${error.errorMessage}`
			);
		}

		console.log(
			[
				["Files   : ", this.counter.size],
				["Migrated: ", this.sum(MigrationType.Success)],
				["Skipped : ", this.sum(MigrationType.Skip)],
				["Error   : ", this.sum(MigrationType.Error)]
			]
				.map(row => row.join(" "))
				.join("\n")
		);
	}

	private sum(type: MigrationType) {
		let result = 0;

		for (const count of this.counter.values()) {
			result += count.get(type) ?? 0;
		}

		return result;
	}
}
