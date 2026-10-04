# CricScore 2.0

Ball-by-ball cricket scoring for club, box and gully cricket, with tournaments, points tables, knockouts and career stats. Everything is stored on the phone (Room database) and works offline.

## Build and run

**Android Studio** (a recent version that supports Android Gradle Plugin 9.1, which this project uses):

1. Open Android Studio, choose **Open**, and select this folder.
2. Let Gradle sync finish, then press **Run** on a phone or emulator (Android 7.0 or newer).

No keystore or API key is needed for a debug build. The app falls back to Android Studio's own debug key when `debug.keystore` isn't present.

**AI Studio**: upload or replace the project files as before. AI Studio's signing setup is still used when its keystore files are present.

### Upgrading from version 1

* Your existing teams, players, grounds, tournaments and matches are kept. The database moves from version 1 to version 2 with a migration, not a wipe.
* On first launch, matches that finished on the field but were never saved as completed (a bug in version 1) get their result, winner and player of the match filled in. Tournament tables, playoffs and champions are recalculated too.
* If the new build is signed with a different key from the one already on the phone, Android refuses to install over it. You would then have to uninstall the old app, which deletes its data. To keep your data, build with the same signing key (the same AI Studio project, or the same machine's debug key).

## What's in the app

| Tab | What you can do |
| --- | --- |
| Home | Resume live matches, see what's up next, recent results, start a match or tournament, switch light/dark theme |
| Matches | Every match, with search, Live / Upcoming / Results filters, and delete |
| Tournaments | League, knockout, or league + playoffs, with an auto-built fixture list, a live points table with NRR and form, knockout progression, champions and tournament stats |
| Squads | Teams (colour, short code, solo sides, captain and keeper), the player pool, and grounds with match counts and average first-innings scores |
| Stats | Career batting, bowling, fielding and team leaderboards, player profiles, and team head-to-head records |

## Project layout

```
app/src/main/java/com/example/
  data/        Room entities, DAOs, migration, repository (all writes go through CricketRepository)
  domain/      Pure cricket rules: scoring, crease logic, results, scorecards, NRR, fixtures, playoffs, stats
  ui/scoring/  Live scoring console, keypad, wicket/tools dialogs, full scorecard
  ui/screens/  Home, Matches, Tournaments, Squads, Stats, setup and settings
  ui/components, ui/theme   Shared components and the design system (Barlow fonts, slate/saffron palette)
app/src/test/  Unit tests for the scoring rules, results, NRR and brackets
```

Fonts: Barlow and Barlow Condensed (SIL Open Font License, see `FONT_LICENSE_OFL.txt`).

See `CHANGES.md` for every fix and new feature.
