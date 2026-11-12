# App icon: the golden rune

A real 3D model rendered with three.js (`rune.html`): a flat faceted gold diamond (depth 0.35) with a
teal crystal, studio-lit, bloom, on the navy background. Owner-approved view: long lens, slight turn.

- `rune_layer.png`: the adaptive-icon layer (1728 px, gem ≈ 48% of the height so it sits in the safe
  zone). Exported to `app/src/main/res/mipmap-*/ic_launcher_foreground.webp` (108 dp at each density).
- `rune_navy.png`: the full-size render.
- Themed (monochrome) icon: `res/drawable/ic_launcher_monochrome.xml`, the flat outline.

Re-render: `npm i three@0.160.0` next to `rune.html`, serve the folder (`python3 -m http.server`), open
`rune.html?size=1728&bg=navy&depth=0.35&fov=9.65` in Chromium and call `draw(-0.22, 0.09)`.
Riot's logos are not used (their policy forbids it); the rune is an original mark.
