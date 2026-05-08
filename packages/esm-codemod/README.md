# ESM Codemod

A CLI tool to convert TypeScript import statements to ESM-compatible syntax

## Requirements

Node v22

## Usage

```bash
npx @com.mgmtp.a12.devtools/esm-codemod <absolute-path-to-file-or-folder>
```

```bash
Options:
  --version            Show version number                                                                                         [boolean]
  --alias              Comma-separated tsconfig alias mappings: alias1=absoluteFilePath1,alias2=absoluteFilePath2                   [string]
  --ignore-packages    Comma-separated list of import packages to ignore                                              [string] [default: []]
  --ignore-files       Comma-separated file patterns to ignore. E.g: '/**/*.skip-test/*'                              [string] [default: []]
  --ignore-extensions  Comma-separated list of import extensions to ignore. E.g: '.png,.svg'                 [string] [default: ".css,.svg"]
  --debug              Enable debug mode                                                                          [boolean] [default: false]
  --help               Show help                                                                                                   [boolean]
```
