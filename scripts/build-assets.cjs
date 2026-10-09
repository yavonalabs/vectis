const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const dest = 'vectis-core/src/main/resources/static/vectis-assets/vendor';
fs.mkdirSync(dest, { recursive: true });
const copies = [
  ['htmx.org/dist/htmx.min.js', 'htmx.min.js'],
  ['alpinejs/dist/cdn.min.js', 'alpine.min.js'],
  ['@alpinejs/anchor/dist/cdn.min.js', 'alpine-anchor.min.js'],
  ['htmx.org/LICENSE', 'htmx-LICENSE'],
  ['tailwindcss/LICENSE', 'tailwind-LICENSE'],
  ['@fontsource/inter/LICENSE', 'inter-LICENSE'],
  ['@fontsource/jetbrains-mono/LICENSE', 'jetbrains-mono-LICENSE']
];
for (const [from, to] of copies) fs.copyFileSync(path.join('node_modules', from), path.join(dest, to));
fs.copyFileSync('scripts/asset-licenses/alpine-LICENSE', `${dest}/alpine-LICENSE`);
let fonts = '';
for (const [pkg, family, weights] of [['inter', 'Inter', [400,500,600,700]], ['jetbrains-mono', 'JetBrains Mono', [400,500,600]]]) {
  for (const weight of weights) {
    const name = `${pkg}-latin-${weight}-normal.woff2`;
    fs.copyFileSync(`node_modules/@fontsource/${pkg}/files/${name}`, `${dest}/${name}`);
    fonts += `@font-face{font-family:'${family}';font-style:normal;font-weight:${weight};font-display:swap;src:url('./${name}') format('woff2')}\n`;
  }
}
fs.writeFileSync(`${dest}/fonts.css`, fonts);
execFileSync(process.execPath, ['node_modules/tailwindcss/lib/cli.js', '-c', 'tailwind.config.cjs', '-i', 'scripts/assets-input.css', '-o', `${dest}/utilities.css`, '--minify'], { stdio: 'inherit' });
