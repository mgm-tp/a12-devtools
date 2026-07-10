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

import type { MatcherFunction } from "@testing-library/react";
import {
	buildQueries,
	queryHelpers,
	queries as rtlQueries
} from "@testing-library/react";

const queryAllById = queryHelpers.queryAllByAttribute.bind(null, "id");
const [queryById, getAllById, getById, findAllById, findById] = buildQueries(
	queryAllById,
	(_, matcher) => `Found multiple elements by: [id=${matcher}]`,
	(_, matcher) => `Unable to find an element by: [id="${matcher}"]`
);

const queryAllByDataRole = queryHelpers.queryAllByAttribute.bind(
	null,
	"data-role"
);
const [
	queryByDataRole,
	getAllByDataRole,
	getByDataRole,
	findAllByDataRole,
	findByDataRole
] = buildQueries(
	queryAllByDataRole,
	(_, matcher) => `Found multiple elements by: [data-role=${matcher}]`,
	(_, matcher) => `Unable to find an element by: [data-role="${matcher}"]`
);

const queryAllByDataTestId = queryHelpers.queryAllByAttribute.bind(
	null,
	"data-testid"
);
const [
	queryByDataTestId,
	getAllByDataTestId,
	getByDataTestId,
	findAllByDataTestId,
	findByDataTestId
] = buildQueries(
	queryAllByDataTestId,
	(_, matcher) => `Found multiple elements by: [data-testid=${matcher}]`,
	(_, matcher) => `Unable to find an element by: [data-testid="${matcher}"]`
);

/**
 * Alternative: We could also use getElementsByTagName("*"). Which one is better suited for us?
 * See https://stackoverflow.com/a/30921553
 */
const queryAllByPredicate = (
	container: HTMLElement,
	matcher: MatcherFunction
): HTMLElement[] => {
	return Array.from(container.querySelectorAll("*")).filter(
		(e: Element): e is HTMLElement =>
			e instanceof HTMLElement && matcher("PARAMETER_NOT_USED", e)
	);
};
const [
	queryByPredicate,
	getAllByPredicate,
	getByPredicate,
	findAllByPredicate,
	findByPredicate
] = buildQueries(
	queryAllByPredicate,
	() => `Found multiple elements by given predicate`,
	() => `Unable to find an element by given predicate`
);

export const queries = {
	...rtlQueries,

	// by id
	queryById,
	queryAllById,
	getById,
	getAllById,
	findById,
	findAllById,

	// by data role
	queryByDataRole,
	queryAllByDataRole,
	getByDataRole,
	getAllByDataRole,
	findAllByDataRole,
	findByDataRole,

	// by data-testid
	queryByDataTestId,
	queryAllByDataTestId,
	getAllByDataTestId,
	getByDataTestId,
	findAllByDataTestId,
	findByDataTestId,

	// by predicate
	queryByPredicate,
	queryAllByPredicate,
	getByPredicate,
	getAllByPredicate,
	findAllByPredicate,
	findByPredicate
};
