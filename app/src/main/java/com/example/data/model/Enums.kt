package com.example.data.model

enum class PlayerRole(val label: String, val short: String) {
    BATSMAN("Batter", "BAT"),
    BOWLER("Bowler", "BOWL"),
    ALL_ROUNDER("All-rounder", "AR"),
    WICKET_KEEPER("Wicket-keeper", "WK")
}

enum class TournamentFormat(val label: String, val description: String) {
    ROUND_ROBIN("League", "Everyone plays everyone. Top of the table wins."),
    KNOCKOUT("Knockout", "Lose and you're out. Byes are added automatically."),
    LEAGUE_AND_KNOCKOUT("League + playoffs", "League stage, then the top 4 play semi-finals and a final.")
}

enum class TournamentStatus(val label: String) {
    UPCOMING("Upcoming"),
    ONGOING("In progress"),
    COMPLETED("Completed")
}

enum class MatchStage(val label: String) {
    STANDALONE("Match"),
    LEAGUE("League"),
    KNOCKOUT_ROUND("Knockout round"),
    QUARTER_FINAL("Quarter-final"),
    SEMI_FINAL_1("Semi-final 1"),
    SEMI_FINAL_2("Semi-final 2"),
    FINAL("Final");

    val isKnockout: Boolean get() = this != STANDALONE && this != LEAGUE
}

enum class MatchType(val label: String, val description: String) {
    STANDARD_LIMITED_OVERS("Limited overs", "One innings each"),
    BOX_GULLY("Box / gully", "Short format, one innings each"),
    SOLO_DUEL("Solo duel", "One player per side with extra lives"),
    MINI_TEST_2_INNINGS("Mini test", "Two innings each")
}

enum class TossDecision(val label: String) {
    BAT("Bat first"),
    BOWL("Bowl first")
}

enum class MatchStatus(val label: String) {
    SCHEDULED("Scheduled"),
    IN_PROGRESS("Live"),
    COMPLETED("Completed"),
    ABANDONED_RAIN("No result"),
    DECLARED_WINNER("Awarded");

    /** True when the match has a final outcome and no more balls can be scored. */
    val isFinished: Boolean get() = this == COMPLETED || this == ABANDONED_RAIN || this == DECLARED_WINNER
}

enum class ExtraType(val code: String, val label: String) {
    NONE("", "None"),
    WIDE("Wd", "Wide"),
    NO_BALL("Nb", "No-ball"),
    BYE("B", "Bye"),
    LEG_BYE("Lb", "Leg bye"),
    PENALTY("Pen", "Penalty")
}

enum class DismissalType(val label: String) {
    NONE("Not out"),
    BOWLED("Bowled"),
    CAUGHT("Caught"),
    RUN_OUT("Run out"),
    STUMPED("Stumped"),
    LBW("LBW"),
    HIT_WICKET("Hit wicket"),
    RETIRED("Retired out");

    /** Wickets that count towards the bowler's figures. */
    val creditedToBowler: Boolean get() = this != RUN_OUT && this != RETIRED && this != NONE
}
