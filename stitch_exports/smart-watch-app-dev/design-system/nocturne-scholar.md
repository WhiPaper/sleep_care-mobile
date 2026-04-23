# Nocturne Scholar

Asset ID: `assets/11449933967684563605`
Instance ID: `asset-stub-assets-11449933967684563605-1776090178901`
Copied from: `assets/7981549002083156018`

## Design System Specification: The Nocturnal Science

### 1. Overview & Creative North Star: "The Digital Sanctuary"
The Digital Sanctuary moves beyond the utility of a "tracker" into the realm of a "cognitive companion." For students, drowsiness is a source of anxiety; this design system aims to neutralize that stress through an Editorial-Scientific aesthetic.

We break the standard app mold by rejecting rigid boxy grids in favor of Intentional Asymmetry and Tonal Depth. The layout should feel like a high-end medical journal reimagined for a digital era, authoritative yet breathable. We utilize wide margins, overlapping glass layers, and a sophisticated typographic scale to guide the student’s eye through complex biological data without overwhelming them.

### 2. Colors: Tonal Architecture
The palette is rooted in the transition from day to night. We avoid harsh contrasts, opting for a Deep-Sea immersion that honors the circadian rhythm.

#### The No-Line Rule
Designers are prohibited from using 1px solid borders to section content. Boundaries must be defined solely through:

- Background color shifts
- Tonal transitions
- Negative space

#### Surface Hierarchy & Nesting
Treat the UI as physical layers of frosted glass.

- Base layer: `surface` `#111415`
- Secondary grouping: `surface-container-low` `#1a1c1d`
- Active interactive elements: `surface-container-high` `#282a2c`

#### The Glass & Gradient Rule
Use glassmorphism for floating navigation or critical alerts. Use a background blur (15px to 25px) combined with `surface-variant` at 40% opacity.

- Signature texture: linear gradient from `primary` `#bdc2ff` to `primary-container` `#1a237e`

### 3. Typography: Editorial Authority
We pair Manrope for high-level expression and Inter for data-heavy utility.

- Display and headlines: `Manrope`
- Body and labels: `Inter`
- Secondary metadata should use `on-surface-variant` `#c6c5d4`

### 4. Elevation & Depth: Tonal Layering
We reject traditional drop shadows. Elevation should come from tonal layering first.

- Primary elevation: stacked `surface-container` tiers
- Floating elements: shadow tinted with `on-primary` `#1b247f` at 6% opacity
- Accessibility fallback stroke: `outline-variant` `#454652` at 15% opacity
- Background atmosphere: radial gradient of `tertiary-container` `#00353d`

### 5. Components: Precision & Care

#### Buttons
- Primary: `primary` fill, `on-primary` text, `md` radius, gradient for key CTAs
- Secondary: `surface-container-high` fill, no border
- Tertiary: text-only using `primary`

#### Cards & Lists
- No dividers
- Separate content with space or subtle background shifts
- Use `xl` padding

#### Drowsiness Alerts
- Warning state: `error_container` `#93000a` with glassmorphism blur

#### Data Visualization
- Trend line: `tertiary` `#44d8f1`
- Fill: gradient fade from `tertiary`

#### Custom Components
- Circadian Slider
- Focus Orb

### 6. Do’s and Don’ts

#### Do
- Use intentional asymmetry
- Prefer more whitespace over smaller text
- Use `surface-bright` for contrast pop

#### Don’t
- Don’t use `#000000`
- Don’t use hard 1px dividers
- Don’t use high-saturation reds for warnings
