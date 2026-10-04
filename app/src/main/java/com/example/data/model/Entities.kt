package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: PlayerRole = PlayerRole.ALL_ROUNDER,
    val battingStyle: String = "Right-hand bat",
    val bowlingStyle: String = "Right-arm medium",
    val jerseyNumber: Int = 7
)

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortName: String,
    val primaryColorHex: String = "#0D5E36",
    val isSoloTeam: Boolean = false,
    val defaultSoloWickets: Int = 3
)

@Entity(
    tableName = "team_players",
    primaryKeys = ["teamId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("teamId"), Index("playerId")]
)
data class TeamPlayerCrossRef(
    val teamId: Long,
    val playerId: Long,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false
)

@Entity(tableName = "grounds")
data class GroundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val city: String,
    val pitchType: String = "Turf",
    val boundarySizeMeters: Int = 65
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val format: TournamentFormat = TournamentFormat.LEAGUE_AND_KNOCKOUT,
    val encountersPerOpponent: Int = 1,
    val oversPerInnings: Int = 10,
    val ballsPerOver: Int = 6,
    val status: TournamentStatus = TournamentStatus.UPCOMING,
    val winnerTeamId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tournament_teams",
    primaryKeys = ["tournamentId", "teamId"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tournamentId"), Index("teamId")]
)
data class TournamentTeamCrossRef(
    val tournamentId: Long,
    val teamId: Long
)

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournamentId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = GroundEntity::class,
            parentColumns = ["id"],
            childColumns = ["groundId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("tournamentId"), Index("groundId")]
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tournamentId: Long? = null,
    val stage: MatchStage = MatchStage.STANDALONE,
    val roundIndex: Int = 1,
    val matchNumberInRound: Int = 1,
    val groundId: Long? = null,
    val team1Id: Long,
    val team2Id: Long,
    val matchType: MatchType = MatchType.STANDARD_LIMITED_OVERS,
    val totalOversPerInnings: Int = 10,
    val ballsPerOver: Int = 6,
    val soloPlayer1Wickets: Int = 3,
    val soloPlayer2Wickets: Int = 3,
    val tossWinnerTeamId: Long? = null,
    val tossDecision: TossDecision? = null,
    val currentInningsIndex: Int = 1,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val winnerTeamId: Long? = null,
    val potmPlayerId: Long? = null,
    val resultSummary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "innings",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("matchId")]
)
data class InningsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val inningsIndex: Int, // 1 to 4
    val battingTeamId: Long,
    val bowlingTeamId: Long,
    val runs: Int = 0,
    val wickets: Int = 0,
    val ballsBowled: Int = 0,
    val penaltyRuns: Int = 0,
    val isCompleted: Boolean = false,
    // Added in DB version 2: the live crease state is persisted so strike rotation,
    // new batters and bowler changes survive reloads, undo and app restarts.
    val strikerId: Long? = null,
    val nonStrikerId: Long? = null,
    val bowlerId: Long? = null,
    // Wickets that end this innings (squad size - 1, or the solo player's lives).
    val maxWickets: Int = 10
)

@Entity(
    tableName = "balls",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InningsEntity::class,
            parentColumns = ["id"],
            childColumns = ["inningsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("matchId"), Index("inningsId")]
)
data class BallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val inningsId: Long,
    val overIndex: Int,
    val ballInOver: Int,
    val legalBallNumber: Int,
    val strikerPlayerId: Long,
    val nonStrikerPlayerId: Long?,
    val bowlerPlayerId: Long,
    val runsOffBat: Int = 0,
    val extraType: ExtraType = ExtraType.NONE,
    val extraRuns: Int = 0,
    val isWicket: Boolean = false,
    val dismissalType: DismissalType = DismissalType.NONE,
    val outPlayerId: Long? = null,
    val fielderPlayerId: Long? = null,
    val isPenaltyRun: Boolean = false,
    val commentary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
