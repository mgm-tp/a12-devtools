# Build Utilities Plugin

Gradle plugin providing build utilities for A12 projects.

## Overview

The Build Utilities Plugin offers a collection of reusable task classes and helper utilities for common build operations in A12 projects, including command-line execution, package.json manipulation, and version management.

## Features

- 🛠️ **LeadingBuildTask** - Base task class for executing command-line operations
- 📝 **PackageJsonModifierTask** - Task for modifying package.json files
- 🔢 **Version utilities** - Version calculation and NPM dist tag classification
- 📋 **Helper utilities** - JSON formatting, file operations, and more

## Installation

Add the plugin to your `build.gradle`:

```groovy
plugins {
    id 'com.mgmtp.a12.devtools.plugins.build-utilities' version '<version>'
}
```

## Prerequisites

- Gradle 8+
- Java 21+

## Configuration

No additional configuration required. The plugin provides utility classes for use in your build scripts.

## Usage

Import the classes you need:

```groovy
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.Helper
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.tasks.LeadingBuildTask
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.tasks.PackageJsonModifierTask
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.utils.VersionUtils
```

### Example: Using VersionUtils

```groovy
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.utils.VersionUtils

task checkVersion {
    doLast {
        def distTag = VersionUtils.getNpmDistTagType('1.2.3')
        println "NPM dist tag: ${distTag}"  // Output: latest
    }
}
```

### Example: Creating a Custom Command Task

```groovy
import com.mgmtp.a12.devtools.gradle.plugins.buildutils.tasks.LeadingBuildTask

task myCustomCommand(type: LeadingBuildTask) {
    commandLine = ['echo', 'Hello World']
}
```
