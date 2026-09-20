# Etoile · Material 3 Expressive

## Design intent

Etoile is a native Android workspace for people who review code, follow projects,
and install open-source Android apps. Each screen should make its next useful
action visible without competing with the content being read.

The editable source is the set of projects in `m3e-canvas/`, using
[lnkiai/m3e-canvas](https://github.com/lnkiai/m3e-canvas) and its public agent
document format. Open the JSON with **Open project**, or use the links in
`m3e-canvas/index.html`. These are layout and navigation specifications; sample
counts, accounts and repositories are illustrative, not live user data.

## Design tokens

| Role | Default light appearance | Native mapping |
| --- | --- | --- |
| Primary | Orbit violet `#6750A4` | `colorScheme.primary` |
| Primary container | Pale violet `#EADDFF` | `primaryContainer` |
| Secondary | Slate violet `#625B71` | `secondary` |
| Tertiary | Rose `#7D5260` | `tertiary` |
| Surface | Paper violet `#FFFBFE` | `surface` / surface containers |
| Ink | Graphite `#1C1B1F` | `onSurface` |

Use semantic Material color roles in the application. Dark mode, wallpaper
colors and selected palettes supply their own matching foregrounds. Never use
the example hex colors as inline component colors.

Display and section titles use the bundled Google Sans Flex; body text uses the
platform sans family with CJK fallback. Monospace is reserved for refs, filenames,
versions and code. Body text stays at 14–16sp, primary labels at 16sp, section
titles at 18sp. Shapes: 12dp small, 16dp controls, 24dp groups, 32dp prominent
surfaces; connected controls use small inner corners. Motion uses the Material
expressive spring scheme and respects Android's animation scale.

The signature is the four work shortcuts, grouped by task: Issues and PRs first,
repositories and stars second. Their color is meaningful; it does not extend to
every list row. Long descriptions remain on the detail screens.

## Layout rules

- 16dp phone margins; a shared 8dp spacing rhythm. Work shortcuts use 12dp
  gaps; collection columns use a 20dp gutter.
- Primary interactive controls have at least a 48dp touch target. Heights grow
  with text; values and action labels are not fitted into fixed-height cards.
  Contribution heatmaps retain dense cells with day descriptions and selection.
- Five destinations retain the same order. M3E uses short navigation labels and
  outlined inactive icons. At 600dp navigation moves into a 96dp rail (80dp in
  Nothing and Miuix). At 1200dp a 240dp sidebar shows full destination labels.
  Both scroll in short windows. The content composition is retained across
  these navigation changes.
- Workspaces and collections are centered at a maximum of 1200dp. Text lists
  use at most two columns, each requiring 360dp multiplied by the font scale.
  Headers, search, filters and pagination span all columns. Contributor and
  connection tiles use a smaller, font-aware minimum width.
- Markdown readers use 840dp, code readers use up to 1440dp, and settings,
  sign-in and editing forms use 640dp. These limits keep reading and editing
  comfortable on an external display as well as on a tablet.
- Work tiles switch from two columns to one when text or window width requires
  it. A wide window can show four. A width alone never overrides large text.
- Repository summary and README, and profile identity and activity, become
  separate columns only when both can retain useful reading width.
- Issue, PR, commit, release, public profile and app detail pages use two panes
  at 960dp multiplied by the system font scale, within the 1200dp content
  limit. Metadata, release assets and app downloads live beside the main
  reading area. The issue body and PR overview description stay in the main
  pane. Release asset shortcuts target the correct pane.
- Repository, own-profile and Actions run/job layouts retain their existing
  840dp font-aware split. Home uses the full width for work shortcuts, with
  contributions and favorites alongside each other when both have room.
- Metadata gives both its label and value bounded space. On narrow layouts,
  the value moves below the label so a long user or branch name cannot squeeze
  the label into a vertical strip. Diff headers show the complete filename
  first, then the selectable directory path; change counts can wrap.
- Search, filters and results share a scroll container. This is especially
  important with a keyboard, a short landscape window or 150% text.
- System bars are applied once. Compose content consumes applied Scaffold
  insets; forms handle the remaining keyboard inset.
- Settings opens two independent pages: Appearance combines design style,
  light/dark mode and palette; Language contains only language choices. Both
  keep a 640dp maximum width and return to Settings. Language changes apply
  immediately while Android restores the current navigation stack.
- State is visible in place: skeleton on initial load, retained content during
  refresh, an explicit retry after failure, and a useful action for empty lists.

```text
Phone home                     Wide profile
┌──────────────────┐           ┌────┬─────────────────────────────────────┐
│ Home      Search │           │nav │ Profile                     Settings│
│ Your work        │           │    ├──────────────┬──────────────────────┤
│ [Issues] [PRs]   │           │    │ Identity     │ Contributions        │
│ [Repos]  [Stars] │           │    │ Followers    │ Achievements         │
│ Connected tools  │           │    │ Account      │ README               │
│ Contributions   │           │    │ Work links   │                      │
│ Saved projects   │           │    │              │                      │
├──────────────────┤           └────┴──────────────┴──────────────────────┘
│Home Inbox … Me   │
└──────────────────┘
```

## Page families

| Canvas project | Pages and transitions | Implementation focus |
| --- | --- | --- |
| Main workspace | Home, inbox, explore, store, profile; wide home/profile | Primary task order, navigation, scroll ownership |
| Repositories | Repository, files, reader, branches/tags, releases, release assets, commits/diff | Identity → actions → readable content; wider summary/README layout |
| Conversations | Issue list/detail/create, PR list/detail/review, personal work | Visible status, filters, full-width titles, keyboard-safe editing |
| Accounts and settings | Sign-in, accounts, settings, appearance, language, public profile, connections, organizations, stars | Early account actions, separate appearance/language pages, readable choices, safe confirmation |
| Automation and apps | Workflows, runs, run detail, job logs, app detail, sources, collaborators, webhooks | Action/state separation; wrapping logs; explicit download status |

Every detail has a back action; main navigation only appears on workspace
screens. External GitHub actions are labelled as such. Canvas links describe
existing routes; unsupported remote operations are not represented as working
native features.

## Review criteria

Check production Compose screens with deterministic debug fixtures in light and
dark mode, 320dp width, 150% text, a short landscape window and a wide window.
Verify that the final action can be scrolled into view, main navigation is
reachable, long names do not displace controls, and forms stay above the keyboard.
Canvas previews and fixture callbacks verify layout/navigation only; existing
repository/ViewModel tests remain the evidence for state and API behavior.

## Implementation notes

Nothing remains the default visual style. Etoile supports Nothing, Material 3
Expressive and Miuix independently. During this design pass, explicitly select
Material in the running app or in the debug fixture; never change the persisted
fallback just to preview it. Material receives its own typography, shapes and
motion. Shared layout changes also require a Nothing and Miuix smoke check.

Validation results and screenshot locations are recorded in
`m3e-canvas/VERIFICATION.md` for the initial design pass and
`large-screen/VERIFICATION.md` for the subsequent large-display adaptation.

The Issue composer now includes repository templates and three bilingual built-in forms.
See [template behavior](ISSUE_TEMPLATES.md) and [template verification](issue-templates/VERIFICATION.md)
for draft protection, submission retry, and 320dp / large-text checks.
