# A12 Integration Plugin

Provides the three standardized tasks the BD integration build calls on each of our
components. Convention-first: apply the plugin at the repo root.

Plugin id: `com.mgmtp.a12.devtools.plugins.integration`

## Required build setup

For the plugin to work out-of-the-box, a component repo must have:

1. **pnpm**: all A12 dependency versions declared in the `pnpm-workspace.yaml` catalogs
   (`a12` and `a12ranges`), with every `package.json` referencing them via `catalog:`.
   Repos without an npm side may omit the `pnpm-workspace.yaml` entirely — the npm-related
   steps are then skipped.
2. **gradle**: all A12 dependency versions declared in `gradle/libs.versions.toml`.
3. The component's own version declared in the root `package.json`.
4. The standard `build`, `npmPublish`, maven `publish`, and `setVersion` tasks.

## Tasks (group `integration build`)

### `prepareForIntegration`

Rewrites the versions of the A12 dependencies this repo consumes, then prints this
component's own version (so later pipeline steps know what to build/publish).

- Optional param `-Pa12DependencyVersions=comp=ver,comp2=ver` — only the components
  passed are rewritten; the rest are left as-is.
- npm deps are rewritten in `pnpm-workspace.yaml` — in the pnpm default catalog (the
  top-level `catalog:` map) and the `a12` and `a12ranges` named catalogs, plus any matching
  `overrides`. Repos that keep A12 versions in further named catalogs can have those
  rewritten too via `integration.additionalPnpmCatalogs = ['myCatalog']`. The file is parsed
  and re-serialized as YAML, so comments and formatting are **not** preserved — that is fine,
  because the integration pipeline rewrites it only to build and publish and never commits
  the result.
- gradle A12 deps are rewritten in `gradle/libs.versions.toml` by their version key,
  exactly like npm deps, for any component that has one (e.g. `dataservices`,
  `prepare-models`). Repos that keep their version catalog elsewhere can point the plugin
  at a different file:

  ```groovy
  integration {
      libsVersionsFile = layout.projectDirectory.file('gradle/a12.versions.toml')
  }
  ```

- Prints this component's version at QUIET level:
  `gradle prepareForIntegration -q` → `29.5.0-build.20260405120000.integration`.
  The build identifier is the UTC time (`yyyyMMddHHmmss`) at which the task ran, not the
  Jenkins build number: the integration build chains components from separate jobs whose
  build numbers are unrelated and collide across runs. The task writes that version into
  the root `package.json` as well, so the pipeline's later gradle invocations reuse it
  instead of computing a new timestamp.

```
gradle prepareForIntegration -Pa12DependencyVersions=widgets=39.1.1-build.xxx,base=29.3.0-build.xxx
```

### `buildAndTestForIntegration`

Builds and runs the integration-relevant tests. By default it runs `build` in **every**
project, exactly like typing `gradle build` — so a multi-project repo gets its
subprojects' `test` tasks too, not just the root lifecycle task.

Narrow it per repo without changing the task name:

```groovy
integration {
    buildTasks = ['assemble', 'someFasterCheck']
}
```

An explicit list is wired verbatim and is **not** fanned out: each entry is a task path
resolved against the root project, so `'build'` means `:build` alone. Name subproject
tasks explicitly if you want them:

```groovy
integration {
    buildTasks = ['assemble', ':form-model:test']
}
```

### `publishForIntegration`

Publishes the artifacts needed for integration. By default it depends on the repo's npm
publish (`npmPublish`) and maven publish (`publish`) tasks, auto-detected: `npmPublish`
always, maven `publish` only where `maven-publish` is applied. It does not wire the DevApp
docker image or documentation tasks, so those are not published by this task.

A repo that publishes differently (e.g. does not use the A12 artifact-publish plugin) can
take full control of the publish channels:

```groovy
integration {
    publishTasks = ['myCustomNpmPublish', 'myCustomMavenPublish']
}
```

Setting `publishTasks` to an _empty_ list is an explicit configuration, too: auto-detection
is switched off and `publishForIntegration` runs without publishing anything:

```groovy
integration {
    publishTasks = []
}
```

Only leaving `publishTasks` untouched keeps the auto-detection.

## Additional components

Repos that consume A12 components not covered by the built-in catalog can register
extra mappings via `additionalComponents`. Entries are merged with (and override) the
built-in `A12ComponentCatalog` and participate in both the pnpm catalog rewrite and
the gradle toml rewrite:

```groovy
integration {
    additionalComponents = [
        'my-component': [npm: ['@com.mgmtp.a12.myns/my-package'], toml: ['my-component']],
        'npm-only'    : [npm: ['@com.mgmtp.a12.other/other-pkg'], toml: []],
    ]
}
```

Repos following the standard conventions do not need to set this — it exists for repos that have
custom or less common A12 dependencies.

## Component → coordinate mapping

BD component names map to their exact npm coordinates and gradle toml version-keys via
an explicit map maintained in `A12ComponentCatalog`. Each entry lists the precise npm
package coordinates (pnpm catalog keys) and, where applicable, the `[versions]` keys in
`gradle/libs.versions.toml` whose version tracks that component — e.g. `client` maps to
`@com.mgmtp.a12.client/client-core` and `@com.mgmtp.a12.client/client-data`;
`dataservices` maps to the npm coordinate `@com.mgmtp.a12.dataservices/dataservices-access`
and the gradle toml key `dataservices`; `prepare-models` maps only to the gradle toml key
`prepare-models` (no npm coordinate). There is no scope-based derivation — membership is
explicit, so a package simply is or isn't listed under a component.
