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

import type { CommonExecOptions } from "node:child_process";
import process from "node:process";

import { execSyncWithError } from "../utils/exec-sync.js";

import type { Command } from "./interfaces.js";

/**
 * Resolves which environment variable's value should drive the script suffix.
 * Defaults to 'NODE_ENV' when no argument was given on the command line.
 */
export function resolveEnvVarName(envVarArg: unknown): string {
	return typeof envVarArg === "string" && envVarArg.length > 0
		? envVarArg
		: "NODE_ENV";
}

/**
 * Resolves the suffix to append to the script name for the given environment variable.
 * 'NODE_ENV' keeps its historical default of 'development'; any other variable that is
 * not set falls back to the suffix 'unset' instead.
 */
export function resolveSuffix(
	envVarName: string,
	env: NodeJS.ProcessEnv = process.env
): string {
	return (
		env[envVarName] ?? (envVarName === "NODE_ENV" ? "development" : "unset")
	);
}

/**
 * Runs a npm script based on an environment variable by appending its value to the current
 * script name. Defaults to 'NODE_ENV' (itself defaulting to 'development') but a different
 * variable name can be passed as an argument.
 */
export const perEnv: Command = {
	name: "per-env",
	description:
		"Runs a npm script based on an environment variable by appending it to the current script name. Uses 'NODE_ENV' by default, sets it to 'development' if unset. Pass a different variable name as an argument to use that instead; if it is unset, the suffix 'unset' is used.",
	arguments: [
		[
			"[envVar]",
			"Name of the environment variable whose value is appended as the script suffix (defaults to NODE_ENV)"
		]
	],
	action(envVarArg?: unknown) {
		const envVarName = resolveEnvVarName(envVarArg);
		const suffix = resolveSuffix(envVarName);
		const isNodeEnv = envVarName === "NODE_ENV";

		const options: CommonExecOptions = {
			env: isNodeEnv ? { ...process.env, NODE_ENV: suffix } : process.env,
			stdio: "inherit"
		};

		const script = `${process.env.npm_lifecycle_event}:${suffix}`;
		const rawArgs = process.argv.slice(2);
		const consumedIndex =
			typeof envVarArg === "string" ? rawArgs.indexOf(envVarArg) : -1;
		const extraArgs = rawArgs
			.filter((_, index) => index !== consumedIndex)
			.join(" ");
		const command = `npm run ${script} ${extraArgs}`;

		execSyncWithError(command, options);
	}
};
