# Nocturne Scholar

Asset ID: `assets/3b70cc54ba9d4901bcaf5e1efd67afc3`
Instance ID: `asset-stub-assets-3b70cc54ba9d4901bcaf5e1efd67afc3-1775054917951`

# Design System Specification: The Nocturnal Science

## 1. Overview & Creative North Star: "The Digital Sanctuary"
The Digital Sanctuary moves beyond the utility of a "tracker" into the realm of a "cognitive companion." For students, drowsiness is a source of anxiety; this design system aims to neutralize that stress through an **Editorial-Scientific** aesthetic.

We break the "standard app" mold by rejecting rigid boxy grids in favor of **Intentional Asymmetry** and **Tonal Depth**. The layout should feel like a high-end medical journal reimagined for a digital era, authoritative yet breathable. We utilize wide margins, overlapping "glass" layers, and a sophisticated typographic scale to guide the student’s eye through complex biological data without overwhelming them.

## 2. Colors: Tonal Architecture
The palette is rooted in the transition from day to night. We avoid harsh contrasts, opting for a "Deep-Sea" immersion that honors the circadian rhythm.

### The "No-Line" Rule
Designers are prohibited from using 1px solid borders to section content. Boundaries must be defined solely through:

- Background color shifts
- Tonal transitions
- Negative space

### Surface Hierarchy & Nesting
Treat the UI as physical layers of frosted glass.

- Base layer: `surface` (`#111415`)
- Secondary grouping: `surface-container-low` (`#1a1c1d`)
- Active interactive elements: `surface-container-high` (`#282a2c`)

### The "Glass & Gradient" Rule
Use Glassmorphism for floating navigation or critical alerts. Apply a linear gradient from `primary` (`#bdc2ff`) to `primary-container` (`#1a237e`) for high-impact CTA buttons.

## 3. Typography: Editorial Authority
Pair **Manrope** for high-level expression and **Inter** for data-heavy utility.

- Display: `display-lg`, `display-md`
- Headline workhorse: `headline-sm`
- Secondary metadata: `on-surface-variant` (`#c6c5d4`)

## 4. Elevation & Depth: Tonal Layering
Stack `surface-container` tiers for elevation. For floating elements, use soft tinted shadows and avoid hard borders. If a stroke is required, use `outline-variant` at low opacity.

## 5. Components: Precision & Care

### Buttons
- Primary: `primary` fill with `on-primary` text, `md` radius
- Secondary: `surface-container-high` fill, no border
- Tertiary: text-only in `primary`

### Cards & Lists
- Forbid dividers
- Use white space or subtle background shifts
- Prefer `xl` padding

### Drowsiness Alerts
- Use `error_container` with a glass-like blur for softer warnings

### Data Visualization
- Sleep trend line: `tertiary` (`#44d8f1`)
- Use gradient fades for chart fills

### Relevant Custom Components
- Circadian Slider
- Focus Orb

## 6. Do’s and Don’ts

### Do
- Use intentional asymmetry
- Prioritize negative space
- Use `surface-bright` when an element should pop

### Don't
- Do not use pure black
- Do not use standard 1px dividers
- Do not use high-saturation reds for warnings
