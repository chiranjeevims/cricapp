# CricScore 2.0: changes

## The two reported bugs

**Matches could not be deleted.** There was no delete option anywhere in the app.
Matches can now be deleted from Home, the Matches tab, the match's own menu, and a tournament's fixture list. All of the match's innings and balls go with it, and tournament tables and brackets update straight away.

**A declared winner didn't show in the tournament points table.** There were two causes:
1. The tournament screen loaded its data once and never refreshed. It is now live, so any result (scored, awarded, abandoned, reset or deleted) updates the table, fixtures and stats immediately.
2. Matches finished on the field were never saved as completed. The **Done** button only went back, so the winner, result and player of the match were never stored and the table never counted them. Results are now saved automatically the moment a match ends. Matches already affected in your data are repaired on first launch.

## Other bugs fixed

Scoring
- Strike never rotated. Singles, threes and leg byes, plus the change of ends after each over, were worked out and then thrown away.
- After a wicket, the batter who was out stayed at the crease. The chosen new batter never came in.
- Undo removed the ball but didn't restore who was batting and bowling.
- The new bowler chosen at the end of an over was forgotten when the app was reopened. The over-end dialog could also be dismissed, leaving the same bowler to bowl consecutive overs.
- Wides, no-balls, byes and leg byes could only ever be 1 run, so there was no way to record a no-ball hit for four, or a wide that went for five.
- Run outs couldn't record runs completed. Whether the striker or non-striker was out was ignored.
- A side was "all out" only at 10 wickets, even with a 4-player squad. With no batter left the screen got stuck. It's now squad size minus one (or a solo side's lives).
- "Won by X wickets" always assumed 10 wickets, and solo sides used the wrong side's lives.
- Run rates, required rates and net run rate always assumed 6-ball overs.
- Awarded and abandoned matches still showed the keypad and could carry on being scored.
- Retired hurt counted as a wicket. It's now a substitution under Change players. "Retired out" is still a wicket.
- Mini test matches: an innings defeat set an impossible negative target instead of ending the match. A side all out one run short was called a draw instead of a tie.
- Maidens were always shown as 0. Byes and leg byes wrongly spoiled maidens in the stats.
- A run-out non-striker wasn't counted as out in career stats. Batters dismissed without facing, and the non-striker, were missing from scorecards.
- Two quick taps could record the same ball twice, or race with undo.
- The penalty runs and edit lineup tools existed but couldn't be reached.

Tournaments
- The back button on a tournament did nothing, so you could never get back to the list.
- Knockout results (semi-finals and final) were counted in the league points table.
- A pure league never declared a champion.
- League + playoffs with fewer than 4 teams never moved on to the playoffs.
- A 3-team knockout made a team play itself. Knockouts with 5 to 7 teams silently left teams out, and quarter-finals never moved on to semi-finals.
- Tied or washed-out knockout matches had no winner, so the bracket got stuck.
- Tournament matches were always started with team 1 batting (no toss), and stayed "Scheduled" until the second innings.
- Tournament status never changed from "Upcoming" until the final.
- Fixtures behind a result that later changed (reset, reopened, deleted) were left in place. They're now withdrawn and rebuilt.

Data and app
- Deleting a team left orphan matches showing "Team 1 v Team 2". Teams in a tournament are now protected. Otherwise their matches are removed together with them.
- Players with match records could be deleted, leaving "Unknown" in scorecards. That's now blocked, with an explanation.
- Deleting a tournament left its matches behind as stray matches. There was also no way to delete a tournament at all.
- Quick match setup let a team play itself.
- The pitch type couldn't be selected when adding a ground.
- Teams, players and grounds couldn't be edited, and captain and wicket-keeper couldn't be set.
- Rotating the phone reset the screen and lost the match being scored.
- Screens inside the main layout got doubled top padding.
- A debug build in Android Studio failed because of a missing `debug.keystore`.
- The launcher icon had a malformed bail drawn far off to the side.

## Redesign

- New look: deep slate scoreboard panels with chalk-white numerals, saffron highlights, and red kept for wickets only. Each team gets its own colour badge.
- Barlow and Barlow Condensed fonts, with condensed tabular figures for scores and tables, readable in sunlight.
- Light, dark, or follow the phone (Settings on Home). The status bar follows the chosen theme.
- New launcher icon.

## New features

- Opening batters and bowler are chosen at the start of every innings.
- Toss, with a coin flip, for both quick matches and tournament fixtures.
- Faster extras: tap Wd, Nb, Bye or LB, then the runs.
- Wicket dialog: how out, who's out, runs completed, fielder or keeper, next batter.
- Choosing the next bowler shows each bowler's figures and blocks consecutive overs.
- Live panel: run rate, required rate, "need X from Y balls", projected score, partnership, lives left for solo batters, lead or trail in mini tests.
- This over, recent overs, and live batter and bowler figures.
- Match tools: penalty runs, change players (substitutes, retired hurt, late arrivals added from the pool), 5+ runs, change overs (rain-shortened), end innings early, award the match, abandon, reopen, reset, rematch, delete.
- Undo works across overs and innings, and reopens a finished match.
- Full scorecard: dismissals, extras breakdown, did not bat, fall of wickets, bowling with wides and no-balls, over-by-over balls, a runs-per-over chart with wickets marked, and match info.
- Share the scorecard as text (WhatsApp and so on).
- Player of the match is picked automatically, and you can change it.
- Knockout ties and washouts: choose who goes through (super over, bowl-out, coin toss or higher seed).
- Tournaments: a format picker with a summary of how many matches it creates, seeding order for knockouts, automatic byes, and live progress.
- Points table with recent form dots and the playoff line.
- Tournament stats: most runs, most wickets, most sixes, player of the match awards, team records, and a champion banner.
- Rename and delete tournaments. Walkover, no result, reopen, reset or delete any fixture.
- Matches tab with search and filters.
- Career stats: batting, bowling and fielding leaderboards with sorting, player profiles, team records with titles and head-to-head.
- Grounds show matches played and the average first-innings score.
- Fresh installs come with four full 6-player sample teams and two solo sides.
- The screen stays on while scoring, and the keypad gives haptic feedback.
