# A shared Prettier config for A12 projects

Disclaimer: This config is for internal usage of A12 projects. A12 does not provide any support for this package.

## Installation

Prettier is required for this package to work. To install this package and prettier, run the following command in the terminal in the root directory of your application.

```bash
npm install --save-dev prettier @com.mgmtp.a12.devtools/prettier-config
```

## Usage

Add a key in your **package.json** file.

```bash
"prettier": "@com.mgmtp.a12.devtools/prettier-config"
```

**OR**

Create a **.prettierrc** , **.prettierrc.yaml** , **.prettierrc.yml** or **.prettierrc.json** file and export a string.

```bash
"@com.mgmtp.a12.devtools/prettier-config"
```

**OR**

Create a **prettier.config.js** or **.prettierrc.js** file and export an object.

```bash
module.exports = {
  ...require("@com.mgmtp.a12.devtools/prettier-config"),
  // endOfLine: 'lf', // to overwrite the property
};
```

**ESLint Configuration**

To use Prettier with ESLint, you will need to suppress all ESLint formatting rules that conflict with Prettier. This can be done easily by installing

```json
    "eslint-config-prettier": "^8.1.0",
```

and extends the ESLint configuration with "prettier" config.

```js
	extends: [
		"@com.mgmtp.a12.devtools/eslint-config/eslint-config-react",
                ...,
                // prettier should be the last entry
                "prettier"
	]
```

**A build step to check the formatting is also recommended:**

```json
  "scripts": {
    "prettier": "prettier --check \"{widgets,showcase}/src/**/*.{ts,tsx}\""
}
```
