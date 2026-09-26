# Vectis: friendly studio direction

Reference: https://yavonalabs.com/ — inspected visually, including the ivory canvas, forest-green typography, purple serif emphasis and orbital illustration.

The standalone `studio-concept.html` is a design prototype, not a connected dashboard. Search and workspace cards explicitly report that they are previews. It adds no data access or business operations.

## Visual system

- Ivory canvas, forest-green navigation and text, violet primary accents; soft lilac, butter and mint for navigation illustrations.
- Expressive serif emphasis on overview headlines only. Familiar sans-serif typography for records, forms and buttons. Monospace reserved for identifiers and technical details.
- A small geometric character on welcome/empty states. Avoid characters, jokes, celebratory effects and decorative motion in destructive or compensation-related confirmations.
- Spacious overview with a prominent search entry and recognizable workspace cards. Compact record screens retain scan-friendly alignment, visible filters and clear actions.
- Gentle hover movement, respecting reduced-motion preferences. No confetti, sounds, points or productivity gamification.

## Implementation sequence

1. Replace hard-coded template colors with semantic tokens, including contrast-tested error/warning/success variants. Establish shared buttons, inputs, cards and typography.
2. Integrate the overview and navigation using permitted metadata, real search and real activity. Workspace descriptions need developer-configurable copy or neutral generic defaults; never infer entity business meaning.
3. Carry the system into lists and filters, keeping dense-data readability. Record details use a clear identity block and readable relationship cards.
4. Update edit forms, dialogs, login and empty/error states together so no dark-theme fragments remain. Preserve all permission and mutation behavior.
5. Verify desktop/narrow layouts, zoom, keyboard focus, contrast, long names, empty data and reduced motion, alongside existing regressions.

The prototype has desktop visual verification. It includes responsive rules but mobile/keyboard/contrast verification remains required before production integration. The current working app remains at port 18080; this standalone concept is served at port 18081.

## Mobile navigation revision

The narrow layout now uses three equal-width primary navigation cells rather than a horizontally scrolling strip. Labels may wrap, so Activity stays discoverable at narrow widths and larger text sizes. Duplicate entity shortcuts are hidden in the mobile navigation; the same destinations remain visible in the workspace cards. Desktop navigation is unchanged.

Verified in the browser at a 320px viewport: all three primary links fit within the navigation bounds, navigation scroll width equals client width, and the page has no horizontal overflow. Activity navigation reaches its section.

## First application integration

The working overview now uses semantic studio tokens, a responsive welcome illustration, a real global-search trigger, permission-filtered entity cards and existing recorded activity. Generic card descriptions avoid invented entity semantics. Navigation uses forest green and lilac active states; the static ONLINE label has been replaced with Workspace. Dense record screens, forms and dialogs retain their existing dark styling for a separate migration. This is the overview/shell milestone, not a completed app-wide theme conversion.

Integration verification: all 60 existing tests passed. Desktop browser inspection confirmed the rendered overview; Find a record opened and focused the real search, and searching Alice returned the live sample record link. At 375px the workspace cards stack with no page-level horizontal overflow. The final CSS pass corrects shortcut-badge contrast and mobile header wrapping. App-wide contrast and theme migration remain outstanding.
