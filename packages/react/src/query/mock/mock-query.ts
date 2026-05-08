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

import { notStrictEqual, ok, strictEqual } from "node:assert/strict";
import type { Mock } from "node:test";

import deepEqual from "fast-deep-equal";
import type { ComponentType } from "react";

export function query<C, T extends Record<keyof C, unknown>>(
	component: ComponentType<T>
): QueryBuilder<T> {
	assertNodeMock(component);
	return new QueryBuilderImpl<T>(component);
}

interface QueryAssertions<T extends Record<string, unknown>> {
	readonly props: () => T;
	readonly maybeProps: () => T | undefined;
	readonly propsHistory: () => T[];
	readonly maybePropsHistory: () => T[];
	readonly assertRenderedTimes: (times: number, msg?: string) => void;
	readonly assertRendered: (msg?: string) => void;
	readonly assertNotRendered: (msg?: string) => void;
}

interface QueryWithProp<T extends Record<string, unknown>>
	extends QueryAssertions<T>, GroupByProp<T> {
	/**
	 * Selects all components where the prop identified by the key matches the given predicate.
	 */
	readonly withPropMatching: (
		key: keyof T & string,
		matches: (value: unknown) => boolean
	) => QueryWithProp<T>;
	/**
	 * Selects all components where the prop identified by the key is deep equal to the value.
	 */
	readonly withProp: (
		key: keyof T & string,
		value: unknown
	) => QueryWithProp<T>;
	/**
	 * Selects all components where the the id prop is referentially equal to the given id.
	 */
	readonly withId: (id: string) => QueryWithProp<T>;
	/**
	 * Selects all components where the the data-testid prop starts with the given id.
	 */
	readonly withTestId: (id: string) => QueryWithProp<T>;
}

interface QueryGroupAssertions<T extends Record<string, unknown>> {
	readonly assertSize: (size: number, msg?: string) => void;
	readonly at: (index: number) => QueryAssertions<T>;
}

interface GroupByProp<T extends Record<string, unknown>> {
	/**
	 * Groups all mock invocations by some property. This can be used to query components by index.
	 * Querying by index is not preferred as it relies on model structure and rendering order, which can change.
	 */
	readonly groupBy: (key: string) => QueryGroupAssertions<T>;
	/**
	 * Groups all mock invocations by their id. This can be used to query components by index.
	 * Querying by index is not preferred as it relies on model structure and rendering order, which can change.
	 */
	readonly groupById: () => QueryGroupAssertions<T>;
	/**
	 * Groups all mock invocations by their test id. This can be used to query components by index.
	 * Querying by index is not preferred as it relies on model structure and rendering order, which can change.
	 */
	readonly groupByTestId: () => QueryGroupAssertions<T>;
}

interface MockOperations {
	readonly resetHistory: () => void;
}

type QueryBuilder<T extends Record<string, unknown>> = QueryWithProp<T> &
	GroupByProp<T> &
	MockOperations;

type NodeMock<T> = Mock<(props: T) => void>;

type PropValueFilter = {
	key: string;
	matches: (value: unknown) => boolean;
	matchDesc?: string;
};

type QueryBuilderProps = {
	readonly propsFilter?: readonly PropValueFilter[];
	readonly propsGroupBy?: string;
	readonly groupIndex?: number;
};

class QueryBuilderImpl<
	T extends Record<string, unknown>
> implements QueryBuilder<T> {
	private readonly mockComponent: NodeMock<T>;
	private readonly propsFilter: readonly PropValueFilter[];
	private readonly propsGroupBy?: string;
	private readonly groupIndex?: number;

	constructor(component: NodeMock<T>, updates?: QueryBuilderProps) {
		this.mockComponent = component;
		this.propsFilter = updates?.propsFilter ?? [];
		this.propsGroupBy = updates?.propsGroupBy;
		this.groupIndex = updates?.groupIndex;

		// allow functional usage of methods
		bindMethods(this);
	}

	public resetHistory() {
		this.mockComponent.mock.resetCalls();
	}

	public withPropMatching(
		key: keyof T & string,
		matches: (value: unknown) => boolean,
		matchDesc?: string
	) {
		return this.copy({
			propsFilter: this.propsFilter.concat({ key, matches, matchDesc })
		});
	}

	public withProp(key: keyof T & string, value: unknown) {
		return this.withPropMatching(
			key,
			propValue => deepEqual(propValue, value),
			`=${value}`
		);
	}

	public withId(id: string) {
		return this.withProp("id", id);
	}

	public withTestId(id: string) {
		const matches = (value: unknown) => String(value).startsWith(id);
		return this.withPropMatching("data-testid", matches, `=${id}`);
	}

	public groupBy(key: string) {
		return this.copy({
			propsGroupBy: key
		});
	}

	public groupById() {
		return this.groupBy("id");
	}

	public groupByTestId() {
		return this.groupBy("data-testid");
	}

	public at(index: number) {
		return this.copy({
			groupIndex: index
		});
	}

	public props(): T {
		this.assertRendered();
		// eslint-disable-next-line @typescript-eslint/no-non-null-assertion
		return this.maybeProps()!;
	}

	public maybeProps(): T | undefined {
		return this.filteredProps().at(-1);
	}

	public propsHistory(): T[] {
		this.assertRendered();
		return this.filteredProps();
	}

	public maybePropsHistory(): T[] {
		return this.filteredProps();
	}

	public assertRenderedTimes(
		times: number,
		msg = `Incorrect amount of renders of ${this.toString()}`
	): void {
		strictEqual(this.filteredProps().length, times, msg);
	}

	public assertRendered(msg = `Expected a render of ${this.toString()}`): void {
		ok(this.filteredProps().length > 0, msg);
	}

	public assertNotRendered(msg?: string): void {
		this.assertRenderedTimes(0, msg);
	}

	public assertSize(
		size: number,
		msg = `Incorrect amount of ${this.toString()}`
	): void {
		strictEqual(this.groupedProps().length, size, msg);
	}

	private copy(updates: QueryBuilderProps): QueryBuilderImpl<T> {
		return new QueryBuilderImpl<T>(this.mockComponent, {
			propsFilter: updates.propsFilter ?? this.propsFilter,
			propsGroupBy: updates.propsGroupBy ?? this.propsGroupBy,
			groupIndex: updates.groupIndex ?? this.groupIndex
		});
	}

	private toString(): string {
		const propsOfAllRenders = this.filteredPropsToString();
		return `${this.filterToString()}${propsOfAllRenders ? ", Options: " : ""}${propsOfAllRenders}`;
	}

	private filterToString(): string {
		const groupByString = this.propsGroupBy
			? `Grouped by: ${this.propsGroupBy}`
			: undefined;
		const filteredString =
			this.propsFilter.length > 0
				? `Filtered by: ${this.propsFilter.map(filter => `${filter.key}${filter.matchDesc ?? ""}`).join(", ")}`
				: undefined;
		return [toString(this.mockComponent), groupByString, filteredString]
			.filter(notUndefined)
			.join(", ");
	}

	private filteredPropsToString(): string {
		const propValuesToString = (props: T) => {
			return (filter: PropValueFilter) => `${filter.key}=${props[filter.key]}`;
		};

		const propsToString = (props: T, index: number) => {
			const valueToString = propValuesToString(props);
			const propValues = this.propsFilter.map(valueToString).join(", ");
			return propValues ? `Call ${index}: ${propValues}` : undefined;
		};

		return this.unfilteredProps()
			.map(propsToString)
			.filter(notUndefined)
			.join(", ");
	}

	private unfilteredProps(): T[] {
		return this.mockComponent.mock.calls.map(c => c.arguments[0]);
	}

	public groupedProps(filteredProps: T[] = this.filteredProps()): T[][] {
		return Array.from(
			Map.groupBy(filteredProps, item => {
				assertExists(this.propsGroupBy);
				return item[this.propsGroupBy];
			}).values()
		);
	}

	private filteredProps(): T[] {
		const matchesFilters: (props: T) => boolean = props =>
			this.propsFilter.every(filter => filter.matches(props[filter.key]));

		const filteredProps = this.unfilteredProps().filter(matchesFilters);
		if (this.groupIndex !== undefined) {
			const props = this.groupedProps(filteredProps)[this.groupIndex];
			notStrictEqual(
				props,
				undefined,
				`There exists no group ${this.groupIndex} of ${this.toString()}`
			);
			return props;
		}
		return filteredProps;
	}
}

function assertExists<T>(
	value: T,
	message = "Expected value to exist"
): asserts value is NonNullable<T> {
	if (value === null || value === undefined) {
		throw new Error(message);
	}
}

function notUndefined<T>(value: T | undefined): value is T {
	return value !== undefined;
}

function assertNodeMock<T>(
	candidate: unknown
): asserts candidate is NodeMock<T> {
	ok(
		// eslint-disable-next-line @typescript-eslint/no-explicit-any
		!!(candidate as any).mock, // candidate can be a proxy, so don't use property enumeration (e.g. "in", "Object.keys")
		`Expected ${toString(candidate)} to be a node mock`
	);
}

function toString(candidate: unknown): string {
	return isFunction(candidate) ? candidate.name : String(candidate);
}

function isFunction(candidate: unknown): candidate is () => unknown {
	return typeof candidate === "function";
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function bindMethods(obj: any) {
	// Get all defined class methods
	const methods = Object.getOwnPropertyNames(Object.getPrototypeOf(obj));

	// Bind all methods
	methods
		.filter(method => method !== "constructor")
		.forEach(method => {
			obj[method] = obj[method].bind(obj);
		});
}
