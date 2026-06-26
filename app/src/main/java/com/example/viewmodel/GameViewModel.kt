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

enum class GameMode(val displayName: String, val desc: String, val reward: Long) {
    NORMAL("Normal Mode", "Calm Zombies\nReward: 15 Coins + $5 per zombie", 5L),
    HARDCORE("Hardcore Mode", "Stronger Zombies\nReward: 15 Coins + $10 per zombie", 10L),
    NIGHTMARE("Nightmare Mode", "Brutal Zombies\nReward: 15 Coins + $20 per zombie", 20L)
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
    val categoryName: String,
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
    var bankDollars by mutableLongStateOf(0L)
    var ownedWeapons = mutableStateListOf<String>()
    var ownedTurrets = mutableStateListOf<String>()
    var equippedPistol by mutableStateOf("Ranger P1")
    var equippedShotgun by mutableStateOf("Crusher 12G")
    var equippedRifle by mutableStateOf("AR-X Defender")
    var equippedMinigun by mutableStateOf("Vulcan M1")
    var equippedTurretsCSV by mutableStateOf("Basic Turret")
    
    var selectedLoadoutTab by mutableStateOf("WEAPONS")
    var selectedLoadoutSubTab by mutableStateOf("PISTOLS")

    var highestWaveNormal by mutableIntStateOf(1)
    var highestWaveHardcore by mutableIntStateOf(1)
    var highestWaveNightmare by mutableIntStateOf(1)
    var currentGameMode by mutableStateOf(GameMode.NORMAL)

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
    var musicVolume by mutableFloatStateOf(0.0f)
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
    var pendingTurretX by mutableFloatStateOf(0f)
    var pendingTurretY by mutableFloatStateOf(0f)
    var toastMessage by mutableStateOf<String?>(null)

    fun showToast(msg: String) {
        toastMessage = msg
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    // Current Active Run states
    var gameState by mutableStateOf(GameState.MENU)
    var isPaused by mutableStateOf(false)

    fun togglePause() {
        if (gameState == GameState.PLAYING) {
            isPaused = !isPaused
            if (!isPaused) {
                // When unpausing, ensure delta time doesn't spike
                // by restarting the game loop with a fresh timestamp
                startGameLoop()
            } else {
                gameJob?.cancel()
            }
        }
    }
    var currentWave by mutableIntStateOf(1)
    var baseHp by mutableFloatStateOf(100f)
    var baseMaxHp by mutableFloatStateOf(100f)
    var runEarnings by mutableLongStateOf(0L)
    var runZombiesKilled by mutableIntStateOf(0)
    var runDollarsEarned by mutableLongStateOf(0L)
    var reviveUsedThisRun by mutableStateOf(false)
    var doubleRewardsClaimedThisRun by mutableStateOf(false)
    
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
    var bossWarningTimer by mutableFloatStateOf(0f)
    var isBossWarningActive by mutableStateOf(false)
    var activeBossName by mutableStateOf("")
    var activeBossHp by mutableFloatStateOf(0f)
    var activeBossMaxHp by mutableFloatStateOf(1f)
    var isBossActive by mutableStateOf(false)
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

        // Observe persistent data reactively
        viewModelScope.launch {
            repository.gameStats.collect { stats ->
                hpLevel = stats.hpUpgradeLevel
                gunLevel = stats.gunUpgradeLevel
                regenLevel = stats.regenUpgradeLevel
                outpostLevel = stats.outpostUpgradeLevel
                lifetimeCoins = stats.lifetimeEarnings
                highestWaveReached = stats.highestWaveReached
                highestWaveNormal = stats.highestWaveNormal
                highestWaveHardcore = stats.highestWaveHardcore
                highestWaveNightmare = stats.highestWaveNightmare
                
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
                
                bankDollars = stats.bankDollars

                ownedWeapons.clear()
                ownedWeapons.addAll(stats.ownedWeaponsCSV.split(",").filter { it.isNotBlank() })
                if (ownedWeapons.isEmpty()) {
                    ownedWeapons.addAll(listOf("Ranger P1", "Crusher 12G", "AR-X Defender", "Vulcan M1"))
                }

                ownedTurrets.clear()
                ownedTurrets.addAll(stats.ownedTurretsCSV.split(",").filter { it.isNotBlank() })
                if (ownedTurrets.isEmpty()) {
                    ownedTurrets.add("Basic Turret")
                }

                equippedPistol = stats.equippedPistol
                equippedShotgun = stats.equippedShotgun
                equippedRifle = stats.equippedRifle
                equippedMinigun = stats.equippedMinigun
                equippedTurretsCSV = stats.equippedTurretsCSV
                
                // Recalculate max hp (only depends on player hp level now)
                baseMaxHp = 100f + (hpLevel - 1) * 25f
            }
        }
        initializeTurrets()
    }

    override fun onCleared() {
        super.onCleared()
        com.example.audio.GameAudioSynth.stopEngine()
    }

    fun initializeTurrets() {
        turrets.clear()
        // Initialize 4 turret slots around base consistently
        turrets.add(Turret(0, 400f, 480f, TurretType.NONE, 1)) // TOP
        turrets.add(Turret(1, 400f, 720f, TurretType.NONE, 1)) // BOTTOM
        turrets.add(Turret(2, 280f, 600f, TurretType.NONE, 1)) // LEFT
        turrets.add(Turret(3, 520f, 600f, TurretType.NONE, 1)) // RIGHT
    }

    // Formulas for Upgrades
    val hpCost: Long get() = (50 + (hpLevel - 1) * 35).toLong()
    val gunCost: Long get() = when (gunLevel) {
        1 -> 150L
        2 -> 750L
        3 -> 2500L
        else -> 2500L // Max level
    }
    val regenCost: Long get() = (80 + regenLevel * 55).toLong()
    val outpostCost: Long get() = (150 + (outpostLevel - 1) * 100).toLong()

    val regenRate: Float get() {
        val baseRegen = if (regenLevel <= 0) 0f else regenLevel * 0.5f
        val prestigeRegen = prestigePoints * 0.2f
        return baseRegen + prestigeRegen
    }

    // Helper to get weapon stats based on overall gun upgrade level
    fun getGunStats(level: Int): GunStats {
        val equippedId = when (min(level, 4)) {
            1 -> equippedPistol
            2 -> equippedShotgun
            3 -> equippedRifle
            else -> equippedMinigun
        }
        val weaponItem = com.example.model.LoadoutData.WEAPONS.find { it.id == equippedId }
            ?: com.example.model.LoadoutData.WEAPONS.first()
            
        val damageMultiplier = 1f + prestigePoints * 0.10f
        val finalDamage = weaponItem.damageBase * damageMultiplier

        return GunStats(
            tierName = weaponItem.name,
            categoryName = weaponItem.category.name,
            damage = finalDamage,
            fireIntervalMs = (1000f / weaponItem.fireRateBase).toLong(),
            projectileSpeed = 900f,
            bulletSize = 12f,
            colorHex = when(weaponItem.category) {
                com.example.model.WeaponCategory.PISTOLS -> 0xFFFFFFFF
                com.example.model.WeaponCategory.SHOTGUNS -> 0xFFF44336
                com.example.model.WeaponCategory.RIFLES -> 0xFFFFEB3B
                com.example.model.WeaponCategory.MINIGUNS -> 0xFF00BCD4
            },
            pierceCount = when(weaponItem.category) {
                 com.example.model.WeaponCategory.RIFLES -> 2
                 com.example.model.WeaponCategory.MINIGUNS -> 2
                 else -> 1
            }
        )
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
        if (gunLevel >= 4) return
        val cost = gunCost
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            gunLevel += 1
            com.example.audio.GameAudioSynth.playUpgrade()
            savePersistentStats()
        }
    }

    fun upgradeRegen() {
        if (regenLevel >= 5) return
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

    fun getTurretUpgradeCost(level: Int): Long {
        return (level * 100L)
    }

    fun upgradeTurret(slotId: Int) {
        val index = turrets.indexOfFirst { it.slotId == slotId }
        val turret = turrets.getOrNull(index)
        if (turret != null && turret.type != TurretType.NONE) {
            val cost = getTurretUpgradeCost(turret.level)
            if (lifetimeCoins >= cost) {
                lifetimeCoins -= cost
                turrets[index] = turret.copy(level = turret.level + 1)
                triggerScreenShake(2f)
                com.example.audio.GameAudioSynth.playUpgrade()
                savePersistentStats()
            }
        }
    }

    fun deployTurret(slotId: Int, type: TurretType) {
        val cost = getTurretBuildCost(type)
        if (lifetimeCoins >= cost) {
            lifetimeCoins -= cost
            var targetX = 0f
            var targetY = 0f
            
            val index = turrets.indexOfFirst { it.slotId == slotId }
            val turret = turrets.getOrNull(index)
            if (turret != null) {
                turrets[index] = turret.copy(type = type, level = 1)
                targetX = turret.x
                targetY = turret.y
            }
            
            if (targetX != 0f) {
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
                            x = targetX,
                            y = targetY,
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

    fun dismantleTurret(slotId: Int) {
        val index = turrets.indexOfFirst { it.slotId == slotId }
        val turret = turrets.getOrNull(index)
        if (turret != null && turret.type != TurretType.NONE) {
            // Refund 50% value of turret build
            val buildCost = getTurretBuildCost(turret.type)
            val refund = (buildCost * 0.5f).toLong()
            lifetimeCoins += refund
            turrets[index] = turret.copy(type = TurretType.NONE, level = 1)
            triggerScreenShake(2f)
            savePersistentStats()
        }
    }

    fun savePersistentStatsPublic() {
        savePersistentStats()
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
                    highestWaveNormal = highestWaveNormal,
                    highestWaveHardcore = highestWaveHardcore,
                    highestWaveNightmare = highestWaveNightmare,
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
                    performanceModeOn = isPerformanceModeOn,
                    bankDollars = bankDollars,
                    ownedWeaponsCSV = ownedWeapons.joinToString(","),
                    ownedTurretsCSV = ownedTurrets.joinToString(","),
                    equippedPistol = equippedPistol,
                    equippedShotgun = equippedShotgun,
                    equippedRifle = equippedRifle,
                    equippedMinigun = equippedMinigun,
                    equippedTurretsCSV = equippedTurretsCSV
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
                    performanceModeOn = false,
                    bankDollars = 0L,
                    ownedWeaponsCSV = "Ranger P1,Crusher 12G,AR-X Defender,Vulcan M1",
                    ownedTurretsCSV = "Basic Turret",
                    equippedPistol = "Ranger P1",
                    equippedShotgun = "Crusher 12G",
                    equippedRifle = "AR-X Defender",
                    equippedMinigun = "Vulcan M1",
                    equippedTurretsCSV = "Basic Turret"
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
            bankDollars = 0L
            ownedWeapons.clear()
            ownedWeapons.addAll(listOf("Ranger P1", "Crusher 12G", "AR-X Defender", "Vulcan M1"))
            ownedTurrets.clear()
            ownedTurrets.add("Basic Turret")
            equippedPistol = "Ranger P1"
            equippedShotgun = "Crusher 12G"
            equippedRifle = "AR-X Defender"
            equippedMinigun = "Vulcan M1"
            equippedTurretsCSV = "Basic Turret"
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
            bankDollars += achievement.rewardCoins
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
        
        val (dollars, points) = when (consecutiveDailyDays) {
            1 -> Pair(250L, 0)
            2 -> Pair(500L, 0)
            3 -> Pair(1000L, 0)
            4 -> Pair(1500L, 0)
            5 -> Pair(2500L, 1)
            else -> Pair(250L, 0)
        }
        
        bankDollars += dollars
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
        com.example.audio.GameAudioSynth.stopGameplayMusic()
        canContinueRun = false
        
        com.example.audio.GameAudioSynth.playUpgrade()
        savePersistentStats()
    }

    fun togglePerformanceMode(enabled: Boolean) {
        isPerformanceModeOn = enabled
        savePersistentStats()
    }

    // Start Run
    fun abandonRun() {
        canContinueRun = false
        gameState = GameState.MENU
        com.example.audio.GameAudioSynth.stopGameplayMusic()
        gameJob?.cancel()
        
        // Reset Run State
        lifetimeCoins = 0L
        hpLevel = 1
        gunLevel = 1
        regenLevel = 0
        outpostLevel = 1
        runEarnings = 0L

        savePersistentStats()
    }
    
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
        runZombiesKilled = 0
        runDollarsEarned = 0L
        reviveUsedThisRun = false
        doubleRewardsClaimedThisRun = false
        lifetimeCoins = 0L
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
        betweenWaveTimer = 0f
        
        prepareWave(currentWave)
        gameState = GameState.PLAYING
        
        // Save the clean starting states right away
        savePersistentStats()
        
        com.example.audio.GameAudioSynth.gameModeName = currentGameMode.name
        com.example.audio.GameAudioSynth.startGameplayMusic()
        startGameLoop()
    }

    // Manual start next wave helper when Auto Skip is OFF
    fun startNextWave() {
        com.example.audio.GameAudioSynth.playMenuTransition()
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
        betweenWaveTimer = 0f

        prepareWave(currentWave)
        gameState = GameState.PLAYING
        
        com.example.audio.GameAudioSynth.gameModeName = currentGameMode.name
        com.example.audio.GameAudioSynth.startGameplayMusic()
        startGameLoop()
    }

    private fun prepareWave(wave: Int) {
        waveZombiesSpawned = 0
        com.example.audio.GameAudioSynth.playWaveStart()
        val weapon = getGunStats(gunLevel)
        com.example.audio.GameAudioSynth.playWeaponReload(weapon.tierName, weapon.categoryName)
        
        val isBossWave = (wave == 25 || wave == 50 || wave == 75 || wave == 100)
        if (isBossWave) {
            isBossWarningActive = true
            bossWarningTimer = 3.0f
            triggerScreenShake(20f)
            com.example.audio.GameAudioSynth.playBossSpawn()
            com.example.audio.GameAudioSynth.stopGameplayMusic()
            com.example.audio.GameAudioSynth.playBossMusic()
        }
        
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
            if (isAutoSkipEnabled) {
                isBetweenWaves = false
                prepareWave(currentWave)
            } else {
                betweenWaveTimer -= deltaTime
                if (betweenWaveTimer <= 0f) {
                    isBetweenWaves = false
                    prepareWave(currentWave)
                }
            }
        }
        
        if (isBossWarningActive) {
            bossWarningTimer -= deltaTime
            if (bossWarningTimer <= 0f) {
                isBossWarningActive = false
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
        if (!isBetweenWaves && !isBossWarningActive && waveZombiesSpawned < totalWaveZombies) {
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
                // Determine direction to player
                val direction = (playerPos - zombiePos).normalized()
                
                // 1. Crowd separation logic to prevent overlapping and merging
                var repX = 0f
                var repY = 0f
                
                // Scan other zombies to calculate push-back forces
                for (i in 0 until zombies.size) {
                    val other = zombies[i]
                    if (other.id != zombie.id) {
                        val dx = zombie.x - other.x
                        val dy = zombie.y - other.y
                        val distSqr = dx * dx + dy * dy
                        
                        // Enforce minimum physical spacing between bodies
                        val minDistance = zombie.size + other.size + 14f 
                        val minDistSqr = minDistance * minDistance
                        
                        if (distSqr < minDistSqr && distSqr > 0.1f) {
                            val dist = kotlin.math.sqrt(distSqr)
                            val pushWeight = (minDistance - dist) / minDistance
                            repX += (dx / dist) * pushWeight
                            repY += (dy / dist) * pushWeight
                        }
                    }
                }
                
                // 2. Combine tracking movement with crowd separation
                // The 1.8 multiplier enforces a strong separation force when clumped
                val combinedMoveX = direction.x + (repX * 1.8f)
                val combinedMoveY = direction.y + (repY * 1.8f)
                val finalDir = Vector2(combinedMoveX, combinedMoveY).normalized()
                
                zombie.x += finalDir.x * zombie.speed * 40f * deltaTime
                zombie.y += finalDir.y * zombie.speed * 40f * deltaTime
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
        } else if (baseHp <= baseMaxHp * 0.25f) {
            // Low health warning
            if (Random.nextFloat() < deltaTime * 1.5f) { // roughly every 0.6 seconds
                com.example.audio.GameAudioSynth.playLowHealth()
            }
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
                    val isHeadshot = Random.nextFloat() < 0.15f // 15% headshot chance
                    val finalDmg = if (isHeadshot) bullet.damage * 2.5f else bullet.damage
                    
                    // Inflict damage
                    zombie.hp -= finalDmg
                    zombie.hitTimer = 0.18f
                    hitOccurred = true

                    if (isHeadshot) {
                        com.example.audio.GameAudioSynth.playHeadshot()
                    } else if (Random.nextFloat() < 0.25f) {
                        com.example.audio.GameAudioSynth.playZombieHit()
                    }
                    
                    // Always play custom weapon impact for non-turrets
                    if (!bullet.isTurretBullet) {
                        com.example.audio.GameAudioSynth.playWeaponImpact(weapon.tierName, weapon.categoryName)
                    }

                    // Floating damage indicator
                    val colorNum = if (isHeadshot) 0xFFFF1744 else if (bullet.isTurretBullet) 0xFF00FFCC else (if (bullet.piercesRemaining == weapon.pierceCount) weapon.colorHex else 0xFFFFFFFF)
                    val prefix = if (isHeadshot) "CRIT " else ""
                    damageNumbers.add(
                        DamageNumber(
                            x = zombie.x + (Random.nextFloat() * 20f - 10f),
                            y = zombie.y - 15f,
                            text = prefix + finalDmg.toInt().toString(),
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
            com.example.audio.GameAudioSynth.playWaveComplete()
            // Trigger Wave Transition
            isBetweenWaves = true
            betweenWaveTimer = 2.0f // 2.0s duration for normal wave delay
            
            // Grant Wave Completion Bonus Reward! (Wave 1 = +$100, Wave 2 = +$150, Wave 3 = +$200...)
            val waveCompleted = currentWave
            val waveBonus = 50L + (waveCompleted * 50L)
            
            // Every 10th wave awards a massive Horde Bonus (+1,000 for Wave 10, +2,000 for Wave 20...)
            val isHordeWave = (waveCompleted % 10 == 0)
            if (isHordeWave || waveCompleted % 5 == 0) {
                com.example.audio.GameAudioSynth.playVictory()
            }
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
            }
            
            // Advance wave
            currentWave += 1
            if (currentWave > highestWaveReached) {
                highestWaveReached = currentWave
            }
            when (currentGameMode) {
                GameMode.NORMAL -> {
                    if (currentWave > highestWaveNormal) highestWaveNormal = currentWave
                }
                GameMode.HARDCORE -> {
                    if (currentWave > highestWaveHardcore) highestWaveHardcore = currentWave
                }
                GameMode.NIGHTMARE -> {
                    if (currentWave > highestWaveNightmare) highestWaveNightmare = currentWave
                }
            }
            savePersistentStats()
        }
    }

    private fun defeatZombie(zombie: Zombie) {
        totalZombiesKilled += 1
        if (zombie.type == ZombieType.BOSS) {
            bossesDefeated += 1
            isBossActive = false
            com.example.audio.GameAudioSynth.stopBossMusic()
            com.example.audio.GameAudioSynth.startGameplayMusic()
        }

        // Play zombie decay/death audio
        if (zombie.type == ZombieType.BOSS) {
            com.example.audio.GameAudioSynth.playBossDeath()
        } else if (Random.nextFloat() < 0.35f) {
            com.example.audio.GameAudioSynth.playZombieDeath()
        }

        val prestigeBonusFactor = 1f + prestigePoints * 0.10f
        var coinValue = 15L
        if (zombie.type != ZombieType.BOSS) {
            coinValue = (zombie.reward * prestigeBonusFactor).toLong()
            if (coinValue < 1L) coinValue = 1L
        } else {
            coinValue = 15L
        }
        var dollarValue = currentGameMode.reward.toLong()
        
        if (zombie.type == ZombieType.BOSS) {
            when (currentWave) {
                25 -> { coinValue = 250; dollarValue = 1000 }
                50 -> { coinValue = 500; dollarValue = 2500 }
                75 -> { coinValue = 1000; dollarValue = 5000 }
                100 -> { coinValue = 2500; dollarValue = 10000 }
                else -> { coinValue = 1000; dollarValue = 5000 }
            }
        }
        
        runZombiesKilled += 1
        runDollarsEarned += dollarValue
        
        // Add coins for upgrades
        lifetimeCoins += coinValue
        runEarnings += coinValue
        totalMoneyEarned += coinValue

        // Add dollars for loadout shop
        bankDollars += dollarValue
        
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
                    text = if (zombie.type == ZombieType.BOSS) "BOSS DEFEATED! +$coinValue Coins, +$$dollarValue" else if (zombie.isGolden) "GOLDEN REWARD! +$coinValue Coins, +$$dollarValue ✨" else "+$coinValue, +$$dollarValue",
                    colorHex = if (zombie.type == ZombieType.BOSS) 0xFF00E5FF else if (zombie.isGolden) 0xFFFFD700 else 0xFF81C784,
                    life = if (zombie.type == ZombieType.BOSS) 3.0f else if (zombie.isGolden) 1.6f else 1.0f
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
                amount = coinValue.toInt()
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
        
        com.example.audio.GameAudioSynth.playWeaponFire(weapon.tierName, weapon.categoryName)
        
        when (weapon.categoryName) {
            "SHOTGUNS" -> {
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
            "MINIGUNS" -> {
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
        // Spawn realistic brass shell casing ejection to the side
        for (i in 0 until 1) {
            val ejectSide = if (Random.nextBoolean()) 1f else -1f
            particles.add(
                Particle(
                    x = playerPos.x + direction.x * 5f,
                    y = playerPos.y + direction.y * 5f,
                    vx = lateral.x * 120f * ejectSide + direction.x * -20f,
                    vy = lateral.y * 120f * ejectSide + direction.y * -20f,
                    colorHex = 0xFFA67C00, // Shiny Brass Casing color
                    size = 5f, // Larger "particle" to look like a casing
                    life = 1f,
                    decay = 2.5f // Falls fast
                )
            )
        }
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
        val isBossWave = (currentWave == 25 || currentWave == 50 || currentWave == 75 || currentWave == 100)
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
                val waveStr = "WAVE $currentWave"
                val bossProps = when (currentWave) {
                    25 -> listOf("BRUTE BOSS", 0xFF607D8B, 1500f, 1.4f, 50f, 55f)
                    50 -> listOf("TOXIC BEHEMOTH", 0xFF00FF00, 3500f, 1.6f, 75f, 65f)
                    75 -> listOf("REAPER MUTANT", 0xFF880E4F, 7000f, 2.2f, 120f, 50f)
                    100 -> listOf("NIGHTMARE KING", 0xFF4A148C, 15000f, 1.8f, 200f, 80f)
                    else -> listOf("UNKNOWN BOSS", 0xFF00E5FF, 1000f, 1.5f, 50f, 48f)
                }

                val title = bossProps[0] as String
                val color = (bossProps[1] as Long)
                val hp = (bossProps[2] as Float) * waveScaleFactor
                val bSpeed = (bossProps[3] as Float) * speedScaleFactor
                val dmg = (bossProps[4] as Float) * damageScaleFactor
                val bSize = bossProps[5] as Float
                
                isBossActive = true
                activeBossName = title
                activeBossHp = hp
                activeBossMaxHp = hp

                Zombie(
                    type = ZombieType.BOSS,
                    x = sx,
                    y = sy,
                    maxHp = hp,
                    hp = hp,
                    speed = bSpeed,
                    damage = dmg,
                    size = bSize,
                    reward = 500, 
                    colorHex = color,
                    bossName = title
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
        if (zombie.type == ZombieType.BOSS) {
            com.example.audio.GameAudioSynth.playBossSpawn()
        } else if (Random.nextFloat() < 0.12f) {
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
        com.example.audio.GameAudioSynth.stopGameplayMusic()
        com.example.audio.GameAudioSynth.playGameOver()
        
        com.example.audio.GameAudioSynth.playDollarEarn()

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
        val equippedId = equippedTurretsCSV.split(",").firstOrNull() ?: "Basic Turret"
        val turretItem = com.example.model.LoadoutData.TURRETS.find { it.id == equippedId } ?: com.example.model.LoadoutData.TURRETS.first()

        for (turret in turrets) {
            if (turret.type == TurretType.NONE) continue
            
            val maxRange = turretItem.range
            
            val turretPos = Vector2(turret.x, turret.y)
            val target = zombies
                .filter { Vector2(it.x, it.y).distanceTo(turretPos) <= maxRange }
                .minByOrNull { Vector2(it.x, it.y).distanceTo(turretPos) }
                
            if (target != null) {
                val targetDir = (Vector2(target.x, target.y) - turretPos).normalized()
                turret.angle = atan2(targetDir.y, targetDir.x) * 180f / PI.toFloat()
                
                val levelFireRateMultiplier = 1f + (turret.level - 1) * 0.25f
                val effectiveFireRate = turretItem.fireRateBase * levelFireRateMultiplier
                val cooldownMs = (1000L / effectiveFireRate).toLong()
                
                if (now - turret.lastShootTime >= cooldownMs) {
                    turret.lastShootTime = now
                    
                    val dmg = turretItem.damageBase * (1f + prestigePoints * 0.10f)
                    val speed = 950f
                    val size = 9f
                    val color = when(turretItem.turretType) {
                        TurretType.GATLING -> 0xFF00FFCC
                        TurretType.PLASMA -> 0xFFFFEB3B
                        TurretType.TESLA -> 0xFFE040FB
                        else -> 0xFFFFFFFF
                    }
                    
                    val pierce = if (turretItem.turretType == TurretType.PLASMA || turretItem.turretType == TurretType.TESLA) 3 else 1
                    val isSplash = turretItem.turretType == TurretType.PLASMA
                    val splashRad = 120f
                    val splashDmgPct = 0.5f

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
                    
                    spawnBloodSplatter(turret.x + targetDir.x * 20f, turret.y + targetDir.y * 20f, color, 3)
                }
            }
        }
    }

    fun exitToMenu() {
        if (gameState != GameState.GAME_OVER) {
            canContinueRun = true
            savedWave = currentWave
            savedBaseHp = baseHp
            savedRunEarnings = runEarnings
            savedTurrets.clear()
            turrets.forEach { t ->
                savedTurrets.add(TurretContainer(t.slotId, t.type, t.level))
            }
        }
        com.example.audio.GameAudioSynth.stopGameplayMusic()
        
        gameState = GameState.MENU
        zombies.clear()
        bullets.clear()
        particles.clear()
        damageNumbers.clear()
        coins.clear()
        decals.clear()
        gameJob?.cancel()
    }

    fun revive() {
        if (!reviveUsedThisRun) {
            reviveUsedThisRun = true
            baseHp = baseMaxHp / 2f // Revive with half health or full health? Requirement says "continue the current run", I'll give full health to be generous.
            // Oh wait, instructions say "revives immediately", let's restore full hp:
            baseHp = baseMaxHp
            gameState = GameState.PLAYING
            com.example.audio.GameAudioSynth.playPurchase()
            com.example.audio.GameAudioSynth.startGameplayMusic()
            startGameLoop()
        }
    }

    fun claimDoubleRewardsAndExit() {
        if (!doubleRewardsClaimedThisRun) {
            doubleRewardsClaimedThisRun = true
            bankDollars += runDollarsEarned
            runDollarsEarned *= 2
            com.example.audio.GameAudioSynth.playDollarEarn()
            savePersistentStats()
            exitToMenu()
        }
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
