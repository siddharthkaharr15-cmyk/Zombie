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
    val highestWaveNormal: Int = 1,
    val highestWaveHardcore: Int = 1,
    val highestWaveNightmare: Int = 1,
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
    val performanceModeOn: Boolean = false,
    val bankDollars: Long = 0L,
    val ownedWeaponsCSV: String = "Ranger P1,Crusher 12G,AR-X Defender,Vulcan M1",
    val ownedTurretsCSV: String = "Basic Turret",
    val equippedPistol: String = "Ranger P1",
    val equippedShotgun: String = "Crusher 12G",
    val equippedRifle: String = "AR-X Defender",
    val equippedMinigun: String = "Vulcan M1",
    val equippedTurretsCSV: String = "Basic Turret"
)
