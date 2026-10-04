package com.example

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.repository.CricketRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MatchSetupDialog
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.screens.SquadsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TournamentsScreen
import com.example.ui.scoring.ScoringScreen
import com.example.ui.theme.CricScoreTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.viewmodels.AppViewModelFactory
import com.example.ui.viewmodels.MatchViewModel
import com.example.ui.viewmodels.MatchesViewModel
import com.example.ui.viewmodels.SquadsViewModel
import com.example.ui.viewmodels.StatsViewModel
import com.example.ui.viewmodels.TournamentViewModel
import kotlinx.coroutines.flow.merge

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MATCHES("Matches", Icons.Default.SportsCricket),
    TOURNAMENTS("Tournaments", Icons.Default.EmojiEvents),
    SQUADS("Squads", Icons.Default.Groups),
    STATS("Stats", Icons.Default.Leaderboard)
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { CricketRepository(AppDatabase.getInstance(applicationContext)) }
    private val factory by lazy { AppViewModelFactory(repository) }

    private val matchViewModel: MatchViewModel by viewModels { factory }
    private val matchesViewModel: MatchesViewModel by viewModels { factory }
    private val tournamentViewModel: TournamentViewModel by viewModels { factory }
    private val squadsViewModel: SquadsViewModel by viewModels { factory }
    private val statsViewModel: StatsViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("cricscore_settings", MODE_PRIVATE)

        setContent {
            var themeMode by remember { mutableStateOf(readThemeMode(prefs)) }
            val dark = isAppInDarkTheme(themeMode)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            CricScoreTheme(themeMode = themeMode) {
                CricScoreApp(
                    matchVm = matchViewModel,
                    matchesVm = matchesViewModel,
                    tournamentVm = tournamentViewModel,
                    squadsVm = squadsViewModel,
                    statsVm = statsViewModel,
                    themeMode = themeMode,
                    onThemeChange = {
                        themeMode = it
                        prefs.edit().putString(KEY_THEME, it.name).apply()
                    }
                )
            }
        }
    }

    private fun readThemeMode(prefs: SharedPreferences): ThemeMode {
        val saved = prefs.getString(KEY_THEME, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
    }
}

@Composable
fun CricScoreApp(
    matchVm: MatchViewModel,
    matchesVm: MatchesViewModel,
    tournamentVm: TournamentViewModel,
    squadsVm: SquadsViewModel,
    statsVm: StatsViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var scoringMatchId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var createTournamentRequest by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val teams by squadsVm.teams.collectAsStateWithLifecycle()
    val players by squadsVm.players.collectAsStateWithLifecycle()
    val grounds by squadsVm.grounds.collectAsStateWithLifecycle()
    val allPlayers = players.orEmpty().map { it.player }
    val allGrounds = grounds.orEmpty().map { it.ground }

    val openMatch: (Long) -> Unit = { id ->
        matchVm.open(id)
        scoringMatchId = id
    }

    LaunchedEffect(scoringMatchId) {
        val id = scoringMatchId
        if (id == null) matchVm.close() else if (matchVm.activeMatchId.value != id) matchVm.open(id)
    }
    LaunchedEffect(Unit) {
        merge(matchesVm.events, tournamentVm.events, squadsVm.events).collect { snackbar.showSnackbar(it) }
    }

    val scoringId = scoringMatchId
    if (scoringId != null) {
        ScoringScreen(
            vm = matchVm,
            allPlayers = allPlayers,
            grounds = allGrounds,
            onBack = { scoringMatchId = null },
            onOpenMatch = openMatch
        )
        return
    }

    BackHandler(enabled = tab != AppTab.HOME) { tab = AppTab.HOME }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.values().forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(
                    vm = matchesVm,
                    onNewMatch = { showSetup = true },
                    onNewTournament = {
                        tab = AppTab.TOURNAMENTS
                        createTournamentRequest = true
                    },
                    onOpenMatch = openMatch,
                    onSeeAllMatches = { tab = AppTab.MATCHES },
                    onOpenSettings = { showSettings = true }
                )
                AppTab.MATCHES -> MatchesScreen(
                    vm = matchesVm,
                    onNewMatch = { showSetup = true },
                    onOpenMatch = openMatch
                )
                AppTab.TOURNAMENTS -> TournamentsScreen(
                    vm = tournamentVm,
                    teams = teams.orEmpty(),
                    grounds = allGrounds,
                    onOpenMatch = openMatch,
                    createRequested = createTournamentRequest,
                    onCreateRequestHandled = { createTournamentRequest = false }
                )
                AppTab.SQUADS -> SquadsScreen(vm = squadsVm)
                AppTab.STATS -> StatsScreen(vm = statsVm)
            }
        }
    }

    if (showSetup) {
        MatchSetupDialog(
            teams = teams.orEmpty(),
            grounds = allGrounds,
            onDismiss = { showSetup = false },
            onCreateTeams = {
                showSetup = false
                tab = AppTab.SQUADS
            },
            onStart = { t1, t2, ground, type, overs, bpo, solo1, solo2, tossWinner, decision ->
                matchesVm.createMatch(t1, t2, ground, type, overs, bpo, solo1, solo2, tossWinner, decision) { id ->
                    showSetup = false
                    openMatch(id)
                }
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            onDismiss = { showSettings = false }
        )
    }
}
