package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BallEntity
import com.example.data.model.GroundEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.PlayerEntity
import com.example.data.model.TeamEntity
import com.example.data.model.TeamPlayerCrossRef
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentTeamCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE ASC")
    fun getAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllPlayersSync(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getPlayerById(id: Long): PlayerEntity?

    @Query("SELECT * FROM players WHERE id IN (:ids)")
    suspend fun getPlayersByIds(ids: List<Long>): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<PlayerEntity>): List<Long>

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Delete
    suspend fun deletePlayer(player: PlayerEntity)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY name COLLATE NOCASE ASC")
    fun getAllTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllTeamsSync(): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getTeamById(id: Long): TeamEntity?

    @Query("SELECT * FROM teams WHERE id = :id")
    fun getTeamFlowById(id: Long): Flow<TeamEntity?>

    @Query("SELECT * FROM teams WHERE id IN (:ids)")
    suspend fun getTeamsByIds(ids: List<Long>): List<TeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeams(teams: List<TeamEntity>): List<Long>

    @Update
    suspend fun updateTeam(team: TeamEntity)

    @Delete
    suspend fun deleteTeam(team: TeamEntity)

    @Query(
        """
        SELECT p.* FROM players p
        INNER JOIN team_players tp ON p.id = tp.playerId
        WHERE tp.teamId = :teamId
        ORDER BY tp.isCaptain DESC, p.name COLLATE NOCASE ASC
        """
    )
    fun getPlayersForTeam(teamId: Long): Flow<List<PlayerEntity>>

    @Query(
        """
        SELECT p.* FROM players p
        INNER JOIN team_players tp ON p.id = tp.playerId
        WHERE tp.teamId = :teamId
        ORDER BY tp.isCaptain DESC, p.name COLLATE NOCASE ASC
        """
    )
    suspend fun getPlayersForTeamSync(teamId: Long): List<PlayerEntity>

    @Query("SELECT * FROM team_players WHERE teamId = :teamId")
    suspend fun getTeamPlayerCrossRefs(teamId: Long): List<TeamPlayerCrossRef>

    @Query("SELECT * FROM team_players")
    fun getAllTeamPlayerCrossRefs(): Flow<List<TeamPlayerCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamPlayerCrossRef(crossRef: TeamPlayerCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeamPlayerCrossRefs(crossRefs: List<TeamPlayerCrossRef>)

    @Query("DELETE FROM team_players WHERE teamId = :teamId AND playerId = :playerId")
    suspend fun removePlayerFromTeam(teamId: Long, playerId: Long)

    @Query("UPDATE team_players SET isCaptain = 0 WHERE teamId = :teamId")
    suspend fun clearCaptain(teamId: Long)

    @Query("UPDATE team_players SET isWicketKeeper = 0 WHERE teamId = :teamId")
    suspend fun clearWicketKeeper(teamId: Long)

    @Query("UPDATE team_players SET isCaptain = 1 WHERE teamId = :teamId AND playerId = :playerId")
    suspend fun setCaptain(teamId: Long, playerId: Long)

    @Query("UPDATE team_players SET isWicketKeeper = 1 WHERE teamId = :teamId AND playerId = :playerId")
    suspend fun setWicketKeeper(teamId: Long, playerId: Long)
}

@Dao
interface GroundDao {
    @Query("SELECT * FROM grounds ORDER BY name COLLATE NOCASE ASC")
    fun getAllGrounds(): Flow<List<GroundEntity>>

    @Query("SELECT * FROM grounds WHERE id = :id")
    suspend fun getGroundById(id: Long): GroundEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGround(ground: GroundEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrounds(grounds: List<GroundEntity>): List<Long>

    @Update
    suspend fun updateGround(ground: GroundEntity)

    @Delete
    suspend fun deleteGround(ground: GroundEntity)
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun getAllTournaments(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    suspend fun getAllTournamentsSync(): List<TournamentEntity>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: Long): TournamentEntity?

    @Query("SELECT * FROM tournaments WHERE id = :id")
    fun getTournamentFlowById(id: Long): Flow<TournamentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: TournamentEntity): Long

    @Update
    suspend fun updateTournament(tournament: TournamentEntity)

    @Delete
    suspend fun deleteTournament(tournament: TournamentEntity)

    @Query(
        """
        SELECT t.* FROM teams t
        INNER JOIN tournament_teams tt ON t.id = tt.teamId
        WHERE tt.tournamentId = :tournamentId
        ORDER BY tt.rowid ASC
        """
    )
    fun getTeamsForTournament(tournamentId: Long): Flow<List<TeamEntity>>

    @Query(
        """
        SELECT t.* FROM teams t
        INNER JOIN tournament_teams tt ON t.id = tt.teamId
        WHERE tt.tournamentId = :tournamentId
        ORDER BY tt.rowid ASC
        """
    )
    suspend fun getTeamsForTournamentSync(tournamentId: Long): List<TeamEntity>

    @Query("SELECT * FROM tournament_teams")
    fun getAllTournamentTeamCrossRefs(): Flow<List<TournamentTeamCrossRef>>

    @Query(
        """
        SELECT tr.* FROM tournaments tr
        INNER JOIN tournament_teams tt ON tr.id = tt.tournamentId
        WHERE tt.teamId = :teamId
        """
    )
    suspend fun getTournamentsForTeam(teamId: Long): List<TournamentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournamentTeamCrossRef(crossRef: TournamentTeamCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournamentTeamCrossRefs(crossRefs: List<TournamentTeamCrossRef>)
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY createdAt DESC, id DESC")
    fun getAllMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY createdAt DESC, id DESC")
    suspend fun getAllMatchesSync(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId ORDER BY roundIndex ASC, matchNumberInRound ASC, id ASC")
    fun getMatchesForTournament(tournamentId: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId ORDER BY roundIndex ASC, matchNumberInRound ASC, id ASC")
    suspend fun getMatchesForTournamentSync(tournamentId: Long): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getMatchById(id: Long): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :id")
    fun getMatchFlowById(id: Long): Flow<MatchEntity?>

    @Query("SELECT COUNT(*) FROM matches WHERE team1Id = :teamId OR team2Id = :teamId")
    suspend fun countMatchesForTeam(teamId: Long): Int

    @Query("SELECT * FROM matches WHERE team1Id = :teamId OR team2Id = :teamId")
    suspend fun getMatchesForTeamSync(teamId: Long): List<MatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>): List<Long>

    @Update
    suspend fun updateMatch(match: MatchEntity)

    @Delete
    suspend fun deleteMatch(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteMatchById(id: Long)

    @Query("DELETE FROM matches WHERE tournamentId = :tournamentId")
    suspend fun deleteMatchesForTournament(tournamentId: Long)
}

@Dao
interface InningsDao {
    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsIndex ASC")
    fun getInningsForMatch(matchId: Long): Flow<List<InningsEntity>>

    @Query("SELECT * FROM innings WHERE matchId = :matchId ORDER BY inningsIndex ASC")
    suspend fun getInningsForMatchSync(matchId: Long): List<InningsEntity>

    @Query("SELECT * FROM innings ORDER BY matchId ASC, inningsIndex ASC")
    fun getAllInnings(): Flow<List<InningsEntity>>

    @Query("SELECT * FROM innings ORDER BY matchId ASC, inningsIndex ASC")
    suspend fun getAllInningsSync(): List<InningsEntity>

    @Query(
        """
        SELECT i.* FROM innings i
        INNER JOIN matches m ON i.matchId = m.id
        WHERE m.tournamentId = :tournamentId
        ORDER BY i.matchId ASC, i.inningsIndex ASC
        """
    )
    fun getInningsForTournament(tournamentId: Long): Flow<List<InningsEntity>>

    @Query(
        """
        SELECT i.* FROM innings i
        INNER JOIN matches m ON i.matchId = m.id
        WHERE m.tournamentId = :tournamentId
        ORDER BY i.matchId ASC, i.inningsIndex ASC
        """
    )
    suspend fun getInningsForTournamentSync(tournamentId: Long): List<InningsEntity>

    @Query("SELECT * FROM innings WHERE id = :id")
    suspend fun getInningsById(id: Long): InningsEntity?

    @Query("SELECT * FROM innings WHERE matchId = :matchId AND inningsIndex = :index")
    suspend fun getInningsByIndex(matchId: Long, index: Int): InningsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInnings(innings: InningsEntity): Long

    @Update
    suspend fun updateInnings(innings: InningsEntity)

    @Query("DELETE FROM innings WHERE id = :id")
    suspend fun deleteInningsById(id: Long)

    @Query("DELETE FROM innings WHERE matchId = :matchId")
    suspend fun deleteInningsForMatch(matchId: Long)
}

@Dao
interface BallDao {
    @Query("SELECT * FROM balls WHERE inningsId = :inningsId ORDER BY id ASC")
    fun getBallsForInnings(inningsId: Long): Flow<List<BallEntity>>

    @Query("SELECT * FROM balls WHERE inningsId = :inningsId ORDER BY id ASC")
    suspend fun getBallsForInningsSync(inningsId: Long): List<BallEntity>

    @Query("SELECT * FROM balls WHERE matchId = :matchId ORDER BY id ASC")
    fun getBallsForMatch(matchId: Long): Flow<List<BallEntity>>

    @Query("SELECT * FROM balls WHERE matchId = :matchId ORDER BY id ASC")
    suspend fun getBallsForMatchSync(matchId: Long): List<BallEntity>

    @Query("SELECT * FROM balls ORDER BY id ASC")
    fun getAllBalls(): Flow<List<BallEntity>>

    @Query("SELECT * FROM balls ORDER BY id ASC")
    suspend fun getAllBallsSync(): List<BallEntity>

    @Query(
        """
        SELECT b.* FROM balls b
        INNER JOIN matches m ON b.matchId = m.id
        WHERE m.tournamentId = :tournamentId
        ORDER BY b.id ASC
        """
    )
    fun getBallsForTournament(tournamentId: Long): Flow<List<BallEntity>>

    @Query("SELECT * FROM balls WHERE inningsId = :inningsId ORDER BY id DESC LIMIT 1")
    suspend fun getLastBallForInnings(inningsId: Long): BallEntity?

    @Query(
        """
        SELECT COUNT(*) FROM balls
        WHERE strikerPlayerId = :playerId OR nonStrikerPlayerId = :playerId
           OR bowlerPlayerId = :playerId OR outPlayerId = :playerId OR fielderPlayerId = :playerId
        """
    )
    suspend fun countBallsInvolvingPlayer(playerId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBall(ball: BallEntity): Long

    @Delete
    suspend fun deleteBall(ball: BallEntity)

    @Query("DELETE FROM balls WHERE id = :id")
    suspend fun deleteBallById(id: Long)
}
