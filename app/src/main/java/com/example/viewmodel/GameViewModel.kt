package com.example.viewmodel

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameStats
import com.example.data.GameStatsRepository
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random

enum class GameState {
    MENU, PLAYING, GAME_OVER
}

enum class ChestRewardType {
    MONEY,
    FREE_UPGRADE,
    TURRET_UPGRADE,
    REGEN_BOOST
}

data class ChestOption(
    val id: Int,
    val type: ChestRewardType,
    val title: String,
    val description: String,
    val amount: Long = 0L,
    var isOpened: Boolean = false
)

data class GameAchievement(
    val id: Int,
    val title: String,
    val description: String,
    val rewardCoins: Long,
    val isFulfilled: (GameViewModel) -> Boolean
)

val AchievementsList = listOf(
    GameAchievement(0, "First Blood", "Kill 1 zombie", 250L) { it.totalZombiesKilled >= 1 },
    GameAchievement(1, "Undead Exterminator", "Kill 100 zombies in career", 1000L) { it.totalZombiesKilled >= 100 },
    GameAchievement(2, "Giant Slayer", "Defeat 5 Boss Behemoths", 2500L) { it.bossesDefeated >= 5 },
    GameAchievement(3, "Outpost Tycoon", "Earn $10,000 total career coins", 2000L) { it.totalMoneyEarned >= 10000 },
    GameAchievement(4, "Nanotech Upgrade", "Reach Outpost Level 5 in a combat run", 1500L) { it.outpostLevel >= 5 },
    GameAchievement(5, "Doomsday Survivor", "Reach Wave 20 or higher", 3000L) { it.highestWaveReached >= 20 },
    GameAchievement(6, "Prestige Pioneer", "Perform a Prestige reset once", 5000L) { it.prestigeLevel >= 1 }
)

data class GunStats(
    val tierName: String,
    val damage: Float,
    val fireIntervalMs: Long,
    val projectileSpeed: Float,
    val bulletSize: Float,
    val colorHex: Long,
    val pierceCount: Int = 1
)

class GameViewModel(
    application: Application,
    private val repository: GameStatsRepository
) : AndroidViewModel(application) {

    // Persistent upgrade states loaded from DB
    var hpLevel by mutableIntStateOf(1)
    var gunLevel by mutableIntStateOf(1)
    var regenLevel by mutableIntStateOf(0)
    var outpostLevel by mutableIntStateOf(1)
    var lifetimeCoins by mutableLongStateOf(0L)
    var highestWaveReached by mutableIntStateOf(1)

    // Persistent Prestige and Systems
    var prestigeLevel by mutableIntStateOf(0)
    var prestigePoints by mutableIntStateOf(0)
    var lastDailyRewardTime by mutableLongStateOf(0L)
    var consecutiveDailyDays by mutableIntStateOf(0)
    var completedAchievementsMask by mutableIntStateOf(0)
    var isPerformanceModeOn by mutableStateOf(false)

    // Reward Chest System State
    var showChestSelection by mutableStateOf(false)
    val chestOptions = mutableStateListOf<ChestOption>()

    // Persistent statistical values
    var totalZombiesKilled by mutableIntStateOf(0)
    var totalMoneyEarned by mutableLongStateOf(0L)
    var totalRunsPlayed by mutableIntStateOf(0)
    var bossesDefeated by mutableIntStateOf(0)
    var playTimeSeconds by mutableLongStateOf(0L)
    private var playTimeAccumulator = 0f

    // Tactical configuration / settings states
    var soundOn by mutableStateOf(true)
    var musicOn by mutableStateOf(true)
    var vibrationOn by mutableStateOf(true)
    var qualitySetting by mutableStateOf("High")

    var masterVolume by mutableFloatStateOf(0.8f)
    var musicVolume by mutableFloatStateOf(0.4f)
    var sfxVolume by mutableFloatStateOf(0.6f)

    fun updateMasterVolume(vol: Float) {
        masterVolume = vol
        com.example.audio.GameAudioSynth.masterVolume = vol
    }

    fun updateMusicVolume(vol: Float) {
        musicVolume = vol
        com.example.audio.GameAudioSynth.musicVolume = vol
    }

    fun updateSfxVolume(vol: Float) {
        sfxVolume = vol
        com.example.audio.GameAudioSynth.sfxVolume = vol
    }

    fun toggleMusic(enabled: Boolean) {
        musicOn = enabled
        com.example.audio.GameAudioSynth.musicOn = enabled
    }

    fun toggleSound(enabled: Boolean) {
        soundOn = enabled
        com.example.audio.GameAudioSynth.soundOn = enabled
    }

    // Active Run replication for Continue Feature
    var canContinueRun by mutableStateOf(false)
    var savedWave by mutableIntStateOf(1)
    var savedBaseHp by mutableFloatStateOf(100f)
    var savedRunEarnings by mutableLongStateOf(0L)
    val savedTurrets = mutableStateListOf<TurretContainer>()

    // Selection state for turrets quick action panel
    var selectedTurretSlotId by mutableStateOf<Int?>(null)

    // Current Active Run states
    var gameState by mutableStateOf(GameState.MENU)
    var currentWave by mutableIntStateOf(1)
    var baseHp by mutableFloatStateOf(100f)
    var baseMaxHp by mutableFloatStateOf(100f)
    var runEarnings by mutableLongStateOf(0L)
    
    // Player status
    var playerPos by mutableStateOf(Vector2(400f, 680f))
    var playerAngle by mutableFloatStateOf(0f)

    // Lists for rendering - mutated on Main thread within update loop
    val zombies = mutableStateListOf<Zombie>()
    val bullets = mutableStateListOf<Bullet>()
    val particles = mutableStateListOf<Particle>()
    val damageNumbers = mutableStateListOf<DamageNumber>()
    val coins = mutableStateListOf<Coin>()
    val decals = mutableStateListOf<Decal>()
    val turrets = mutableStateListOf<Turret>()

    // Screen Shake Offset
    var shakeX by mutableFloatStateOf(0f)
    var shakeY by mutableFloatStateOf(0f)
    private var shakeMagnitude = 0f

    // Wave Management Spawning Status
    var isBetweenWaves by mutableStateOf(false)
    var isAutoSkipEnabled by mutableStateOf(false)
    var betweenWaveTimer by mutableFloatStateOf(0f)
    private var waveZombiesSpawned = 0
    private var totalWaveZombies = 0
    private var spawnTimer = 0f
    private var shootTimer = 0f
    private var bossNameSeed = listOf("MEGA SLASHER", "TOXIC GOLIATH", "OUTPOST ZEALOT", "CYBER ARMAGEDDON", "DECAY REAPER")

    // Engine loop job
    private var gameJob: Job? = null

    init {
        // Sync music/sfx configurator parameters
        com.example.audio.GameAudioSynth.masterVolume = masterVolume
        com.example.audio.GameAudioSynth.musicVolume = musicVolume
        com.example.audio.GameAudioSynth.sfxVolume = sfxVolume
        com.example.audio.GameAudioSynth.musicOn = musicOn
        com.example.audio.GameAudioSynth.soundOn = soundOn
        com.example.audio.GameAudioSynth.startMusic()

        // Observe persistent data reactively
        viewModelScope.launch {
            repository.gameStats.collect { stats ->
                hpLevel = stats.hpUpgradeLevel
                gunLevel = stats.gunUpgradeLevel
                regenLevel = stats.regenUpgradeLevel
                outpostLevel = stats.outpostUpgradeLevel
                lifetimeCoins = stats.lifetimeEarnings
                highestWaveReached = stats.highestWaveReached
                
                totalZombiesKilled = stats.totalZombiesKilled
                totalMoneyEarned = stats.totalMoneyEarned
                totalRunsPlayed = stats.totalRunsPlayed
                bossesDefeated = stats.bossesDefeated
                playTimeSeconds = stats.playTimeSeconds
                
                // Load prestige and newer subsystems
                prestigeLevel = stats.prestigeLevel
                prestigePoints = stats.prestigePoints
                lastDailyRewardTime = stats.lastDailyRewardTime
                consecutiveDailyDays = stats.consecutiveDailyDays
                completedAchievementsMask = stats.completedAchievementsMask
                isPerformanceModeOn = stats.performanceModeOn
                
                // Recalculate max hp (only depends on player hp level now)
                baseMaxHp = 100f + (hpLevel - 1) * 25f
            }
        }
        initializeTurrets()
    }

    override fun onCleared() {
        super.onCleared()
        com.example.audio.GameAudioSynth.stopMusic()
    }

    fun initializeTurrets() {
        turrets.clear()
        // 4 dedicated slots around the Outpost in a cross layout
        turrets.add(Turret(0, 400f, 480f, TurretType.NONE, 1)) // TOP
        turrets.add(Turret(1, 400f, 720f, TurretType.NONE, 1)) // BOTTOM
        turrets.add(Turret(2, 280f, 600f, TurretType.NONE, 1)) // LEFT
        turrets.add(Turret(3, 520f, 600f, TurretType.NONE, 1)) // RIGHT
    }

    // Formulas for Upgrades
    val hpCost: Long get() = (50 + (hpLevel - 1) * 35).toLong()
    val gunCost: Long get() = (100 + (gunLevel - 1) * 65).toLong()
    val regenCost: Long get() = (80 + regenLevel * 55).toLong()
    val outpostCost: Long get() = (150 + (outpostLevel - 1) * 100).toLong()

    val regenRate: Float get() {
        val baseRegen = if (regenLevel <= 0) 0f else regenLevel * 0.5f
        val prestigeRegen = prestigePoints * 0.2f
        return baseRegen + prestigeRegen
    }

    // Helper to get weapon stats based on overall gun upgrade level
    fun getGunStats(level: Int): GunStats {
        val baseStats = when {
            level < 5 -> GunStats(
                tierName = "Pistol",
                damage = 8.5f + level * 2.5f, // level 1 starts at 11f (2-3 shots on walker/runner/tank target)
                fireIntervalMs = 550L, // Slightly faster fire intervals
                projectileSpeed = 700f,
                bulletSize = 10f,
                colorHex = 0xFFFFFFFF,
                pierceCount = 1
            )
            level < 10 -> GunStats(
                tierName = "SMG",
                damage = 11f + level * 2.3f, // level 5 starts at 22.5f damage
                fireIntervalMs = 170L, // Faster fire interval for better DPS
                projectileSpeed = 800f,
                bulletSize = 9f,
                colorHex = 0xFFFF9800,
                pierceCount = 1
            )
            level < 20 -> GunStats(
                tierName = "Shotgun",
                damage = 25f + level * 4.6f, // level 10 deals 71f total damage per shot
                fireIntervalMs = 680L, // Faster burst
                projectileSpeed = 600f,
                bulletSize = 13f,
                colorHex = 0xFFF44336,
                pierceCount = 1
            )
            level < 40 -> GunStats(
                tierName = "Assault Rifle",
                damage = 35f + level * 5.4f, // level 20 deals 143f damage
                fireIntervalMs = 160L, // Satisfying assault fire cycles
                projectileSpeed = 900f,
                bulletSize = 11f,
                colorHex = 0xFFFFEB3B,
                pierceCount = 2
            )
            level < 75 -> GunStats(
                tierName = "Minigun",
                damage = 28f + level * 4.6f, // level 40 deals 212f damage
                fireIntervalMs = 65L, // Extreme speed and satisfying spray
                projectileSpeed = 1100f,
                bulletSize = 10f,
                colorHex = 0xFF00BCD4,
                pierceCount = 2
            )
            else -> GunStats(
                tierName = "Laser Gun",
                damage = 80f + level * 11.5f, // Ultimate endgame laser beam
                fireIntervalMs = 240L,
                projectileSpeed = 1550f,
                bulletSize = 20f,
                colorHex = 0xFFE91E63,
                pierceCount = 9999L.toInt()
            )
        }
        val damageMultiplier = 1f + prestigePoints * 0.10f
        return baseStats.copy(damage = baseStats.damage * damageMultiplier)
    }

    // Upgrades permanent handlers
    fun upgradeHp() {
        val cost = hpCost
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            hpLevel += 1
            baseMaxHp = 100f + (hpLevel - 1) * 25f
            // Play satisfying upgrade chime
            com.example.audio.GameAudioSynth.playUpgrade()
            // In-run boost
            if (gameState == GameState.PLAYING) {
                baseHp = min(baseMaxHp, baseHp + 25f)
            }
            savePersistentStats()
        }
    }

    fun upgradeGun() {
        val cost = gunCost
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            gunLevel += 1
            com.example.audio.GameAudioSynth.playUpgrade()
            savePersistentStats()
        }
    }

    fun upgradeRegen() {
        val cost = regenCost
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            regenLevel += 1
            com.example.audio.GameAudioSynth.playUpgrade()
            savePersistentStats()
        }
    }

    fun upgradeOutpost() {
        val cost = outpostCost
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            outpostLevel += 1
            baseMaxHp = 100f + (hpLevel - 1) * 25f + (outpostLevel - 1) * 50f
            com.example.audio.GameAudioSynth.playUpgrade()
            if (gameState == GameState.PLAYING) {
                baseHp = min(baseMaxHp, baseHp + 50f)
            }
            savePersistentStats()
        }
    }

    fun getTurretBuildCost(type: TurretType): Long {
        return when (type) {
            TurretType.GATLING -> 300L
            TurretType.PLASMA -> 500L
            TurretType.TESLA -> 750L
            else -> 0L
        }
    }

    fun getTurretUpgradeCost(turret: Turret): Long {
        return (150 + turret.level * 100).toLong()
    }

    fun deployTurret(slotId: Int, type: TurretType) {
        val cost = getTurretBuildCost(type)
        if (lifetimeCoins >= cost) {
            val turret = turrets.firstOrNull { it.slotId == slotId }
            if (turret != null) {
                lifetimeCoins -= cost
                turret.type = type
                turret.level = 1
                triggerScreenShake(4f)
                com.example.audio.GameAudioSynth.playUpgrade()
                
                // Build animation shockwave particle burst
                val colorHex = 0xFF00FFCC
                val colors = listOf(colorHex, 0xFFE040FB, 0xFFFFFFFF)
                for (i in 0..40) {
                    val angle = Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
                    val speed = Random.nextFloat() * 250f + 120f
                    particles.add(
                        Particle(
                            x = turret.x,
                            y = turret.y,
                            vx = kotlin.math.cos(angle) * speed,
                            vy = kotlin.math.sin(angle) * speed,
                            colorHex = colors.random(),
                            size = Random.nextFloat() * 9f + 4f,
                            life = 1.2f,
                            decay = Random.nextFloat() * 1.6f + 0.9f
                        )
                    )
                }
                
                savePersistentStats()
            }
        }
    }

    fun upgradeTurret(slotId: Int) {
        val turret = turrets.firstOrNull { it.slotId == slotId }
        if (turret != null && turret.type != TurretType.NONE) {
            val cost = getTurretUpgradeCost(turret)
            if (lifetimeCoins >= cost) {
                lifetimeCoins -= cost
                turret.level += 1
                triggerScreenShake(3f)
                com.example.audio.GameAudioSynth.playUpgrade()
                savePersistentStats()
            }
        }
    }

    fun dismantleTurret(slotId: Int) {
        val turret = turrets.firstOrNull { it.slotId == slotId }
        if (turret != null && turret.type != TurretType.NONE) {
            // Refund 50% value of turret build and levels
            val buildCost = getTurretBuildCost(turret.type)
            var totalSpent = buildCost
            for (lvl in 1 until turret.level) {
                totalSpent += (150 + lvl * 100)
            }
            lifetimeCoins += (totalSpent / 2)
            turret.type = TurretType.NONE
            turret.level = 1
            triggerScreenShake(2f)
            savePersistentStats()
        }
    }

    private fun savePersistentStats() {
        viewModelScope.launch {
            repository.saveStats(
                GameStats(
                    id = 1,
                    hpUpgradeLevel = hpLevel,
                    gunUpgradeLevel = gunLevel,
                    regenUpgradeLevel = regenLevel,
                    outpostUpgradeLevel = outpostLevel,
                    highestWaveReached = highestWaveReached,
                    lifetimeEarnings = lifetimeCoins,
                    totalZombiesKilled = totalZombiesKilled,
                    totalMoneyEarned = totalMoneyEarned,
                    totalRunsPlayed = totalRunsPlayed,
                    bossesDefeated = bossesDefeated,
                    playTimeSeconds = playTimeSeconds,
                    prestigeLevel = prestigeLevel,
                    prestigePoints = prestigePoints,
                    lastDailyRewardTime = lastDailyRewardTime,
                    consecutiveDailyDays = consecutiveDailyDays,
                    completedAchievementsMask = completedAchievementsMask,
                    performanceModeOn = isPerformanceModeOn
                )
            )
        }
    }

    fun claimTestCoins() {
        lifetimeCoins += 1000L
        savePersistentStats()
    }

    fun resetGameStats() {
        viewModelScope.launch {
            repository.saveStats(
                GameStats(
                    id = 1,
                    hpUpgradeLevel = 1,
                    gunUpgradeLevel = 1,
                    regenUpgradeLevel = 0,
                    outpostUpgradeLevel = 1,
                    highestWaveReached = 1,
                    lifetimeEarnings = 0L,
                    totalZombiesKilled = 0,
                    totalMoneyEarned = 0L,
                    totalRunsPlayed = 0,
                    bossesDefeated = 0,
                    playTimeSeconds = 0L,
                    prestigeLevel = 0,
                    prestigePoints = 0,
                    lastDailyRewardTime = 0L,
                    consecutiveDailyDays = 0,
                    completedAchievementsMask = 0,
                    performanceModeOn = false
                )
            )
            hpLevel = 1
            gunLevel = 1
            regenLevel = 0
            outpostLevel = 1
            highestWaveReached = 1
            lifetimeCoins = 0L
            totalZombiesKilled = 0
            totalMoneyEarned = 0L
            totalRunsPlayed = 0
            bossesDefeated = 0
            playTimeSeconds = 0L
            prestigeLevel = 0
            prestigePoints = 0
            lastDailyRewardTime = 0L
            consecutiveDailyDays = 0
            completedAchievementsMask = 0
            isPerformanceModeOn = false
            baseMaxHp = 100f
            baseHp = 100f
            canContinueRun = false
            initializeTurrets()
        }
    }

    fun isAchievementCompleted(id: Int): Boolean {
        return (completedAchievementsMask and (1 shl id)) != 0
    }

    fun claimAchievement(id: Int) {
        val achievement = AchievementsList.firstOrNull { it.id == id } ?: return
        if (achievement.isFulfilled(this) && !isAchievementCompleted(id)) {
            completedAchievementsMask = completedAchievementsMask or (1 shl id)
            lifetimeCoins += achievement.rewardCoins
            com.example.audio.GameAudioSynth.playUpgrade()
            savePersistentStats()
        }
    }

    val isDailyRewardClaimable: Boolean get() = (System.currentTimeMillis() - lastDailyRewardTime) >= 24 * 60 * 60 * 1000L

    fun claimDailyReward() {
        if (!isDailyRewardClaimable) return
        
        val now = System.currentTimeMillis()
        val delta = now - lastDailyRewardTime
        
        if (delta > 48 * 60 * 60 * 1000L && lastDailyRewardTime != 0L) {
            consecutiveDailyDays = 1
        } else {
            consecutiveDailyDays = (consecutiveDailyDays % 5) + 1
        }
        
        lastDailyRewardTime = now
        
        val (coins, points) = when (consecutiveDailyDays) {
            1 -> Pair(250L, 0)
            2 -> Pair(500L, 0)
            3 -> Pair(1000L, 0)
            4 -> Pair(1500L, 0)
            5 -> Pair(2500L, 1)
            else -> Pair(250L, 0)
        }
        
        lifetimeCoins += coins
        prestigePoints += points
        com.example.audio.GameAudioSynth.playUpgrade()
        
        savePersistentStats()
    }

    fun testResetDailyRewardCooldown() {
        lastDailyRewardTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L - 1000L
        savePersistentStats()
    }

    val prestigePointsEarnedOnReset: Int get() {
        if (currentWave < 10) return 0
        return (currentWave - 5) / 5
    }

    val canPrestige: Boolean get() = currentWave >= 10

    fun performPrestige() {
        if (!canPrestige) return
        
        val earnedPoints = prestigePointsEarnedOnReset
        prestigePoints += earnedPoints
        prestigeLevel += 1
        
        currentWave = 1
        runEarnings = 0L
        lifetimeCoins = 0L
        
        hpLevel = 1
        gunLevel = 1
        regenLevel = 0
        outpostLevel = 1
        
        baseMaxHp = 100f + (hpLevel - 1) * 25f + (outpostLevel - 1) * 50f
        baseHp = baseMaxHp
        
        zombies.clear()
        bullets.clear()
        particles.clear()
        damageNumbers.clear()
        coins.clear()
        decals.clear()
        
        initializeTurrets()
        
        gameState = GameState.MENU
        canContinueRun = false
        
        com.example.audio.GameAudioSynth.playUpgrade()
        savePersistentStats()
    }

    fun generateChestRewards() {
        chestOptions.clear()
        val types = ChestRewardType.values()
        
        for (i in 1..3) {
            val type = types.random()
            val (title, description, amount) = when (type) {
                ChestRewardType.MONEY -> {
                    val baseReward = 300L + currentWave * 120L
                    val prestigeBonusFactor = 1f + prestigePoints * 0.10f
                    val finalReward = (baseReward * prestigeBonusFactor).toLong()
                    Triple("Supply Crate", "+$finalReward Credits", finalReward)
                }
                ChestRewardType.FREE_UPGRADE -> {
                    val isGun = Random.nextBoolean()
                    val titleStr = if (isGun) "Hyper Barrel" else "Titan Plate"
                    val descStr = if (isGun) "FREE Gun Upgrade (+1 level)" else "FREE Max HP Upgrade (+1 level)"
                    Triple(titleStr, descStr, if (isGun) 1L else 0L)
                }
                ChestRewardType.TURRET_UPGRADE -> {
                    Triple("Turret Upgrade", "Upgrades a random active turret +1 level or research credits!", 0L)
                }
                ChestRewardType.REGEN_BOOST -> {
                    Triple("Repair Nanites", "FREE Regen Upgrade (+1 level)", 0L)
                }
            }
            
            chestOptions.add(
                ChestOption(
                    id = i,
                    type = type,
                    title = title,
                    description = description,
                    amount = amount,
                    isOpened = false
                )
            )
        }
    }

    fun claimChestReward(optionId: Int) {
        val option = chestOptions.firstOrNull { it.id == optionId } ?: return
        if (option.isOpened) return
        
        option.isOpened = true
        
        when (option.type) {
            ChestRewardType.MONEY -> {
                lifetimeCoins += option.amount
                runEarnings += option.amount
                totalMoneyEarned += option.amount
            }
            ChestRewardType.FREE_UPGRADE -> {
                if (option.amount == 1L) {
                    gunLevel += 1
                } else {
                    hpLevel += 1
                    baseMaxHp = 100f + (hpLevel - 1) * 25f + (outpostLevel - 1) * 50f
                    baseHp = min(baseMaxHp, baseHp + 25f)
                }
            }
            ChestRewardType.TURRET_UPGRADE -> {
                val builtTurrets = turrets.filter { it.type != TurretType.NONE }
                if (builtTurrets.isNotEmpty()) {
                    val randomTurret = builtTurrets.random()
                    randomTurret.level += 1
                } else {
                    val bonus = 500L + currentWave * 100L
                    lifetimeCoins += bonus
                    runEarnings += bonus
                    totalMoneyEarned += bonus
                }
            }
            ChestRewardType.REGEN_BOOST -> {
                regenLevel += 1
            }
        }
        
        com.example.audio.GameAudioSynth.playUpgrade()
        savePersistentStats()
    }

    fun togglePerformanceMode(enabled: Boolean) {
        isPerformanceModeOn = enabled
        savePersistentStats()
    }

    // Start Run
    fun startGame() {
        totalRunsPlayed += 1
        canContinueRun = false

        // Run Reset System: Reset All Run UPGRADES
        hpLevel = 1
        gunLevel = 1
        regenLevel = 0
        outpostLevel = 1

        baseMaxHp = 100f + (hpLevel - 1) * 25f + (outpostLevel - 1) * 50f
        baseHp = baseMaxHp
        currentWave = 1
        runEarnings = 0L
        playerPos = Vector2(400f, 680f)
        
        zombies.clear()
        bullets.clear()
        particles.clear()
        damageNumbers.clear()
        coins.clear()
        decals.clear()

        // Clear all turrets and reset their levels to 1
        initializeTurrets()

        isBetweenWaves = false
        isAutoSkipEnabled = false
        betweenWaveTimer = 0f
        
        prepareWave(currentWave)
        gameState = GameState.PLAYING
        
        // Save the clean starting states right away
        savePersistentStats()
        
        startGameLoop()
    }

    // Manual start next wave helper when Auto Skip is OFF
    fun startNextWave() {
        if (isBetweenWaves) {
            isBetweenWaves = false
            prepareWave(currentWave)
        }
    }

    fun continueGame() {
        if (!canContinueRun) return
        baseMaxHp = 100f + (hpLevel - 1) * 25f + (outpostLevel - 1) * 50f
        baseHp = savedBaseHp
        currentWave = savedWave
        runEarnings = savedRunEarnings
        playerPos = Vector2(400f, 680f)

        zombies.clear()
        bullets.clear()
        particles.clear()
        damageNumbers.clear()
        coins.clear()
        decals.clear()

        // Restore turrets from backup
        turrets.clear()
        if (savedTurrets.isEmpty()) {
            initializeTurrets()
        } else {
            savedTurrets.forEach { saved ->
                val (x, y) = when (saved.slotId) {
                    0 -> Pair(400f, 480f) // TOP
                    1 -> Pair(400f, 720f) // BOTTOM
                    2 -> Pair(280f, 600f) // LEFT
                    3 -> Pair(520f, 600f) // RIGHT
                    else -> Pair(400f, 480f)
                }
                turrets.add(Turret(saved.slotId, x, y, saved.type, saved.level))
            }
            if (turrets.isEmpty()) {
                initializeTurrets()
            }
        }

        isBetweenWaves = false
        isAutoSkipEnabled = false
        betweenWaveTimer = 0f

        prepareWave(currentWave)
        gameState = GameState.PLAYING

        startGameLoop()
    }

    private fun prepareWave(wave: Int) {
        waveZombiesSpawned = 0
        
        // Every wave: Zombie Count +2% (base 15 zombies, plus wave linear boost)
        val baseCount = 15.0
        val pWave = wave - 1
        val normalCount = (baseCount * 1.02.pow(pWave.toDouble())).toInt() + wave
        
        val count = when {
            wave % 25 == 0 -> {
                // Every 25th wave is a major challenge wave (increased count & epic challenge)
                (normalCount * 2.2f).toInt()
            }
            wave % 10 == 0 -> {
                // Every 10th wave is a mini difficulty spike (increased count & mini spike)
                (normalCount * 1.6f).toInt()
            }
            wave % 5 == 0 -> {
                // Every 5 waves is a horde wave (1.3x normal)
                (normalCount * 1.3f).toInt()
            }
            else -> normalCount
        }
        
        totalWaveZombies = maxOf(5, count)
        spawnTimer = 0f
        shootTimer = 0f
    }

    private fun triggerScreenShake(magnitude: Float) {
        shakeMagnitude = max(shakeMagnitude, magnitude)
    }

    private fun startGameLoop() {
        gameJob?.cancel()
        gameJob = viewModelScope.launch(Dispatchers.Main) {
            var lastTime = System.currentTimeMillis()
            while (isActive && gameState == GameState.PLAYING) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastTime) / 1000f
                lastTime = currentTime

                // Safe guard against extreme spikes
                val clampedDelta = min(deltaTime, 0.1f)

                updateGame(clampedDelta)
                delay(16) // Target ~60 FPS
            }
        }
    }

    fun handleJoystickInput(dx: Float, dy: Float, deltaTime: Float) {
        if (gameState != GameState.PLAYING) return
        
        val playerSpeed = 300f // pixels per second
        var px = playerPos.x + dx * playerSpeed * deltaTime
        var py = playerPos.y + dy * playerSpeed * deltaTime

        // Border constraints
        px = px.coerceIn(30f, 770f)
        py = py.coerceIn(30f, 1170f)

        playerPos = Vector2(px, py)
    }

    private fun updateGame(deltaTime: Float) {
        playTimeAccumulator += deltaTime
        if (playTimeAccumulator >= 1.0f) {
            val secs = playTimeAccumulator.toInt()
            playTimeSeconds += secs
            playTimeAccumulator -= secs
        }

        if (isBetweenWaves) {
            if (isAutoSkipEnabled && !showChestSelection) {
                betweenWaveTimer -= deltaTime
                if (betweenWaveTimer <= 0f) {
                    isBetweenWaves = false
                    prepareWave(currentWave)
                }
            }
        }

        // 1. Process base regeneration
        if (baseHp > 0) {
            baseHp = min(baseMaxHp, baseHp + regenRate * deltaTime)
        }

        // 2. Manage Screen Shake
        if (shakeMagnitude > 0.1f) {
            shakeX = (Random.nextFloat() * 2f - 1f) * shakeMagnitude
            shakeY = (Random.nextFloat() * 2f - 1f) * shakeMagnitude
            shakeMagnitude *= exp(-5f * deltaTime) // decay rate
        } else {
            shakeX = 0f
            shakeY = 0f
            shakeMagnitude = 0f
        }

        val baseCentroid = Vector2(400f, 600f)

        // 3. Spawning System
        if (!isBetweenWaves && waveZombiesSpawned < totalWaveZombies) {
            spawnTimer += deltaTime
            // Spawning speed increased by 300% (shorter delay between groups)
            val spawnInterval = max(0.3f, 0.8f - (currentWave * 0.02f))
            if (spawnTimer >= spawnInterval && zombies.size < 70) { // Limit active zombies on screen (50-80 range)
                spawnZombieGroup()
                spawnTimer = 0f
            }
        }

        // 4. Update Game Entities
        // A. Zombies
        val iterator = zombies.iterator()
        while (iterator.hasNext()) {
            val zombie = iterator.next()
            
            // Handle hit timer decrement
            if (zombie.hitTimer > 0f) {
                zombie.hitTimer = max(0f, zombie.hitTimer - deltaTime)
            }

            val zombiePos = Vector2(zombie.x, zombie.y)
            val distToPlayer = zombiePos.distanceTo(playerPos)

            if (distToPlayer <= (18f + zombie.size)) { // Touched player
                if (zombie.type == ZombieType.BOMBER) {
                    if (!zombie.isExploding) {
                        zombie.isExploding = true
                        zombie.explosionTimer = 0f
                    } else {
                        zombie.explosionTimer += deltaTime
                        if (zombie.explosionTimer >= 0.4f) { // Explodes!
                            triggerScreenShake(18f)
                            baseHp = max(0f, baseHp - zombie.damage)
                            
                            // Explosion particle effects
                            createExplosionBurst(zombie.x, zombie.y)

                            // Chain reaction: damage nearby zombies
                            val bomberX = zombie.x
                            val bomberY = zombie.y
                            zombies.forEach { other ->
                                if (other.id != zombie.id) {
                                    val dist = Vector2(other.x, other.y).distanceTo(Vector2(bomberX, bomberY))
                                    if (dist <= 120f) {
                                        other.hp -= zombie.damage * 1.5f // highly rewarding chain reactions
                                        other.hitTimer = 0.18f
                                    }
                                }
                            }
                            
                            iterator.remove()
                            checkWaveCompletion()
                        }
                    }
                } else {
                    // Constant player biting damage over time
                    baseHp = max(0f, baseHp - zombie.damage * deltaTime)
                    triggerScreenShake(1.5f) // small persistent buzz on player attack

                    // Play tiny hit sparkles around player occasionally
                    if (Random.nextFloat() < 0.1f) {
                        spawnBloodSplatter(zombie.x, zombie.y, 0xFFE91E63, 2)
                    }
                }
            } else {
                // Move zombie towards player
                val direction = (playerPos - zombiePos).normalized()
                zombie.x += direction.x * zombie.speed * 40f * deltaTime
                zombie.y += direction.y * zombie.speed * 40f * deltaTime
            }

            // Secondary check if killed by explosive chain reactions
            if (zombie.hp <= 0 && zombie.type != ZombieType.BOMBER) {
                defeatZombie(zombie)
                iterator.remove()
                checkWaveCompletion()
            }
        }

        // Trigger defeat?
        if (baseHp <= 0f) {
            endRun()
            return
        }

        // B. Bullets auto shooter
        // Find closest zombie
        val targetZombie = zombies.minByOrNull { Vector2(it.x, it.y).distanceTo(Vector2(playerPos.x, playerPos.y)) }
        val weapon = getGunStats(gunLevel)
        
        if (targetZombie != null) {
            val targetDir = (Vector2(targetZombie.x, targetZombie.y) - playerPos).normalized()
            playerAngle = atan2(targetDir.y, targetDir.x) * 180f / PI.toFloat()

            shootTimer += deltaTime * 1000f
            if (shootTimer >= weapon.fireIntervalMs) {
                fireWeapon(targetDir, weapon)
                shootTimer = 0f
            }
        }

        // B2. Base defense turrets auto-firing solver
        updateTurrets(deltaTime)

        // Update active bullets
        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            bullet.x += bullet.vx * deltaTime
            bullet.y += bullet.vy * deltaTime

            // Check boundaries
            if (bullet.x < -50f || bullet.x > 850f || bullet.y < -50f || bullet.y > 1250f) {
                bulletIterator.remove()
                continue
            }

            // Check collision with zombies
            var hitOccurred = false
            for (zombie in zombies) {
                val zPos = Vector2(zombie.x, zombie.y)
                val bPos = Vector2(bullet.x, bullet.y)
                if (zPos.distanceTo(bPos) <= (zombie.size + bullet.size)) {
                    // Inflict damage
                    zombie.hp -= bullet.damage
                    zombie.hitTimer = 0.18f
                    hitOccurred = true

                    // Play subtle hit sound with random chance to prevent noise pollution
                    if (Random.nextFloat() < 0.25f) {
                        com.example.audio.GameAudioSynth.playZombieHit()
                    }

                    // Floating damage indicator
                    val colorNum = if (bullet.isTurretBullet) 0xFF00FFCC else (if (bullet.piercesRemaining == weapon.pierceCount) weapon.colorHex else 0xFFFFFFFF)
                    damageNumbers.add(
                        DamageNumber(
                            x = zombie.x + (Random.nextFloat() * 20f - 10f),
                            y = zombie.y - 15f,
                            text = bullet.damage.toInt().toString(),
                            colorHex = colorNum,
                            life = 1.0f
                        )
                    )

                    // Impact Sparks and sparks animations
                    spawnBloodSplatter(bullet.x, bullet.y, bullet.colorHex, 5)
                    spawnImpactSparks(bullet.x, bullet.y)

                    // Trigger splash damage if this bullet has splash configured
                    if (bullet.isSplash && bullet.splashRadius > 0f) {
                        createSplashExplosionParticles(bullet.x, bullet.y, bullet.splashRadius, bullet.colorHex)
                        
                        val splashDmg = bullet.damage * bullet.splashDamagePercent
                        zombies.forEach { other ->
                            if (other.id != zombie.id) {
                                val dist = Vector2(other.x, other.y).distanceTo(Vector2(bullet.x, bullet.y))
                                if (dist <= bullet.splashRadius) {
                                    other.hp -= splashDmg
                                    other.hitTimer = 0.18f
                                    
                                    damageNumbers.add(
                                        DamageNumber(
                                            x = other.x + (Random.nextFloat() * 16f - 8f),
                                            y = other.y - 10f,
                                            text = splashDmg.toInt().toString(),
                                            colorHex = 0xFF00FFCC,
                                            life = 0.8f
                                        )
                                    )
                                    
                                    if (other.hp <= 0) {
                                        defeatZombie(other)
                                    }
                                }
                            }
                        }
                    }

                    // If zombie died, register rewards
                    if (zombie.hp <= 0) {
                        defeatZombie(zombie)
                    }

                    bullet.piercesRemaining -= 1
                    if (bullet.piercesRemaining <= 0) break
                }
            }

            // Clean dead zombies
            val deadZombies = zombies.filter { it.hp <= 0 }
            if (deadZombies.isNotEmpty()) {
                zombies.removeAll(deadZombies)
                checkWaveCompletion()
            }

            if (hitOccurred && bullet.piercesRemaining <= 0) {
                bulletIterator.remove()
            }
        }

        // C. Clean up exploded/removed elements and wave state progress
        // D. Particles system update
        val partIter = particles.iterator()
        while (partIter.hasNext()) {
            val part = partIter.next()
            part.x += part.vx * deltaTime
            part.y += part.vy * deltaTime
            part.life -= part.decay * deltaTime
            if (part.life <= 0f) {
                partIter.remove()
            }
        }

        // E. Floating damage numbers update
        val numIter = damageNumbers.iterator()
        while (numIter.hasNext()) {
            val num = numIter.next()
            num.y -= 45f * deltaTime
            num.life -= 1.5f * deltaTime
            if (num.life <= 0f) {
                numIter.remove()
            }
        }

        // F. Golden Coins update (gliding towards balance icon at top right)
        // Let's set top-right balance slot roughly around coordinate x=750f, y=40f
        val targetCoinSlot = Vector2(760f, 40f)
        val coinIter = coins.iterator()
        while (coinIter.hasNext()) {
            val coin = coinIter.next()
            coin.progress += 2.0f * deltaTime // speed of glide
            if (coin.progress >= 1.0f) {
                // Play satisfying synthesized coin pickup sound
                com.example.audio.GameAudioSynth.playCoin()
                // runEarnings was already added instantly to lifetimeCoins and runEarnings on zombie death.
                // little coin collection spark
                spawnBloodSplatter(targetCoinSlot.x, targetCoinSlot.y, 0xFFFFD700, 3)
                coinIter.remove()
            } else {
                // ease-in exponential interpolation
                val t = coin.progress
                val curve = t * t
                coin.x = coin.startX + (targetCoinSlot.x - coin.startX) * curve
                coin.y = coin.startY + (targetCoinSlot.y - coin.startY) * curve
            }
        }
    }

    private fun checkWaveCompletion() {
        if (waveZombiesSpawned >= totalWaveZombies && zombies.isEmpty()) {
            // Trigger Wave Transition
            isBetweenWaves = true
            betweenWaveTimer = 1.8f // 1.8s duration for quick, non-blocking notification
            
            // Grant Wave Completion Bonus Reward! (Wave 1 = +$100, Wave 2 = +$150, Wave 3 = +$200...)
            val waveCompleted = currentWave
            val waveBonus = 50L + (waveCompleted * 50L)
            
            // Every 10th wave awards a massive Horde Bonus (+1,000 for Wave 10, +2,000 for Wave 20...)
            val isHordeWave = (waveCompleted % 10 == 0)
            val hordeBonus = if (isHordeWave) (waveCompleted / 10L) * 1000L else 0L
            
            val totalGranted = waveBonus + hordeBonus
            lifetimeCoins += totalGranted
            runEarnings += totalGranted
            totalMoneyEarned += totalGranted
            
            // Pop visually satisfying text on the play field
            damageNumbers.add(
                DamageNumber(
                    x = 400f,
                    y = 550f,
                    text = "WAVE $waveCompleted BONUS +$$waveBonus",
                    colorHex = 0xFF81C784, // Glowing lime green
                    life = 1.8f
                )
            )
            
            if (isHordeWave) {
                damageNumbers.add(
                    DamageNumber(
                        x = 400f,
                        y = 510f,
                        text = "HORDE BONUS +$$hordeBonus!! ⚔️",
                        colorHex = 0xFFFFD700, // Shiny gold
                        life = 2.0f
                    )
                )
                generateChestRewards()
                showChestSelection = true
            }
            
            // Advance wave
            currentWave += 1
            if (currentWave > highestWaveReached) {
                highestWaveReached = currentWave
            }
            savePersistentStats()
        }
    }

    private fun defeatZombie(zombie: Zombie) {
        totalZombiesKilled += 1
        if (zombie.type == ZombieType.BOSS) {
            bossesDefeated += 1
        }

        // Play zombie decay/death audio
        if (zombie.type == ZombieType.BOSS || Random.nextFloat() < 0.35f) {
            com.example.audio.GameAudioSynth.playZombieDeath()
        }

        val prestigeBonusFactor = 1f + prestigePoints * 0.10f
        val coinValue = (zombie.reward * prestigeBonusFactor).toInt()
        // Immediately add money to the player's wallet and statistics
        lifetimeCoins += coinValue
        runEarnings += coinValue
        totalMoneyEarned += coinValue
        savePersistentStats()

        // Sparks (Golden gold sparks if golden)
        val particleColor = if (zombie.isGolden) 0xFFFFD700 else zombie.colorHex
        val particleCount = if (zombie.isGolden) 25 else 12
        spawnBloodSplatter(zombie.x, zombie.y, particleColor, particleCount)
        triggerScreenShake(if (zombie.type == ZombieType.BOSS) 14f else if (zombie.isGolden) 9f else 4f)

        // Floating reward indicator
        if (!isPerformanceModeOn) {
            damageNumbers.add(
                DamageNumber(
                    x = zombie.x,
                    y = zombie.y - 30f,
                    text = if (zombie.isGolden) "GOLDEN REWARD! +$coinValue ✨" else "+$coinValue",
                    colorHex = 0xFFFFD700, // Gold text
                    life = if (zombie.isGolden) 1.6f else 1.0f
                )
            )
        }

        // Spawn beautiful flying coins
        coins.add(
            Coin(
                x = zombie.x,
                y = zombie.y,
                startX = zombie.x,
                startY = zombie.y,
                progress = 0f,
                amount = coinValue
            )
        )

        // Spawn permanent ground splatter & pool decal
        spawnDecal(zombie.x, zombie.y, zombie.size * 1.5f, zombie.colorHex)
    }

    private fun spawnDecal(x: Float, y: Float, baseSize: Float, colorHex: Long) {
        val size = Random.nextFloat() * (baseSize * 0.4f) + (baseSize * 0.8f)
        val angle = Random.nextFloat() * 360f
        val type = Random.nextInt(3) // 0: splat, 1: blood pool, 2: radioactive trail
        decals.add(Decal(x, y, size, colorHex, angle, type))
        if (decals.size > 80) {
            decals.removeAt(0)
        }
    }

    private fun fireWeapon(direction: Vector2, weapon: GunStats) {
        val lateral = Vector2(-direction.y, direction.x) // orthogonal vector for spray spread offsets
        
        when (weapon.tierName) {
            "Shotgun" -> {
                com.example.audio.GameAudioSynth.playShotgun()
                // Fire 3-way spread bullet
                for (i in -1..1) {
                    val angleOffset = i * 0.26f // roughly 15 degrees
                    val rotatedDir = Vector2(
                        direction.x * cos(angleOffset) - direction.y * sin(angleOffset),
                        direction.x * sin(angleOffset) + direction.y * cos(angleOffset)
                    ).normalized()
                    
                    bullets.add(
                        Bullet(
                            x = playerPos.x,
                            y = playerPos.y,
                            vx = rotatedDir.x * weapon.projectileSpeed,
                            vy = rotatedDir.y * weapon.projectileSpeed,
                            damage = weapon.damage,
                            speed = weapon.projectileSpeed,
                            size = weapon.bulletSize,
                            colorHex = weapon.colorHex,
                            piercesRemaining = weapon.pierceCount,
                            originalPierceCount = weapon.pierceCount
                        )
                    )
                }
                triggerScreenShake(5f)
            }
            "Minigun" -> {
                com.example.audio.GameAudioSynth.playMinigun()
                // slight jitter spread
                val jitter = (Random.nextFloat() * 2f - 1f) * 0.12f
                val jitterDir = (direction + lateral * jitter).normalized()
                bullets.add(
                    Bullet(
                        x = playerPos.x,
                        y = playerPos.y,
                        vx = jitterDir.x * weapon.projectileSpeed,
                        vy = jitterDir.y * weapon.projectileSpeed,
                        damage = weapon.damage,
                        speed = weapon.projectileSpeed,
                        size = weapon.bulletSize,
                        colorHex = weapon.colorHex,
                        piercesRemaining = weapon.pierceCount,
                        originalPierceCount = weapon.pierceCount
                    )
                )
                triggerScreenShake(2f)
            }
            else -> {
                // Play SFX suited for Pistol, SMG, Assault Rifle or Laser Gun
                when (weapon.tierName) {
                    "Pistol" -> com.example.audio.GameAudioSynth.playPistol()
                    "SMG" -> com.example.audio.GameAudioSynth.playSMG()
                    "Assault Rifle" -> com.example.audio.GameAudioSynth.playSMG() // fast crisp burst
                    "Laser Gun", "Laser" -> com.example.audio.GameAudioSynth.playLaser()
                    else -> com.example.audio.GameAudioSynth.playPistol()
                }

                // Standard straight shooter (Pistol, SMG, Assault Rifle, Laser Gun)
                bullets.add(
                    Bullet(
                        x = playerPos.x,
                        y = playerPos.y,
                        vx = direction.x * weapon.projectileSpeed,
                        vy = direction.y * weapon.projectileSpeed,
                        damage = weapon.damage,
                        speed = weapon.projectileSpeed,
                        size = weapon.bulletSize,
                        colorHex = weapon.colorHex,
                        piercesRemaining = weapon.pierceCount,
                        originalPierceCount = weapon.pierceCount
                    )
                )
                triggerScreenShake(if (weapon.tierName == "Laser Gun") 9f else 3.5f)
            }
        }

        // Spawn fire tiny particles at hand
        val muzzlePos = playerPos + direction * 25f
        spawnBloodSplatter(muzzlePos.x, muzzlePos.y, weapon.colorHex, 3)
    }

    private fun spawnZombieGroup() {
        val sides = listOf(0, 1, 2, 3) // Top, Bottom, Left, Right
        val isSpikeWave = (currentWave % 10 == 0)
        val isMajorWave = (currentWave % 25 == 0)
        
        // Group spawning: 2-3 standard, larger on spikes/major waves
        val minGroup = if (isMajorWave) 3 else if (isSpikeWave) 2 else 2
        val maxGroup = if (isMajorWave) 5 else if (isSpikeWave) 4 else 3
        
        for (side in sides) {
            val countToSpawnOnThisSide = Random.nextInt(minGroup, maxGroup + 1)
            for (i in 0 until countToSpawnOnThisSide) {
                if (waveZombiesSpawned >= totalWaveZombies) return
                if (zombies.size >= 85) return // Active screen limit to preserve performance (50-80 range)
                
                spawnSingleZombieAtSide(side)
            }
        }
    }

    private fun spawnSingleZombieAtSide(side: Int) {
        val isBossWave = (currentWave % 10 == 0) || (currentWave % 25 == 0)
        val bossAlreadyExist = zombies.any { it.type == ZombieType.BOSS }
        val type = selectZombieType(currentWave, isBossWave, bossAlreadyExist)

        // Spawn around outer border box boundaries
        // Logical bounds: [0, 800] x [0, 1200]
        var sx = 400f
        var sy = 0f
        val offset = 60f

        when (side) {
            0 -> { // Top
                sx = Random.nextFloat() * 800f
                sy = -offset
            }
            1 -> { // Bottom
                sx = Random.nextFloat() * 800f
                sy = 1200f + offset
            }
            2 -> { // Left
                sx = -offset
                sy = Random.nextFloat() * 1200f
            }
            3 -> { // Right
                sx = 800f + offset
                sy = Random.nextFloat() * 1200f
            }
        }

        // Calculate Wave Procedural Scaling
        // Every wave: HP +1.5%, Damage +1%, Speed +0.3%
        val pWave = currentWave - 1
        val hpMultiplier = 1.015f.pow(pWave.toFloat())
        val damageMultiplier = 1.01f.pow(pWave.toFloat())
        val speedMultiplier = 1.003f.pow(pWave.toFloat())

        // Every 10 waves is a mini difficulty spike (stronger zombies)
        val isSpikeWave = (currentWave % 10 == 0)
        val spikeHpBonus = if (isSpikeWave) 1.25f else 1.0f
        val spikeDmgBonus = if (isSpikeWave) 1.20f else 1.0f
        val spikeSpeedBonus = if (isSpikeWave) 1.08f else 1.0f

        // Every 25 waves is an epic major challenge wave (super-status, increased rewards)
        val isMajorWave = (currentWave % 25 == 0)
        val majorHpBonus = if (isMajorWave) 1.50f else 1.0f
        val majorDmgBonus = if (isMajorWave) 1.35f else 1.0f
        val majorSpeedBonus = if (isMajorWave) 1.15f else 1.0f

        val waveScaleFactor = hpMultiplier * spikeHpBonus * majorHpBonus
        val damageScaleFactor = damageMultiplier * spikeDmgBonus * majorDmgBonus
        
        // Base movement speed increased by 20%
        val speedScaleFactor = speedMultiplier * spikeSpeedBonus * majorSpeedBonus * 1.20f
        
        // Increased rewards on spike (1.3x) and major challenge waves (2.2x)
        val rewardMultiplier = if (isMajorWave) 2.20f else if (isSpikeWave) 1.30f else 1.0f
        val rewardScaleFactor = (1.0f + pWave.toFloat() * 0.05f) * rewardMultiplier

        val zombie = when (type) {
            ZombieType.WALKER -> Zombie(
                type = ZombieType.WALKER,
                x = sx,
                y = sy,
                maxHp = 28f * waveScaleFactor, // Target: 3-4 pistol starting shots (damage 8) -> 3.5 shots
                hp = 28f * waveScaleFactor,
                speed = 2.0088f * speedScaleFactor,
                damage = 6f * damageScaleFactor,
                size = 18f,
                reward = (25f * rewardScaleFactor).toInt(), // Normal Zombie reward increased from $10 to $25
                colorHex = 0xFF4CAF50
            )
            ZombieType.RUNNER -> Zombie(
                type = ZombieType.RUNNER,
                x = sx,
                y = sy,
                maxHp = 40f * waveScaleFactor, // Target: 4-6 pistol starting shots (damage 8) -> 5 shots
                hp = 40f * waveScaleFactor,
                speed = 3.8543f * speedScaleFactor,
                damage = 3.6f * damageScaleFactor,
                size = 15f,
                reward = (35f * rewardScaleFactor).toInt(), // Runner Zombie reward increased from $15 to $35
                colorHex = 0xFFFF5722
            )
            ZombieType.TANK -> Zombie(
                type = ZombieType.TANK,
                x = sx,
                y = sy,
                maxHp = 120f * waveScaleFactor, // Target: 12-20 pistol starting shots (damage 8) -> 15 shots
                hp = 120f * waveScaleFactor,
                speed = 1.1426f * speedScaleFactor,
                damage = 18f * damageScaleFactor,
                size = 28f,
                reward = (100f * rewardScaleFactor).toInt(), // Tank Zombie reward increased from $50 to $100
                colorHex = 0xFF9C27B0
            )
            ZombieType.BOMBER -> Zombie(
                type = ZombieType.BOMBER,
                x = sx,
                y = sy,
                maxHp = 26f * waveScaleFactor,
                hp = 26f * waveScaleFactor,
                speed = 3.31f * speedScaleFactor,
                damage = 30f * damageScaleFactor,
                size = 17f,
                reward = (50f * rewardScaleFactor).toInt(), // Bomber reward upgraded proportionally from 18 to 50
                colorHex = 0xFFFFEB3B
            )
            ZombieType.BOSS -> {
                val bossCount = (currentWave / 10f).toInt()
                val baseBossHp = if (isMajorWave) (600f + bossCount * 450f) else (360f + bossCount * 220f)
                val bossMaxHp = baseBossHp * waveScaleFactor
                val bossTitle = if (isMajorWave) {
                    listOf("DOOMSDAY BEHEMOTH", "OBLIVION TITAN", "VOID REAPER").random()
                } else {
                    bossNameSeed.random()
                }
                Zombie(
                    type = ZombieType.BOSS,
                    x = sx,
                    y = sy,
                    maxHp = bossMaxHp,
                    hp = bossMaxHp,
                    speed = (if (isMajorWave) 1.70f else 1.51f) * speedScaleFactor,
                    damage = (if (isMajorWave) 64f else 48f) * damageScaleFactor,
                    size = if (isMajorWave) 48f else 42f,
                    reward = (((if (isMajorWave) 1000f else 500f) + bossCount * 150f) * rewardScaleFactor).toInt(), // Boss reward increased from $200 to $500 base (+ scaled)
                    colorHex = if (isMajorWave) 0xFFFF1744 else 0xFF00E5FF, // Vivid scarlet for doomsday overlord boss, cyan for normal boss
                    bossName = "$bossTitle (WAVE $currentWave)"
                )
            }
        }

        if (zombie.type != ZombieType.BOSS && Random.nextFloat() < 0.05f) {
            zombie.isGolden = true
            zombie.maxHp *= 2f
            zombie.hp = zombie.maxHp
            zombie.reward *= 10
            zombie.colorHex = 0xFFFFD700
        }

        zombies.add(zombie)
        waveZombiesSpawned += 1

        // Play soft zombie growl on spawn (low probability or guaranteed for bosses)
        if (zombie.type == ZombieType.BOSS || Random.nextFloat() < 0.12f) {
            com.example.audio.GameAudioSynth.playZombieGrowl()
        }
    }

    private fun selectZombieType(wave: Int, isBossWave: Boolean, bossAlreadySpawned: Boolean): ZombieType {
        if (isBossWave && !bossAlreadySpawned) {
            return ZombieType.BOSS
        }

        val r = Random.nextFloat()
        val isSpikeWave = (wave % 10 == 0)

        if (isSpikeWave) {
            return when {
                r < 0.30f -> ZombieType.RUNNER
                r < 0.60f -> ZombieType.TANK
                r < 0.85f -> ZombieType.BOMBER
                else -> ZombieType.WALKER
            }
        }

        return when {
            wave < 3 -> ZombieType.WALKER
            wave < 5 -> {
                if (r < 0.45f) ZombieType.RUNNER else ZombieType.WALKER
            }
            wave < 10 -> {
                when {
                    r < 0.20f -> ZombieType.TANK
                    r < 0.55f -> ZombieType.RUNNER
                    else -> ZombieType.WALKER
                }
            }
            else -> {
                when {
                    r < 0.25f -> ZombieType.RUNNER
                    r < 0.50f -> ZombieType.TANK
                    r < 0.70f -> ZombieType.BOMBER
                    else -> ZombieType.WALKER
                }
            }
        }
    }

    private fun spawnBloodSplatter(x: Float, y: Float, color: Long, count: Int) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * 150f + 50f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    colorHex = color,
                    size = Random.nextFloat() * 5f + 3f,
                    life = 1.0f,
                    decay = Random.nextFloat() * 1.5f + 1.2f
                )
            )
        }
    }

    private fun spawnImpactSparks(x: Float, y: Float) {
        val colors = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFFF9800) // White, Yellow, Orange spark-fire
        for (i in 0 until 5) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * 180f + 100f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    colorHex = colors.random(),
                    size = Random.nextFloat() * 3f + 1.5f,
                    life = 1.0f,
                    decay = Random.nextFloat() * 3.5f + 2.5f // fast decay for sparkles
                )
            )
        }
    }

    private fun createExplosionBurst(x: Float, y: Float) {
        // Red, orange, and gray ashes
        val colors = listOf(0xFFFF5722, 0xFFFFC107, 0xFF3E2723, 0xFF757575)
        for (i in 0..25) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * 280f + 80f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    colorHex = colors.random(),
                    size = Random.nextFloat() * 10f + 4f,
                    life = 1.0f,
                    decay = Random.nextFloat() * 2.0f + 1.0f
                )
            )
        }
    }

    private fun endRun() {
        gameState = GameState.GAME_OVER
        // Since run earnings are added instantly on zombie kill, we don't add them here to avoid double counting!
        canContinueRun = false
        savePersistentStats()
        gameJob?.cancel()
    }

    private fun createSplashExplosionParticles(x: Float, y: Float, radius: Float, colorHex: Long) {
        val colors = listOf(colorHex, 0xFFE040FB, 0xFF00E5FF, 0xFFFFFFFF)
        for (i in 0..15) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = Random.nextFloat() * (radius * 1.5f) + 50f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    colorHex = colors.random(),
                    size = Random.nextFloat() * 6f + 3f,
                    life = 0.8f,
                    decay = Random.nextFloat() * 1.5f + 1.2f
                )
            )
        }
    }

    private fun updateTurrets(deltaTime: Float) {
        val now = System.currentTimeMillis()
        for (turret in turrets) {
            if (turret.type == TurretType.NONE) continue
            
            // 1. Range based on level progression
            val maxRange = 300f + (turret.level - 1) * 15f
            
            val turretPos = Vector2(turret.x, turret.y)
            val target = zombies
                .filter { Vector2(it.x, it.y).distanceTo(turretPos) <= maxRange }
                .minByOrNull { Vector2(it.x, it.y).distanceTo(turretPos) }
                
            if (target != null) {
                val targetDir = (Vector2(target.x, target.y) - turretPos).normalized()
                turret.angle = atan2(targetDir.y, targetDir.x) * 180f / PI.toFloat()
                
                // 2. Fire Rate based on level progression (cooldown interval gets faster)
                val cooldownMs = max(100L, 450L - (turret.level - 1) * 20L)
                
                if (now - turret.lastShootTime >= cooldownMs) {
                    turret.lastShootTime = now
                    
                    // 3. Damage based on level progression (reduced by 15% to support player primary damage)
                    val baseDmg = (8f + (turret.level - 1) * 4f) * 0.85f
                    val dmg = baseDmg * (1f + prestigePoints * 0.10f)
                    val speed = 800f + (turret.level - 1) * 12f
                    // Size scales beautifully as weapon caliber grows
                    val size = 7f + (turret.level * 0.4f).coerceAtMost(8f)
                    val color = 0xFF00FFCC // Aqua/Cyan energy stream
                    
                    // 4. Piercing bullets based on level thresholds
                    val pierce = when {
                        turret.level >= 10 -> 5
                        turret.level >= 6 -> 3
                        turret.level >= 3 -> 2
                        else -> 1
                    }
                    
                    // 5. Splash damage config
                    val isSplash = (turret.level >= 5)
                    val splashRad = if (turret.level >= 9) 140f else 90f
                    val splashDmgPct = if (turret.level >= 9) 0.75f else 0.50f

                    // 6. Projectiles count progression
                    if (turret.level >= 8) {
                        // Level 8+: Fires 3 bullets fan-spread style
                        val spreads = listOf(-15f, 0f, 15f)
                        for (angleSpread in spreads) {
                            val radSpread = (turret.angle + angleSpread) * PI.toFloat() / 180f
                            val dirX = cos(radSpread)
                            val dirY = sin(radSpread)
                            bullets.add(
                                Bullet(
                                    x = turret.x,
                                    y = turret.y,
                                    vx = dirX * speed,
                                    vy = dirY * speed,
                                    damage = dmg,
                                    speed = speed,
                                    size = size,
                                    colorHex = color,
                                    piercesRemaining = pierce,
                                    originalPierceCount = pierce,
                                    isTurretBullet = true,
                                    isSplash = isSplash,
                                    splashRadius = splashRad,
                                    splashDamagePercent = splashDmgPct
                                )
                            )
                        }
                    } else if (turret.level >= 4) {
                        // Level 4-7: Fires 2 parallel streams
                        val orthX = -targetDir.y * 6f
                        val orthY = targetDir.x * 6f
                        
                        bullets.add(
                            Bullet(
                                x = turret.x + orthX,
                                y = turret.y + orthY,
                                vx = targetDir.x * speed,
                                vy = targetDir.y * speed,
                                damage = dmg,
                                speed = speed,
                                size = size,
                                colorHex = color,
                                piercesRemaining = pierce,
                                originalPierceCount = pierce,
                                isTurretBullet = true,
                                isSplash = isSplash,
                                splashRadius = splashRad,
                                splashDamagePercent = splashDmgPct
                            )
                        )
                        bullets.add(
                            Bullet(
                                x = turret.x - orthX,
                                y = turret.y - orthY,
                                vx = targetDir.x * speed,
                                vy = targetDir.y * speed,
                                damage = dmg,
                                speed = speed,
                                size = size,
                                colorHex = color,
                                piercesRemaining = pierce,
                                originalPierceCount = pierce,
                                isTurretBullet = true,
                                isSplash = isSplash,
                                splashRadius = splashRad,
                                splashDamagePercent = splashDmgPct
                            )
                        )
                    } else {
                        // Level 1-3: Standard single bullet stream
                        bullets.add(
                            Bullet(
                                x = turret.x,
                                y = turret.y,
                                vx = targetDir.x * speed,
                                vy = targetDir.y * speed,
                                damage = dmg,
                                speed = speed,
                                size = size,
                                colorHex = color,
                                piercesRemaining = pierce,
                                originalPierceCount = pierce,
                                isTurretBullet = true,
                                isSplash = isSplash,
                                splashRadius = splashRad,
                                splashDamagePercent = splashDmgPct
                            )
                        )
                    }
                    
                    // Spawn muzzle spark particles
                    spawnBloodSplatter(turret.x + targetDir.x * 20f, turret.y + targetDir.y * 20f, color, 3)
                }
            }
        }
    }

    fun exitToMenu() {
        canContinueRun = true
        savedWave = currentWave
        savedBaseHp = baseHp
        savedRunEarnings = runEarnings
        savedTurrets.clear()
        turrets.forEach { t ->
            savedTurrets.add(TurretContainer(t.slotId, t.type, t.level))
        }

        gameState = GameState.MENU
        zombies.clear()
        bullets.clear()
        particles.clear()
        damageNumbers.clear()
        coins.clear()
        decals.clear()
        gameJob?.cancel()
    }
}

// Decal data class for static ground effects
data class Decal(
    val x: Float,
    val y: Float,
    val size: Float,
    val colorHex: Long,
    val angle: Float,
    val type: Int
)

data class TurretContainer(
    val slotId: Int,
    val type: TurretType,
    val level: Int
)

// ViewModel factory implementation
class GameViewModelFactory(private val repository: GameStatsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val app = com.example.MyApplication.instance
            return GameViewModel(app, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
