# Prepare Models Plugin

Gradle plugin for preparing a flat, runtime-ready models folder from a nested model workspace.

## Overview

The Prepare Models Plugin transforms A12 model workspaces into flat, runtime-ready structures suitable for dev apps in mock mode or for testing. It handles model expansion, validation code generation, and migration.

## Features

- 📁 **Flat structure generation** - Copies models from nested workspace to flat output directory
- 📄 **Document model expansion** - Replaces document models with expanded variants
- ✅ **Validation code generation** - Creates validation code for expanded models
- 🔄 **Model migration** - Migrates document models to latest version
- ⚡ **Incremental builds** - Gradle task-level up-to-date checking skips the WCF conversion run when inputs are unchanged
- 🔗 **Model references** - WCF resolves document-model includes/references internally during full workspace conversion

## Installation

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.mgmtp.a12.devtools.plugins.prepare-models' version '<version>'
}
```

## Prerequisites

The main `prepareModels` flow needs no extra buildscript dependencies — `convertWorkspaceModels` resolves the `prepare-models-validation-converter` library and its transitive dependencies at build time and runs the WCF conversion pipeline in a forked JVM.

The `migrateDocumentModels` helper task is the exception: it runs the kernel migration CLI from the buildscript classpath, so projects that use it must provide the A12 kernel library `kernel-md-facade` as a buildscript dependency (matching the kernel version that supports the document models in the workspace):

```groovy
buildscript {
    dependencies {
        classpath 'com.mgmtp.a12.kernel:kernel-md-facade:<version>'
    }
}
```

## Usage

When applying the plugin, the following tasks are registered in your project:

- convertWorkspaceModels - converts the workspace via the WCF library (expands document models; generates validation code when `generateValidationCode` is enabled)
- copyOtherFiles - copies all files that are not Document Models from the workspace to the output directory in a flat structure
- mergeOutputs - merges the WCF conversion output (expanded models + validation code) into the final output directory
- prepareModels - does all of the above. This is the recommended task to use in your build script.

The `mergeOutputs` task consolidates the WCF conversion output (`build/expanded-code`) and the other-files output into the final output directory. This design enables:

- **Incremental builds**: Each task writes to its own dedicated output directory, so Gradle's up-to-date checking applies per task
- **Proper output tracking**: Gradle can track which task produced which files
- **Correct up-to-date checking**: Changes in one processing step don't invalidate outputs from other steps
- **Build cache compatibility**: Separate outputs can be cached independently

Additionally, following helper tasks are provided:

- migrateDocumentModels - migrates all Document Models to the latest version in the given input directory

### Configuration

The following properties can be configured via the 'prepareModels' extension:

- inputPath - string of the input directory path - defaults to "\<projectDirectory.path\>"
- outputPath - string of the output directory path - defaults to "\<projectDirectory.path\>/../../target/models"
- eachFileAction - custom action to transform file paths when copying other files with `copyOtherFiles` - defaults to flattening files (copying only the file name, not the directory structure)
- generateValidationCode - boolean flag to generate validation code inside the WCF conversion pipeline - defaults to `false` (see [In-converter validation code generation](#in-converter-validation-code-generation) below)
- kernelMdFacadeVersion - version of `kernel-md-facade` used for validation code generation in the forked JVM — auto-detected from the buildscript classpath when present; if neither this property is set nor kernel is on the buildscript classpath, the forked JVM relies on the transitive kernel version from wcf-core (which may not match your runtime); must match the kernel version used at runtime
- validationConverterVersion - version of the `prepare-models-validation-converter` library resolved onto the forked JVM classpath - defaults to the version this plugin was built against

#### Example Configuration

```groovy
prepareModels {
    // for example, flatten only specific subdirectories
    eachFileAction.set({ fileCopyDetails ->
        if (fileCopyDetails.path.startsWith('common/')) {
            fileCopyDetails.path = fileCopyDetails.name
        }
    } as Action)
}
```

#### In-converter validation code generation

`convertWorkspaceModels` runs the WCF conversion pipeline in a forked JVM. The plugin resolves the
`prepare-models-validation-converter` library and its transitive dependencies (WCF core, the RMC
converter pipeline, kernel validation codegen, Spring Boot) onto a single classpath and launches
`WcfConversionLauncher`, which boots a Spring context, discovers every `@WcfConverter` (RMC's pipeline
plus the validation-code converter) by component scan, and runs them in order. When
`generateValidationCode` is `true`, the launcher is started with
`-Dvalidation.codegen.enabled=true` so the validation-code converter emits `*.validation.js` files.

With the flag off (default), `prepareModels` produces no validation code.

```groovy
prepareModels {
    generateValidationCode = true
    // optional: pin the validation-converter library version
    // validationConverterVersion = '0.2.0'
}
```

When `kernelMdFacadeVersion` is not set, the plugin auto-detects the kernel version from the
project's buildscript classpath. Projects that already declare `kernel-md-facade` as a buildscript
dependency (required for `migrateDocumentModels`) get the correct version automatically. To
override explicitly:

```groovy
prepareModels {
    generateValidationCode = true
    kernelMdFacadeVersion = '32.0.0'  // must match runtime kernel version
}
```

> If neither `kernelMdFacadeVersion` nor a buildscript `kernel-md-facade` dependency is present,
> the forked JVM still receives kernel transitively from `wcf-core` — but the version will be
> whatever `wcf-core` carries, which may not match your runtime kernel. For correct generated code
> compatibility, always ensure kernel is discoverable via one of the two paths above.

Notes:

- Default is `false`. When disabled, the validation-code converter is still on the classpath but
  does not emit any files.
- The WCF and RMC versions are **fixed transitively by the `prepare-models-validation-converter`
  library**; there is no separate `wcfCliVersion` or `rmcConverterVersion` knob. The kernel version, however, is configurable via `kernelMdFacadeVersion` and auto-detected from the buildscript classpath when not set.

## Architecture

### ConvertWorkspaceModelsTask

`ConvertWorkspaceModelsTask` is the sole model-processing engine. It resolves the
`prepare-models-validation-converter` library and its transitive dependencies onto a forked JVM
classpath and launches `WcfConversionLauncher`. The launcher boots a Spring context, discovers every
`@WcfConverter` (RMC's pipeline plus the validation-code converter) by component scan, and runs them
in order. When `generateValidationCode` is enabled, it passes
`-Dvalidation.codegen.enabled=true` so the validation-code converter emits `*.validation.js` files.
