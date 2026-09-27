# Public GitHub launch preparation

Public repository: [yavonalabs/vectis](https://github.com/yavonalabs/vectis), created on 27 September 2026. The reviewed history was pushed to `master`, and GitHub's branch SHA matched local commit `d75796f14c87da5b781e2c270ed86d42b014f865`. GitHub is the `github` remote; GitLab remains `origin` because Render deploys from it. Neither remote is an automatic mirror: push approved changes to both explicitly.

GitHub Issues and pull requests are the public contribution channels. Private vulnerability reporting is enabled. The [initial GitHub Verify workflow](https://github.com/yavonalabs/vectis/actions/runs/36331447911) passed on `d75796f`. Its Node 20 deprecation warnings prompted upgrading checkout and setup-java to v5; check [Actions](https://github.com/yavonalabs/vectis/actions) for verification of subsequent commits.

Prepared: accurate preview README, approved vector identity, contribution guidance, vulnerability reporting guidance, issue/PR templates and Java 17 Maven CI. The README no longer advertises an unimplemented enterprise edition or a published Maven artifact.

## Review performed

On 27 September 2026, all eight locally reachable commits were scanned with targeted patterns for private keys, GitHub/GitLab tokens, AWS access-key IDs and long API/client-secret assignments. No matches were found. Historical filenames were checked for common credential/key-store files, with no matches. Public sample credentials are intentional and documented. This is a bounded review, not a guarantee that no secret or private material exists; the patterns do not detect every credential format.

The previous LICENSE contained only the Apache notice. Its attribution is preserved in NOTICE, and the complete Apache 2.0 license text is now included.

## Publication checks

1. Confirm the signed-in GitHub owner and destination. An unauthenticated 404 does not establish whether a repository is private or absent.
2. Create the agreed public repository, without an auto-generated initial commit. Do not modify unrelated repositories.
3. Push the reviewed branch without force and compare remote HEAD with the local commit.
4. Confirm that CI passes on GitHub; local test results are not GitHub CI results.
5. Enable private vulnerability reporting where available, set an accurate repository description and topics, and decide where new issues and contributions will be handled.
6. Keep the product-page demo promotion pending its hosted accessibility and custom-domain gates. Publish a tagged/Maven release only after its separate release checks.
