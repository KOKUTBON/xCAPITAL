# xCAPITAL brand guide

xCAPITAL is a financial operating system for modern companies. The identity says two things: **capital moves forward**, and **every movement is controlled**.

## The mark

The x is built from two strokes:

- A **rising stroke** that ends in an arrow, filled with the signal gradient (violet `#7C5CFF` to cyan `#3EE6D2`). It stands for capital growth and forward motion.
- A **broken counter-stroke** in near-white (`#E8ECF6`). The gap where it meets the rising stroke stands for Guard: the point where every action is checked before it proceeds.

The mark sits on a deep navy tile (`#0B0F1F`, or `#070A12` in print) with a 16/64 corner radius.

## Wordmark

`xCAPITAL` is always written with a lowercase **x** and an uppercase **CAPITAL**. On product surfaces, the x carries the signal gradient; in plain text it is never stylised. The wordmark is set in Space Grotesk 700 with a +0.04em tracking.

## Assets

| File | Use |
| --- | --- |
| [`assets/branding/logo.svg`](../assets/branding/logo.svg) | Mark and wordmark for light backgrounds (documentation, README) |
| [`assets/branding/logo-light.svg`](../assets/branding/logo-light.svg) | Mark and wordmark for dark backgrounds |
| [`assets/branding/mark.svg`](../assets/branding/mark.svg) | App icon / favicon |
| [`assets/branding/mark-512.png`](../assets/branding/mark-512.png) | Raster icon (PWA, mobile) |
| [`assets/branding/social-preview.png`](../assets/branding/social-preview.png) | 1280×640 social card (GitHub, Open Graph) |

The web client ships its own copies in `frontend/web/src/assets/brand/`. PNGs are exported from the SVG sources with `rsvg-convert`.

## Colour

| Token | Dark | Light | Role |
| --- | --- | --- | --- |
| Canvas | `#05070D` | `#F3F5FA` | Page background |
| Surface | `#0C101C` | `#FFFFFF` | Cards and panels |
| Primary | `#6E8BFF` | `#4361EE` | Actions, focus, selection |
| Accent | `#3EE6D2` | `#0EA5A0` | Liquidity, live signals |
| Intelligence | `#A78BFA` | `#7C3AED` | Anything proposed by AI |
| Positive / Negative | `#2FD69B` / `#FF5C7A` | `#0D9F6E` / `#E11D48` | Gains, inflows / losses, outflows, blocks |
| Signal gradient | `#3EE6D2 → #5B8CFF → #7C5CFF` | same | Brand moments only: the mark, primary CTA, active navigation |

AI-originated content is always marked in the Intelligence violet, so people can tell at a glance what a model proposed and what a person did.

## Typography

- **Inter** for interface text.
- **Space Grotesk** for headings and display numbers.
- **JetBrains Mono** for figures, identifiers, hashes and eyebrows, always with tabular numerals.

All three are bundled locally through Fontsource. No font is loaded from a third-party CDN.

## Usage

- Keep clear space equal to one stroke width around the mark. The minimum size is 20 px.
- Do not recolour, rotate, outline or add effects other than the product's soft glow.
- Never place the mark on a busy photo.
- The product is a demonstration. Any screenshot or deck must keep the "simulated data" disclosure visible.
