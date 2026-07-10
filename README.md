<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/dark/A12-Dark.svg" />
  <img src="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/light/A12-Light.svg" height="200" alt="A12 logo" />
</picture>

# Devtools

Developer tools and utilities for the A12 platform development.

**Disclaimer:** Please note that these tools may not strictly adhere to A12's breaking change management policies and are subject to modification as required by the A12 development team.

Refer to https://geta12.com/#/docs to get started with A12 development

---

## License

Parts of the A12 platform are made available under a **dual license**.
Please check the [LICENSE](./LICENSE) file for details.

---

## Getting Started

### How to Use It

#### Import & Install

Refer to each plugin or package README for installation and usage instructions.

---

### How to Build and Run

#### Prerequisites

- **Java 21**
- **Gradle 9**
- **Node.js 24**
- **pnpm 11+**

#### How to Build

Build all Gradle plugins:

```bash
gradle build
```

Build NPM packages:

```bash
pnpm install
node --run compile
```

#### How to Test

Test Gradle plugins:

```bash
gradle test
```

Test NPM packages:

```bash
node --run test
```

#### How to Run

NPM scripts available at root level:

```bash
# Clean all build artifacts
node --run clean

# Compile all packages
node --run compile

# Start TypeScript watch mode for all packages
node --run start

# Test all packages
node --run test

# Lint code
node --run lint

# Format code
node --run format
```

#### How to Access It

This repository contains utility libraries and build plugins. There are no DevApps to run.

---

### Documentation

- Full technical documentation is available at [GetA12.com](https://GetA12.com).
- The website also provides access to the **A12 Discourse Community Forum**.

---

**The mgm A12 Team**

[mgm technology partners GmbH](https://www.mgm-tp.com) • [Imprint](https://www.mgm-tp.com/imprint.html)
