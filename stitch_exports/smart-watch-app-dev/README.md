# Stitch Export: 스마트 워치 앱 개발

Project title: `스마트 워치 앱 개발`
Project ID: `3884119377295494772`

This folder contains:

- `download.sh`: a ready-to-run `curl -L` export script for the 4 requested screens.
- `screens/manifest.json`: screen IDs, titles, download URLs, and dimensions.
- `design-system/nocturne-scholar.md`: exported design system spec text.
- `design-system/theme.json`: exported design system theme tokens.
- `screens/*.png` and `screens/*.html`: downloaded screenshots and exported HTML for the requested screens.

Download status:

- A direct `curl -L` export succeeded in this session on `2026-04-23`.
- The `screens/` folder contains the downloaded PNG and HTML files for `Alerting`, `Connection Waiting`, `Watch Settings`, and `Active Session`.

Design system note:

- Stitch exposed the design system as structured theme data for asset `assets/11449933967684563605`.
- It did not expose a separate screenshot or HTML bundle for the design-system instance ID `asset-stub-assets-11449933967684563605-1776090178901`, so this export stores the design tokens and markdown spec locally instead.
