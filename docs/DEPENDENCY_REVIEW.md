# Candidate dependency review — 9 October 2026

The asset builder now pins Tailwind 3.4.19 and overrides `postcss-selector-parser` to 7.1.6. The [selector-parser advisory](https://github.com/advisories/GHSA-rj75-hqrm-r3gf) identifies 7.1.6 as fixed. Asset rebuilding and JavaScript regression checks pass with that override. This retains the existing Tailwind 3 architecture and generated utility design.

The current full npm audit still reports five high-severity package findings: braces, chokidar, micromatch, fast-glob and tailwindcss. They trace to the single [braces recursive-pattern advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm). Upstream lists no patched braces version as of this review. A zero-advisory audit is not claimed.

## Exposure and handling

Braces is used by the repository's asset-build dependency graph to process source patterns. It is not a browser asset and is not a Java request-path dependency. `scripts/build-assets.cjs` reads repository-controlled templates/configuration; customer queries, record values, exports and uploaded styles are not passed to that builder. Maven consuming applications use the committed generated assets and need no Node installation. The Docker runtime runs the Java application, without the Node build graph.

Treat asset sources/configuration and dependency updates as trusted code. Do not add a service that compiles customer-supplied CSS, globs or templates using this graph. Build an untrusted branch only in isolated CI without production secrets, as it can already execute Maven/Node code. This restriction bounds the identified exposure; it is not a patch to braces.

`npm audit --omit=dev` reports no findings in its production graph, but this does not certify the shipped Alpine/htmx/font assets: those are declared as development dependencies and intentionally copied into the Java resource package. Their versions and license files are separately pinned. Review future runtime-library advisories independently.

Logs: `target/freeze-reviewed-audit.json`, `target/freeze-production-audit.json`, `target/freeze-dependency-assets.log`, `target/freeze-dependency-js.log`. Remaining braces findings stay visible for release review; do not silence them or use `npm audit fix --force` to introduce an unverified major CSS migration. An independent reviewer/product owner must assess this documented residual build risk before release approval.
