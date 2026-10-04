package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.MatchStage
import com.example.data.model.MatchStatus
import com.example.data.model.MatchType
import com.example.data.model.PlayerRole
import com.example.data.model.TossDecision
import com.example.data.model.TournamentFormat
import com.example.data.model.TournamentStatus

class Converters {
    @TypeConverter
    fun fromPlayerRole(value: PlayerRole): String = value.name
    @TypeConverter
    fun toPlayerRole(value: String): PlayerRole = try { PlayerRole.valueOf(value) } catch (e: Exception) { PlayerRole.ALL_ROUNDER }

    @TypeConverter
    fun fromTournamentFormat(value: TournamentFormat): String = value.name
    @TypeConverter
    fun toTournamentFormat(value: String): TournamentFormat = try { TournamentFormat.valueOf(value) } catch (e: Exception) { TournamentFormat.LEAGUE_AND_KNOCKOUT }

    @TypeConverter
    fun fromTournamentStatus(value: TournamentStatus): String = value.name
    @TypeConverter
    fun toTournamentStatus(value: String): TournamentStatus = try { TournamentStatus.valueOf(value) } catch (e: Exception) { TournamentStatus.UPCOMING }

    @TypeConverter
    fun fromMatchStage(value: MatchStage): String = value.name
    @TypeConverter
    fun toMatchStage(value: String): MatchStage = try { MatchStage.valueOf(value) } catch (e: Exception) { MatchStage.STANDALONE }

    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name
    @TypeConverter
    fun toMatchType(value: String): MatchType = try { MatchType.valueOf(value) } catch (e: Exception) { MatchType.STANDARD_LIMITED_OVERS }

    @TypeConverter
    fun fromTossDecision(value: TossDecision?): String? = value?.name
    @TypeConverter
    fun toTossDecision(value: String?): TossDecision? = value?.let { try { TossDecision.valueOf(it) } catch (e: Exception) { null } }

    @TypeConverter
    fun fromMatchStatus(value: MatchStatus): String = value.name
    @TypeConverter
    fun toMatchStatus(value: String): MatchStatus = try { MatchStatus.valueOf(value) } catch (e: Exception) { MatchStatus.SCHEDULED }

    @TypeConverter
    fun fromExtraType(value: ExtraType): String = value.name
    @TypeConverter
    fun toExtraType(value: String): ExtraType = try { ExtraType.valueOf(value) } catch (e: Exception) { ExtraType.NONE }

    @TypeConverter
    fun fromDismissalType(value: DismissalType): String = value.name
    @TypeConverter
    fun toDismissalType(value: String): DismissalType = try { DismissalType.valueOf(value) } catch (e: Exception) { DismissalType.NONE }
}
