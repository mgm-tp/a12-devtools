# @com.mgmtp.a12.devtools/docu

Shared asciidoctor documentation builder for A12. Provides the `docu`
binary, run from a documentation package directory (its cwd), which:

- converts `src/index.adoc` → `build/index.html` with asciidoctor 4,
- highlights code via asciidoctor's built-in `highlightjs` (client-side: the page
  loads highlight.js from a CDN at view time),
- copies `src/assets/**` to `build/assets/**` if present,
- reads `version` and `author` from the local `package.json`.

Conventions (required): entry `src/index.adoc`, docinfo `src/docinfo.html`,
assets `src/assets/`, output `build/`.

## Optional config

Drop a `docu.config.ts` next to `package.json` to override the source/output
directories (default `src`/`build`, relative to the cwd) or asciidoctor
attributes. Use the `defineConfig` helper (exported from the package) for full
typing:

```ts
import { defineConfig } from "@com.mgmtp.a12.devtools/docu";

export default defineConfig({
	srcDir: "documentation",
	outDir: "dist/docs",
	attributes: { toclevels: 3 }
});
```
