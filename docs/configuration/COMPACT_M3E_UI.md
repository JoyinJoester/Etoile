# Compact M3E UI

The home work section uses seven grouped rows instead of 116 dp shortcut tiles. Rows have a 56 dp minimum height, a 32 dp leading slot, 12 dp icon-to-label spacing, and a consistent trailing chevron. Material dividers start at the 60 dp text inset. Short values stay inline; longer values stack and wrap at increased font sizes.

The Material profile header uses a 56 dp avatar beside left-aligned identity text and a left-aligned biography. Repository selection controls sit outside the complete text column, preserving title/description/metadata alignment. Metadata row icons are vertically centered against their text block.

Verification: debug APK build, lint, native emulator screenshots for home, profile, settings, explore and inbox at 411 dp width; home and settings at 320 dp / 150% font scale. Screenshots: `.codex-tmp/m3e-screens/compact-*.png`.
