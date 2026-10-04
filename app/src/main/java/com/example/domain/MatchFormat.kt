package com.example.domain

import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.MatchStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Display helpers for match lists and cards. */
object MatchFormat {

    /** "145/6 (20)" or "120/10 & 98/4 (12.3)" for two-innings matches. Null if the team hasn't batted. */
    fun teamScore(match: MatchEntity, innings: List<InningsEntity>, teamId: Long): String? {
        val mine = innings.filter { it.battingTeamId == teamId }.sortedBy { it.inningsIndex }
        if (mine.isEmpty()) return null
        return mine.joinToString(" & ") { inn ->
            val total = inn.runs + inn.penaltyRuns
            val last = inn == mine.last()
            val overs = CricketRules.oversText(inn.ballsBowled, match.ballsPerOver)
            if (last) "$total/${inn.wickets} ($overs)" else "$total/${inn.wickets}"
        }
    }

    fun shortDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

    fun longDate(millis: Long): String = SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

    /** One line describing where the match stands. */
    fun statusLine(match: MatchEntity): String = when (match.status) {
        MatchStatus.SCHEDULED -> "Not started"
        MatchStatus.IN_PROGRESS -> "Live"
        else -> match.resultSummary.ifBlank { match.status.label }
    }
}
