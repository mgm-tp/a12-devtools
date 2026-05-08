# A shared [ESLint](https://eslint.org/) config for A12 projects

Disclaimer: This config is for internal usage of A12 projects. A12 does not provide any support for this package.

## What does this package contain?

This package contains four configurations for ESLint:

- `recommended` includes basic configuration that could be used in any A12 projects that use TypeScript. It exposes recommended rules from ESLint, [TypesScript ESLint](https://github.com/typescript-eslint/typescript-eslint) and a rule to organize imports of A12 projects.
- `reactRecommended` extends from the above configuration and includes recommended rules for a React based project.
- `strict` and `reactStrict` are stricter versions of two configs above, enforcing higher code quality standards.

## Installation

```bash
npm install @com.mgmtp.a12.devtools/eslint-config --save-dev
```

and also install/upgrade ESLint (at least v9) you haven't already.

If you are migrating from an older version (below 1.0.0) of this package,
the following dependencies can be removed, as they are now included within this package:

- `@typescript-eslint/eslint-plugin`
- `@typescript-eslint/parser`
- `eslint-plugin-import`
- `eslint-plugin-react`
- `eslint-plugin-react-hooks`

Please refer to the [ESLint Configuration Migration Guide](https://eslint.org/docs/latest/use/configure/migration-guide) to update configurations to the new flat structure and simplified setup.

## Usage

Create a **eslint.config.mjs** file and extend the shared config.

```javascript
import devtoolsConfigs from "@com.mgmtp.a12.devtools/eslint-config";

export default [
	...devtoolsConfigs.recommended
	// Add your project specific rules here
];
```

**OR** extend from the React config

```javascript
import devtoolsConfigs from "@com.mgmtp.a12.devtools/eslint-config";

export default [
	...devtoolsConfigs.reactRecommended
	// Add your project specific rules here
];
```
