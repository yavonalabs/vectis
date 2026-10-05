# Contributing to Vectis

Start with a small, reproducible problem or an actual operator workflow. For a substantial feature, discuss the proposed behavior in an issue before implementing it.

## Local development

Use Java 17 and Maven. From the repository root, run `mvn verify`. To try the sample, run the packaged sample JAR with the `demo` profile as described in the README. Use fictional data only.

For action-dialog JavaScript changes, also run `node --test scripts/action-modal.test.cjs` with Node.js 18 or newer. These controller tests supplement browser checks. Run `python scripts/smoke_demo.py http://localhost:8080` against the disposable running sample to check real login, CSRF, preview permissions and logout.

## Pull requests

- Explain the user-visible problem and resulting behavior.
- Add regression coverage for changes to permissions, query parsing, navigation, mutation behavior and audit outcomes.
- For UI changes, check desktop, 320px and 375px layouts, keyboard access and visible feedback. Include relevant screenshots without private data.
- Run the relevant Maven checks and report any checks you could not perform.
- Keep permission enforcement on the server even when controls are hidden in the UI.

Do not commit credentials, generated build output or logs containing customer information. Report suspected vulnerabilities privately as described in SECURITY.md.

Contributions are made under the repository's Apache License 2.0. No response-time or merge commitment is implied.
