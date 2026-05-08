# Prepare Models Plugin

Gradle plugin for preparing a flat, runtime-ready models folder from a nested model workspace.

## Overview

The Prepare Models Plugin transforms A12 model workspaces into flat, runtime-ready structures suitable for dev apps in mock mode or for testing. It handles model expansion, validation code generation, and migration.

## Features

- 📁 **Flat structure generation** - Copies models from nested workspace to flat output directory
- 📄 **Document model expansion** - Replaces document models with expanded variants
- ✅ **Validation code generation** - Creates validation code for expanded models
- 🔄 **Model migration** - Migrates document models to latest version
- ⚡ **Incremental builds** - Only processes changed files
- 🔗 **Dependency tracking** - Automatically reprocesses dependent models

## Installation

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.mgmtp.a12.devtools.plugins.prepare-models' version '<version>'
}
```

## Prerequisites

This plugin requires the following A12 kernel libraries to be available as buildscript dependencies:

- "com.mgmtp.a12.kernel:kernel-md-model"
- "com.mgmtp.a12.kernel:kernel-md-facade"
- "com.mgmtp.a12.kernel:kernel-tool-model-migration"

The kernel version should be the one supporting the document models in the workspace.

```groovy
buildscript {
    dependencies {
        classpath 'com.mgmtp.a12.kernel:kernel-md-model:<version>'
        classpath 'com.mgmtp.a12.kernel:kernel-md-facade:<version>'
        classpath 'com.mgmtp.a12.kernel:kernel-tool-model-migration:<version>'
    }
}
```

## Usage

When applying the plugin, the following tasks are registered in your project:

- generateValidationCode - creates the validation code for the Document Models
- copyOtherFiles - copies all files that are not Document Models from the workspace to the output directory in a flat structure
- expandDocumentModels - creates the expanded Document Models in the output directory
- mergeOutputs - merges the separate task outputs into the final output directory
- prepareModels - does all of the above. This is the recommended task to use in your build script.

The `mergeOutputs` task is a simple copy task that consolidates the outputs from the separate processing tasks into the final output directory. This design enables:

- **Incremental builds**: Each processing task writes to its own dedicated output directory
- **Proper output tracking**: Gradle can track which task produced which files
- **Correct up-to-date checking**: Changes in one processing step don't invalidate outputs from other steps
- **Build cache compatibility**: Separate outputs can be cached independently

Additionally, following helper tasks are provided:

- migrateDocumentModels - migrates all Document Models to the latest version in the given input directory

### Configuration

The following properties can be configured via the 'prepareModels' extension:

- inputPath - string of the input directory path - defaults to "\<projectDirectory.path\>"
- outputPath - string of the output directory path - defaults to "\<projectDirectory.path\>/../../target/models"
- enableLog - boolean flag to enable detailed logging output - defaults to `false`
- eachFileAction - custom action to transform file paths when copying other files with `copyOtherFiles` - defaults to flattening files (copying only the file name, not the directory structure)

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

## Architecture

### ModelConversionTask (Base Class)

The abstract base class for document model processing tasks. It provides:

- **Incremental build support**: Only processes changed files using Gradle's `InputChanges` API
- **Dependency tracking**: Automatically reprocesses dependent files when an included model changes
- **JavaExec wrapper**: Delegates to A12 kernel CLI tools for the actual model processing

**Key responsibilities:**

- Monitors the input directory for changes (added/modified/removed document models)
- Reads the dependency cache to find dependent models
- Builds command-line arguments for the kernel tools
- Only executes when there are actual changes to process

### UpdateDependenciesTask

A helper task that builds and maintains a dependency graph of document models.

**Purpose:**
Document models can include other models using `modelReferences`. When a base model changes, all models that include it must be reprocessed. This task:

- Scans document models for `include` references
- Builds a reverse dependency map (which models depend on which)
- Stores this information in a cache file (`target/includes`)

**Example:**
If `model-a.json` includes `model-b.json`, the cache records that `model-b.json` is a dependency of `model-a.json`. When `model-b.json` changes, `model-a.json` must also be expanded/regenerated.

### Why the Extension Pattern?

Both expansion and validation code generation follow the same pattern:

- Watch for file changes incrementally
- Track dependencies between models
- Execute a kernel CLI tool
- Only process what changed
