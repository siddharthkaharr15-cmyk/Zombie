package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_stats")
data class GameStats(
    @PrimaryKey val id: Int = 1,
    val hpUpgradeLevel: Int = 1,
    val gunUpgradeLevel: Int = 1,
    val regenUpgradeLevel: Int = 1,
    val outpostUpgradeLevel: Int = 1,
    val highestWaveReached: Int = 1,
    val lifetimeEarnings: Long = 0L,
    val totalZombiesKilled: Int = 0,
    val totalMoneyEarned: Long = 0L,
    val totalRunsPlayed: Int = 0,
    val bossesDefeated: Int = 0,
    val playTimeSeconds: Long = 0L,
    val prestigeLevel: Int = 0,
    val prestigePoints: Int = 0,
    val lastDailyRewardTime: Long = 0L,
    val consecutiveDailyDays: Int = 0,
    val completedAchievementsMask: Int = 0,
    val performanceModeOn: Boolean = false
)
