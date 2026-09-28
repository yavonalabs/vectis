# Vectis product-page preview

Serve this directory with any static HTTP server. It has no build step, JavaScript or external font/script dependency. `index.html` and `site.css` must be deployed together.

This is an unpublished product-page draft. Its source link points to the verified public repository, https://github.com/yavonalabs/vectis. It has no live-demo CTA, signup form, published-package badge or announced price. GitLab remains the Render deployment source.

Before publication:

1. Public source repository confirmed and linked near the source-build instructions on 27 September 2026.
2. Complete the hosted-demo gates in `../DEPLOYMENT.md` on the final domain.
3. Replace the public-demo pending notice with the verified demo link, preserving the sample-data explanation.
4. Check mobile widths, keyboard navigation, contrast and all links on the deployed product page as well.

Do not infer production readiness or a published Maven artifact from this preview. The source build targets the current `0.1.0-SNAPSHOT` checkout.

## Local checks

Browser smoke checks on 26 September 2026 covered desktop rendering, 320px and 375px widths with no page-level horizontal overflow, all internal anchor targets, the setup navigation link and keyboard expansion of the pricing FAQ. This is not a full accessibility audit and does not satisfy the hosted-demo release gate.
