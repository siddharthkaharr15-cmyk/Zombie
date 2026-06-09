package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameStatsRepository(private val gameStatsDao: GameStatsDao) {
    val gameStats: Flow<GameStats> = gameStatsDao.getGameStats()
        .map { it ?: GameStats() }

    suspend fun saveStats(stats: GameStats) {
        gameStatsDao.saveGameStats(stats)
    }
}
