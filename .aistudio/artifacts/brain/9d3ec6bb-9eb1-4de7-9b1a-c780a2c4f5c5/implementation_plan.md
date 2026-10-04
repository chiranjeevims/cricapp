# CricScore - Pro Cricket Scoring & Tournament Ecosystem (Final Architecture)

A broadcast-grade, feature-rich Android cricket scoring and tournament platform built with Jetpack Compose and local Room SQLite persistence. Engineered for club tournaments, gully/box matches, solo player duels, and multi-innings mini-tests, CricScore delivers automated tournament fixtures and knockout brackets, comprehensive team & player statistics (both in-tournament and lifetime career), automated Player of the Match awards, grounds management, rainout/walkover controls, live in-match penalty runs, mid-match lineup editing, and an ultra-tactile live scoring console with instant undo.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product rules and features are formalized:

- **Automated Knockout Engine**:
  - **Knockout Tournaments**: Generates full single-elimination brackets (Quarter-finals, Semi-finals, Final) for 4, 8, or custom team counts.
  - **League + Knockout Tournaments**: Generates round-robin fixtures (with user-chosen encounters per opponent); upon group completion, **Top 4 teams automatically qualify** into Semi-Final 1 (1st vs 4th) and Semi-Final 2 (2nd vs 3rd), with winners advancing automatically to the Grand Final.
- **Dual-Scope Statistics (Tournament & Lifetime)**:
  - **Team Statistics**:
    - *Tournament Scope*: Matches, Won, Lost, Tied, Win %, Total Runs, Total Wickets, Highest Score, Lowest Defended, NRR.
    - *Lifetime Scope*: Career matches, win rate, tournament trophies won, head-to-head records against opponents.
  - **Player Statistics**:
    - *Tournament Scope*: Orange Cap (Most Runs), Purple Cap (Most Wickets), Highest Individual Score, Best Bowling Figures, Most 4s & 6s, Best Economy.
    - *Lifetime Scope*: Career matches, innings, runs, batting average, strike rate, centuries, half-centuries, total wickets, 5-wicket hauls, and bowling average.
- **Player of the Match (POTM / MVP)**:
  - Automatically calculated after each match using an intelligent performance index (runs scored + milestone bonuses + wickets + maiden overs), with a one-tap manual override for the scorer.
- **Persistent Teams & Global Player Pool**:
  - Central **Player Pool** directory to register cricketers once with roles (Batsman, Bowler, All-Rounder, Wicketkeeper) and batting/bowling styles.
  - Teams are permanently saved in Room DB, with players easily assigned from the pool or created on the fly.
- **Solo Player ("One-Man Army") Team Mode**:
  - Single-player team mode with selectable **2 to 10 wickets (lives)**.
  - **Non-resetting score rule**: When a wicket falls, the batsman's score (runs, balls, 4s, 6s) **does not reset**—they keep batting while team wickets increment until the max limit is reached.
  - **Continuous Bowling**: Solo player can bowl consecutive overs with no rotation restriction.
  - Supports **Solo vs. Solo** and **Solo vs. Full Squad**.
- **Limited-Overs 2-Innings ("Mini-Test") Format**:
  - Two innings per side (up to 4 innings total).
  - True Test match dynamics: 1st innings scores calculate **Lead / Trail**; 3rd innings sets the 4th innings target.
- **Grounds & Venues Directory**:
  - Add and manage grounds (Name, City, Pitch Surface: Turf, Matting, Cement, Box/Astro, Grass; Boundary Size).
- **Match Abandonment & In-Match Tools**:
  - **Rain Abandonment**: Records as `NO_RESULT` and awards **1 point each** in the standings.
  - **Declare Winner / Walkover**: Awards 2 full points and logs the official decision.
  - **Add Penalty Runs**: Award +5 or custom penalty runs directly to team extras.
  - **Edit Playing XI Mid-Match**: Substitute or add players without losing scoring history.

---

## 1. Overview & Core Concept

CricScore is designed to feel like a modern international cricket broadcast console in your pocket:
1. **Effortless Tournament Management**: Pick teams, set matches per opponent, and let CricScore generate all fixtures, update live points tables with Net Run Rate, seed the semi-finals, and crown the champion with a victory podium card.
2. **Fast & Forgiving Scoring**: Single-tap runs keypad, color-coded boundaries, clear wicket modal, and instantaneous **Undo** for stress-free scoring.
3. **Deep Statistical Insights**: Switch seamlessly between tournament-specific leaderboards and all-time career statistics.
4. **Resilient Local Persistence**: Room SQLite database guarantees 100% offline functionality at any ground.

---

## 2. User Experience & Visual Design

### Screen Navigation & Flow

```
[Main Navigation Bar]
  ├── 1. [Matches]
  │     ├── Quick Match Setup (Standard, Box/Gully, Solo Duel, Mini-Test)
  │     ├── Select Venue / Ground & Set Overs
  │     ├── Toss & Playing XI Selection (Solo wickets selector: 2-10)
  │     └── Live Scoring Screen
  │           ├── Broadcast Scorebar: Total Runs, Wickets, Overs, CRR, RRR, Target / Lead-Trail
  │           ├── Live Pitch: Striker*, Non-Striker, Current Bowler with individual figures
  │           ├── Over Timeline: Color-coded balls [ 1 ] [ 4 ] [ Wd ] [ . ] [ W ] [ 6 ]
  │           ├── Scoring Keypad: Runs 0-6, Extras, Wickets, Swap Striker, Undo
  │           ├── Action Sheet: Add Penalty Runs (+5), Edit Lineups, Abandon (Rain) / Walkover
  │           └── Scorecard Modal & Match Summary with Player of the Match
  │
  ├── 2. [Tournaments]
  │     ├── Tournament Creator (Round-Robin, Knockout Bracket, or League + Knockout)
  │     ├── Encounters Selector (1x, 2x, 3x) & Auto-Fixture Generator
  │     ├── Standings Tab (P, W, L, T, NR, Pts, NRR)
  │     ├── Fixtures & Knockout Bracket Tab (Top 4 Semis & Final progression)
  │     ├── Tournament Team Stats Tab (Win %, Total Runs, Wickets, High/Low Scores)
  │     └── Tournament Player Stats Tab (Orange Cap, Purple Cap, Best Figures)
  │
  ├── 3. [Grounds & Venues]
  │     ├── Venue Directory (Add Ground: Name, City, Surface, Boundary Dimensions)
  │     └── Ground Details (Match count, average 1st innings total)
  │
  ├── 4. [Teams & Player Pool]
  │     ├── Master Player Pool (Register players, roles, batting/bowling styles)
  │     ├── Team Manager (Create Club, assign players from Pool, Solo Mode toggle)
  │     └── Overall Lifetime Team Stats Tab (Career win %, head-to-head)
  │
  └── 5. [Lifetime Leaderboards]
        ├── Career Batting (Total Runs, Average, Strike Rate, 50s/100s, HS)
        ├── Career Bowling (Total Wickets, BBI, Economy Rate, 5-fers)
        └── Player Profile Cards
```

### Visual Identity & Theme
- **Broadcast Turf Palette**:
  - `Primary`: Deep English Willow Green (`#0A4D2E`) with Vibrant Emerald (`#00E676`)
  - `Accent / Badges`: Stadium Gold (`#FFB300`) for boundaries, awards, and leaderboard toppers
  - `Wickets`: Vivid Alert Coral (`#E53935`)
  - `Dot Balls`: Muted Slate (`#757575`)
- **Visual Polish**:
  - Circular player avatars with jersey numbers and primary role badges
  - Real-time animated ball outcome pills
  - Celebratory Player of the Match banner and Tournament Winner trophy card
  - Dark mode and Light mode high-contrast support for outdoor sunlight readability

---

## 3. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  ┌──────────────┐  ┌───────────────┐  ┌─────────────┐  ┌────────────┐  │
│  │ DashboardView│  │ LiveScoreView │  │TournamentHub│  │ GroundsView│  │
│  │ (Match Setup,│  │ (Keypad, Undo,│  │(Auto-Sched, │  │ (Directory,│  │
│  │  Format Pick)│  │  Penalty, XI) │  │ Standings)  │  │  Venues)   │  │
│  └──────┬───────┘  └───────┬───────┘  └──────┬──────┘  └─────┬──────┘  │
│         │                  │                 │               │         │
│  ┌──────┴──────────────────┴─────────────────┴───────────────┴──────┐  │
│  │      Teams, Player Pool & Lifetime/Tournament Leaderboards       │  │
│  └─────────────────────────────────┬────────────────────────────────┘  │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │ StateFlow & Actions
┌────────────────────────────────────▼───────────────────────────────────┐
│                              ViewModels                                │
│   • MatchViewModel        (Live scoring, undo stack, penalty runs,     │
│                            mid-match lineups, rainout, declare winner) │
│   • TournamentViewModel   (Auto-scheduler, Top 4 knockout progression, │
│                            standings with NRR, tournament stats)       │
│   • GroundsViewModel      (Venues CRUD & match assignments)            │
│   • TeamPlayerViewModel   (Player pool, team rosters, solo settings)   │
│   • StatsViewModel        (Lifetime & tournament team/player rankings) │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                      Domain & Calculation Engines                      │
│   • TournamentScheduler   (Circle method for N teams x K encounters)   │
│   • KnockoutProgression   (Top 4 qualification: SF1, SF2, Final tree)  │
│   • CricketScoringEngine  (Runs, extras, penalties, solo non-reset)    │
│   • MiniTestEngine        (Lead/trail tracking, 4-innings targets)     │
│   • NrrEngine             (Net Run Rate calculation for standings)     │
│   • PlayerOfTheMatchIndex (Automated performance formula + override)   │
│   • StatsAggregator       (Aggregates tournament & lifetime stats)     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
┌────────────────────────────────────▼───────────────────────────────────┐
│                       Room SQLite Database & DAOs                      │
│   • GroundEntity          • TournamentEntity    • MatchEntity          │
│   • PlayerEntity          • TeamEntity          • TeamPlayerCrossRef   │
│   • InningsEntity         • BallEntity (Complete ball-by-ball history) │
└────────────────────────────────────────────────────────────────────────┘
```

### Database Schema Architecture
- **GroundEntity**: `id`, `name`, `city`, `pitchType`, `boundaryLengthMeters`
- **TournamentEntity**: `id`, `name`, `format` (`ROUND_ROBIN`, `KNOCKOUT`, `LEAGUE_AND_KNOCKOUT`), `encountersPerOpponent`, `oversPerInnings`, `status`, `winnerTeamId`
- **TeamEntity**: `id`, `name`, `shortName`, `primaryColorHex`, `isSoloTeam`, `defaultSoloWickets`
- **PlayerEntity**: `id`, `name`, `role`, `battingStyle`, `bowlingStyle`, `jerseyNumber`
- **TeamPlayerCrossRef**: `teamId`, `playerId`
- **MatchEntity**: `id`, `tournamentId`, `groundId`, `stage` (`LEAGUE`, `QUARTER_FINAL`, `SEMI_FINAL_1`, `SEMI_FINAL_2`, `FINAL`, `STANDALONE`), `team1Id`, `team2Id`, `matchType` (`STANDARD_LIMITED_OVERS`, `BOX_GULLY`, `SOLO_DUEL`, `MINI_TEST_2_INNINGS`), `totalOversPerInnings`, `ballsPerOver`, `soloPlayer1Wickets`, `soloPlayer2Wickets`, `tossWinnerTeamId`, `tossDecision`, `status` (`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `ABANDONED_RAIN`, `DECLARED_WINNER`), `winnerTeamId`, `potmPlayerId`, `resultNote`
- **InningsEntity**: `id`, `matchId`, `inningsIndex` (1 to 4), `battingTeamId`, `bowlingTeamId`, `runs`, `wickets`, `ballsBowled`, `penaltyRuns`, `isCompleted`
- **BallEntity**: `id`, `matchId`, `inningsId`, `overIndex`, `ballInOver`, `strikerPlayerId`, `nonStrikerPlayerId`, `bowlerPlayerId`, `runsOffBat`, `extrasType`, `extrasRuns`, `isWicket`, `dismissalType`, `outPlayerId`, `isPenaltyRun`, `isUndo`

---

## 4. Implementation Steps (Execution Roadmap)

1. **Step 1: Database Foundation & Domain Calculation Engines**
   - Implement all Room entities, DAOs, and repository layer.
   - Implement `TournamentScheduler` (round-robin with configurable encounters) and `KnockoutProgression` (Top 4 semi-finals and final).
   - Implement `CricketScoringEngine` (runs, extras, penalties, solo non-resetting wickets, continuous bowling, and instant undo).
   - Implement `PlayerOfTheMatchIndex` calculation and `NrrEngine`.
   - Seed pre-loaded grounds, teams, and master player pool for immediate launch.
2. **Step 2: Grounds & Venues Hub**
   - Ground creation and detail screen (city, pitch surface, boundary dimensions).
3. **Step 3: Teams & Master Player Pool**
   - Central Player Pool screen (create/edit cricketers with role and styles).
   - Team manager with squad assignment and Solo Player mode toggle (2–10 wickets).
4. **Step 4: Live Scoring Console with Tactile Keypad**
   - Scorebar with CRR/RRR, target/lead, striker/non-striker/bowler cards.
   - Over balls timeline with color-coded chips.
   - Runs keypad (0–6, Wd, Nb, B, Lb, Wicket, Undo, Swap Ends).
   - Action dialogs: **Add Penalty Runs**, **Edit Lineups Mid-Match**, **Abandon (Rain)**, and **Declare Winner**.
5. **Step 5: Tournaments Hub & Auto-Knockouts**
   - Tournament wizard with automated fixtures and configurable encounters.
   - Dynamic Points Table with Net Run Rate, Wins, Losses, and Rainouts (1 pt each).
   - Knockout tree visualization with automated Top 4 semi-final seeding and champion podium.
6. **Step 6: Statistics Hub (Tournament & Lifetime)**
   - Dual-scope stats view:
     - Team stats: in-tournament table vs. all-time team records.
     - Player stats: tournament Orange/Purple Cap vs. lifetime career leaderboards.
7. **Step 7: Verification & Build Compilation**
   - Run `compile_applet` and verify error-free Android compilation.
