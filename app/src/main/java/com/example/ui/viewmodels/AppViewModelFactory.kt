package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.CricketRepository

/** Creates the app's view models so they survive rotation and other configuration changes. */
class AppViewModelFactory(private val repository: CricketRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(MatchViewModel::class.java) -> MatchViewModel(repository)
            modelClass.isAssignableFrom(MatchesViewModel::class.java) -> MatchesViewModel(repository)
            modelClass.isAssignableFrom(TournamentViewModel::class.java) -> TournamentViewModel(repository)
            modelClass.isAssignableFrom(SquadsViewModel::class.java) -> SquadsViewModel(repository)
            modelClass.isAssignableFrom(StatsViewModel::class.java) -> StatsViewModel(repository)
            else -> throw IllegalArgumentException("Unknown view model ${modelClass.name}")
        }
        return vm as T
    }
}
