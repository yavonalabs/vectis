# Public GitHub launch preparation

Intended repository: `yavonalabs/vectis`, subject to confirmation of account access and name availability. Keep GitLab's origin intact because Render deploys from it. Add GitHub as a separate remote initially; do not force-push or overwrite an existing repository.

Prepared: accurate preview README, approved vector identity, contribution guidance, vulnerability reporting guidance, issue/PR templates and Java 17 Maven CI. The README no longer advertises an unimplemented enterprise edition or a published Maven artifact.

## Review performed

On 27 September 2026, all eight locally reachable commits were scanned with targeted patterns for private keys, GitHub/GitLab tokens, AWS access-key IDs and long API/client-secret assignments. No matches were found. Historical filenames were checked for common credential/key-store files, with no matches. Public sample credentials are intentional and documented. This is a bounded review, not a guarantee that no secret or private material exists; the patterns do not detect every credential format.

The existing LICENSE contained only the Apache notice. Preserve its attribution in NOTICE and include the complete Apache 2.0 license text before publication.

## Publication checks

1. Confirm the signed-in GitHub owner and destination. An unauthenticated 404 does not establish whether a repository is private or absent.
2. Create the agreed public repository, without an auto-generated initial commit. Do not modify unrelated repositories.
3. Push the reviewed branch without force and compare remote HEAD with the local commit.
4. Confirm that CI passes on GitHub; local test results are not GitHub CI results.
5. Enable private vulnerability reporting where available, set an accurate repository description and topics, and decide where new issues and contributions will be handled.
6. Keep the product-page demo promotion pending its hosted accessibility and custom-domain gates. Publish a tagged/Maven release only after its separate release checks.
