package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.BallEntity
import com.example.data.model.GroundEntity
import com.example.data.model.InningsEntity
import com.example.data.model.MatchEntity
import com.example.data.model.PlayerEntity
import com.example.data.model.PlayerRole
import com.example.data.model.TeamEntity
import com.example.data.model.TeamPlayerCrossRef
import com.example.data.model.TournamentEntity
import com.example.data.model.TournamentTeamCrossRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PlayerEntity::class,
        TeamEntity::class,
        TeamPlayerCrossRef::class,
        GroundEntity::class,
        TournamentEntity::class,
        TournamentTeamCrossRef::class,
        MatchEntity::class,
        InningsEntity::class,
        BallEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun groundDao(): GroundDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun matchDao(): MatchDao
    abstract fun inningsDao(): InningsDao
    abstract fun ballDao(): BallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Version 1 -> 2: keeps all existing teams, players, matches and scores.
         * Adds the persisted crease state (striker, non-striker, bowler) and the
         * wickets limit to every innings.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `innings` ADD COLUMN `strikerId` INTEGER")
                db.execSQL("ALTER TABLE `innings` ADD COLUMN `nonStrikerId` INTEGER")
                db.execSQL("ALTER TABLE `innings` ADD COLUMN `bowlerId` INTEGER")
                db.execSQL("ALTER TABLE `innings` ADD COLUMN `maxWickets` INTEGER NOT NULL DEFAULT 10")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cricscore_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).seedInitialData()
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }

    suspend fun seedInitialData() {
        val groundDao = groundDao()
        val playerDao = playerDao()
        val teamDao = teamDao()

        // Grounds
        groundDao.insertGrounds(
            listOf(
                GroundEntity(name = "Central Turf Arena", city = "Metro City", pitchType = "Turf", boundarySizeMeters = 65),
                GroundEntity(name = "Downtown Box Cricket Hub", city = "Uptown", pitchType = "Box Astro-Turf", boundarySizeMeters = 30),
                GroundEntity(name = "National Stadium Oval", city = "Capital City", pitchType = "Grass", boundarySizeMeters = 72)
            )
        )

        data class SeedPlayer(val name: String, val role: PlayerRole, val bat: String, val bowl: String, val jersey: Int)

        val squads = listOf(
            Triple(
                TeamEntity(name = "Royal Strikers", shortName = "RS", primaryColorHex = "#3B4FD8"),
                listOf(
                    SeedPlayer("Arjun Rao", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm medium", 18),
                    SeedPlayer("Vikram Shetty", PlayerRole.WICKET_KEEPER, "Left-hand bat", "None", 7),
                    SeedPlayer("Rahul Menon", PlayerRole.ALL_ROUNDER, "Right-hand bat", "Right-arm off-break", 23),
                    SeedPlayer("Karthik Iyer", PlayerRole.BOWLER, "Right-hand bat", "Right-arm fast", 93),
                    SeedPlayer("Sameer Khan", PlayerRole.BOWLER, "Left-hand bat", "Left-arm orthodox", 11),
                    SeedPlayer("Nikhil Gowda", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm leg-break", 45)
                ),
                0
            ),
            Triple(
                TeamEntity(name = "Titan Warriors", shortName = "TW", primaryColorHex = "#0E8FA3"),
                listOf(
                    SeedPlayer("Pranav Hegde", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm off-break", 4),
                    SeedPlayer("Rohan Das", PlayerRole.ALL_ROUNDER, "Left-hand bat", "Left-arm medium", 33),
                    SeedPlayer("Aditya Kulkarni", PlayerRole.WICKET_KEEPER, "Right-hand bat", "None", 17),
                    SeedPlayer("Imran Sheikh", PlayerRole.BOWLER, "Right-hand bat", "Right-arm fast-medium", 56),
                    SeedPlayer("Manish Patil", PlayerRole.BOWLER, "Right-hand bat", "Right-arm leg-break", 19),
                    SeedPlayer("Suresh Nair", PlayerRole.BATSMAN, "Left-hand bat", "Right-arm medium", 10)
                ),
                0
            ),
            Triple(
                TeamEntity(name = "Chennai Sparks", shortName = "CS", primaryColorHex = "#E58A00"),
                listOf(
                    SeedPlayer("Kiran Bhat", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm medium", 49),
                    SeedPlayer("Deepak Joshi", PlayerRole.BOWLER, "Right-hand bat", "Right-arm fast", 30),
                    SeedPlayer("Varun Reddy", PlayerRole.ALL_ROUNDER, "Right-hand bat", "Right-arm off-break", 8),
                    SeedPlayer("Harsha Murthy", PlayerRole.WICKET_KEEPER, "Left-hand bat", "None", 21),
                    SeedPlayer("Faisal Ahmed", PlayerRole.BOWLER, "Left-hand bat", "Left-arm fast", 64),
                    SeedPlayer("Gautam Pillai", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm leg-break", 3)
                ),
                0
            ),
            Triple(
                TeamEntity(name = "Knight Kings", shortName = "KK", primaryColorHex = "#8E3FD1"),
                listOf(
                    SeedPlayer("Yash Desai", PlayerRole.BATSMAN, "Left-hand bat", "Right-arm leg-break", 31),
                    SeedPlayer("Abhinav Singh", PlayerRole.ALL_ROUNDER, "Right-hand bat", "Right-arm fast-medium", 55),
                    SeedPlayer("Tarun Kamath", PlayerRole.WICKET_KEEPER, "Right-hand bat", "None", 12),
                    SeedPlayer("Naveen Kumar", PlayerRole.BOWLER, "Right-hand bat", "Right-arm fast", 99),
                    SeedPlayer("Siddharth Jain", PlayerRole.BOWLER, "Left-hand bat", "Left-arm orthodox", 27),
                    SeedPlayer("Mohan Prasad", PlayerRole.BATSMAN, "Right-hand bat", "Right-arm medium", 5)
                ),
                0
            ),
            Triple(
                TeamEntity(name = "Ayaan Solo Army", shortName = "ASA", primaryColorHex = "#D3364A", isSoloTeam = true, defaultSoloWickets = 3),
                listOf(SeedPlayer("Ayaan 'The Striker'", PlayerRole.ALL_ROUNDER, "Right-hand bat", "Right-arm fast", 9)),
                0
            ),
            Triple(
                TeamEntity(name = "Kabir One-Man", shortName = "KOM", primaryColorHex = "#C2410C", isSoloTeam = true, defaultSoloWickets = 4),
                listOf(SeedPlayer("Kabir 'Blaster'", PlayerRole.ALL_ROUNDER, "Left-hand bat", "Left-arm spin", 1)),
                0
            )
        )

        for ((team, players, _) in squads) {
            val teamId = teamDao.insertTeam(team)
            val refs = players.mapIndexed { index, p ->
                val playerId = playerDao.insertPlayer(
                    PlayerEntity(name = p.name, role = p.role, battingStyle = p.bat, bowlingStyle = p.bowl, jerseyNumber = p.jersey)
                )
                TeamPlayerCrossRef(
                    teamId = teamId,
                    playerId = playerId,
                    isCaptain = index == 0,
                    isWicketKeeper = p.role == PlayerRole.WICKET_KEEPER || players.size == 1
                )
            }
            teamDao.insertTeamPlayerCrossRefs(refs)
        }
    }
}
