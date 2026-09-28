# Vectis first-release hosting decision

Accepted with the project owner on 25 September 2026.

## Decision

Use GitHub for source, releases and issues, and Render for the interactive Spring Boot sample application. Keep the product under the existing YavonaLabs domain. Developers install the Vectis starter inside their own application; the hosted demo is not a service for connecting customer databases.

Repository status: the public source is https://github.com/yavonalabs/vectis (`github` remote). The existing `origin` remains `https://gitlab.com/jigsya23-group/vectis-studio.git`, which Render builds from. Push approved changes to both; no automatic mirroring is configured. GitHub verification passed on commit `852d7e7`.

The owner confirmed Cloudflare manages `yavonalabs.com` on 27 September 2026. DNS changes remain pending authenticated access and inspection of existing records. Only the proposed Vectis subdomains are in scope; preserve root-domain and mail records.

Proposed addresses (not yet provisioned):

| Address | Purpose |
| --- | --- |
| vectis.yavonalabs.com | Product page, screenshots, installation instructions and documentation |
| demo.vectis.yavonalabs.com | Isolated interactive sample on Render |

The product page can use the existing YavonaLabs website host. GitHub Pages is an option for static project documentation, but cannot run the Java application. Do not change the existing domain's root or mail records.

Start with Render's free compute plan for deployment testing. It sleeps after 15 minutes without incoming traffic and typically takes about a minute to resume. Select and budget an appropriately sized paid instance before actively promoting an always-available demo. Railway is an alternative with a $5 monthly minimum and usage charges beyond its included allowance; it is not a fixed $5 bill.

## Prepared package

The sample root URL redirects to the configured Studio path. Its header exposes a CSRF-protected POST logout control on desktop and mobile. Embedded applications opt in by setting `vectis.logout-path` to their own Spring Security logout endpoint (the sample uses `/logout`); the core leaves it hidden by default rather than assuming a host application's authentication route.

The root Dockerfile runs Maven verification before packaging the sample in a Java 17 runtime, running as a non-root user. The demo Spring profile accepts Render's PORT variable, binds to all interfaces and disables the H2 console. The Render blueprint checks /login and enables secure session cookies for its HTTPS endpoint.

The sample uses an in-memory H2 database and seeds fictional records. Records, mutations and audit history are shared between visitors and reset when the process restarts. There is no scheduled reset yet. Credentials are intentionally the sample credentials: admin/admin for editing, user/password for read-only access. They are not production authentication. Never attach real data or credentials to this service.

## Local verification

```sh
mvn verify
docker build -t vectis-demo .
docker run --rm -p 18080:8080 --memory=512m vectis-demo
```

Open http://localhost:18080/login. Exercise sign-in, lists and filters, a sample edit, related records, activity and sign-out. Restart the container and verify that the sample resets. Check memory under concurrent use before choosing a paid size. Secure cookies are enabled by Render's environment configuration; local HTTP container testing leaves them disabled.

## Publication sequence

1. Finish browser verification and the container build/smoke check. The demo profile displays sample credentials and shared-data/reset semantics on the entry page; review this behavior before inviting visitors.
2. Push the reviewed release to the intended GitHub repository. Connect it to Render and create the service from render.yaml. Review the selected plan and any billing settings.
3. Verify the assigned onrender.com URL, HTTPS sign-in, mutations, audit feedback, session expiry and a restart. Run the hosted mobile and accessibility smoke pass below against this deployed build; earlier local checks do not satisfy this release gate. The login health check verifies HTTP availability, not all application workflows.
4. Add demo.vectis.yavonalabs.com in Render. Copy the exact DNS records Render provides into the domain's DNS provider; do not guess the target. Wait for domain verification and TLS issuance.
5. Repeat the hosted smoke pass on the final custom-domain URL and record the results before publishing the product-page demo link. Publish the product page and documentation only after these checks pass. Include installation instructions that have been tested independently.

### Hosted mobile and accessibility smoke pass

Run on the deployed HTTPS demo at 320px and 375px widths, plus desktop. Record the URL, deployed commit, date, browser and viewport sizes, screenshots and any failures. This is a release smoke test, not a complete accessibility audit.

- Confirm that the login card, sample-account notice, inputs and sign-in button are visible and usable. Complete sign-in at mobile width.
- Open mobile navigation and reach every destination. Check for clipped or undiscoverable items and page-level horizontal overflow. Any intentionally scrollable table must have a visible scrolling affordance.
- Exercise the overview, a filtered list, record detail, create/edit form, drawer and action/delete dialogs. Confirm controls and feedback remain readable and reachable without clipping; cancel destructive operations.
- Use keyboard-only navigation to check visible focus, logical order, accessible control names, dialog focus containment, Escape dismissal and focus return. Check form error announcements and feedback with a screen reader.
- Check text and control contrast on the rendered hosted theme, including buttons, errors and focus indicators, and verify reduced-motion behavior with that preference enabled.

Keep this gate pending until it has been run on the hosted build. Fix failures and rerun the affected checks before the public link goes out.

Rollback: deploy the last verified image/commit through Render. This resets the disposable database; it does not restore historical sample data. Keep a known-good release reference before each deployment.

## Status and remaining scope

27 September: the owner deployed the demo at https://vectis-demo.onrender.com/login. Initial hosted HTTP and mobile browser checks passed; see [the hosted verification record](HOSTED_DEMO_VERIFICATION.md) for exact coverage and outstanding release gates. The final custom domain and product-page publication remain pending.

A static product-page draft is available in `docs/site/`. It explains the workflow and current source build, with no external script dependencies. Its public GitHub source is linked; its hosted-demo CTA remains pending release verification. See `docs/site/README.md` for publication steps.

Hosting decision and configuration are documented. The owner created the Render service; GitHub publication is complete, while DNS changes are pending. Neither Docker nor Podman is available on the local command path. The Render deployment now serves the application, but its build logs and commit must still be checked. Local Maven verification passed all 61 tests on 26 September, and the later root/logout fix passed 63 tests. A successful Maven build does not establish production readiness.

The packaged JAR was started locally with the demo profile on 26 September. Browser checks verified the entry explanation, admin sign-in, action preview, a sample leave-status change and its activity entry, drawer content and read-only access. The disabled H2 console returned HTTP 404. The product page, container smoke test, hosted HTTPS checks and full accessibility verification remain outstanding. Saved views, export, unsaved-edit protection and mutation lifecycle work remain separate roadmap items; they are not claimed complete by this deployment package.

## References

- [GitHub Pages capabilities](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages)
- [Render Docker and service configuration](https://render.com/docs/blueprint-spec)
- [Render free-service limitations](https://render.com/docs/free)
- [Railway plans](https://docs.railway.com/pricing/plans)
