package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

object GameAudioSynth {
    var context: android.content.Context? = null
    private const val SAMPLE_RATE = 44100
    
    var masterVolume = 0.8f
    var musicVolume = 0.15f
    var sfxVolume = 0.6f
    var musicOn = false
    var soundOn = true

    private var masterJob: Job? = null
    private var masterTrack: AudioTrack? = null

    // Mixer properties
    private class ActiveSound(val buffer: FloatArray, val volume: Float) {
        var pos = 0
        var isDead = false
    }

    private val MAX_SOUNDS = 32
    private val activeSounds = Array<ActiveSound?>(MAX_SOUNDS) { null }

    private fun playSfx(buffer: FloatArray, localVolume: Float = 1.0f) {
        if (!soundOn) return
        val sound = ActiveSound(buffer, localVolume * sfxVolume)
        synchronized(activeSounds) {
            // Find empty slot or overwrite finished sound
            var placed = false
            for (i in activeSounds.indices) {
                if (activeSounds[i] == null || activeSounds[i]!!.isDead) {
                    activeSounds[i] = sound
                    placed = true
                    break
                }
            }
            if (!placed) {
                // Steal oldest slot
                var maxPos = -1
                var targetIdx = 0
                for (i in activeSounds.indices) {
                    val act = activeSounds[i]
                    if (act != null && act.pos > maxPos) {
                        maxPos = act.pos
                        targetIdx = i
                    }
                }
                activeSounds[targetIdx] = sound
            }
        }
    }

    // --- SOUND EFFECTS BUFFERS CACHE ---
    private val pistolBuf by lazy { generatePistol() }
    private val smgBuf by lazy { generateSMG() }
    private val shotgunBuf by lazy { generateShotgun() }
    private val minigunBuf by lazy { generateMinigun() }
    private val laserBuf by lazy { generateLaser() }
    
    private val zombieGrowlBuf by lazy { generateZombieGrowl() }
    private val zombieHitBuf by lazy { generateZombieHit() }
    private val zombieDeathBuf by lazy { generateZombieDeath() }
    
    private val bossSpawnBuf by lazy { generateBossSpawn() }
    private val bossDeathBuf by lazy { generateBossDeath() }
    
    private val coinBuf by lazy { generateCoin() }
    private val upgradeBuf by lazy { generateUpgrade() }
    
    private val menuTransitionBuf by lazy { generateMenuTransition() }
    private val buttonClickBuf by lazy { generateButtonClick() }

    // Gun Generators
    private fun generatePistol(): FloatArray {
        val size = (SAMPLE_RATE * 0.18f).toInt()
        val arr = FloatArray(size)
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 22.0)
            val freq = 600.0 * exp(-t * 40.0)
            val osc = sin(2.0 * PI * freq * t)
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = ((osc * 0.7 + noise * 0.4) * env).toFloat()
        }
        return arr
    }

    private fun generateSMG(): FloatArray {
        val size = (SAMPLE_RATE * 0.12f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 30.0)
            val freq = 400.0 * exp(-t * 30.0)
            val osc = sin(2.0 * PI * freq * t)
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = ((osc * 0.5 + noise * 0.6) * env * 0.85).toFloat()
        }
        return arr
    }

    private fun generateShotgun(): FloatArray {
        val size = (SAMPLE_RATE * 0.35f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 12.0)
            val freq = 180.0 * exp(-t * 25.0)
            val osc = sin(2.0 * PI * freq * t)
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = ((osc * 0.6 + noise * 0.8) * env).toFloat()
        }
        return arr
    }

    private fun generateMinigun(): FloatArray {
        val size = (SAMPLE_RATE * 0.1f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 35.0)
            val freq = 300.0 * exp(-t * 20.0)
            val noise = Random.nextFloat() * 2 - 1
            val osc = sin(2.0 * PI * freq * t)
            arr[i] = ((osc * 0.4 + noise * 0.8) * env * 0.8).toFloat()
        }
        return arr
    }

    private fun generateLaser(): FloatArray {
        val size = (SAMPLE_RATE * 0.22f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 15.0)
            val freq = 1600.0 * exp(-t * 28.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = if (sin(phase) > 0) 1.0 else -1.0
            arr[i] = (osc * env * 0.6).toFloat()
        }
        return arr
    }

    // Zombie Generators
    private fun generateZombieGrowl(): FloatArray {
        val size = (SAMPLE_RATE * 0.5f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 6.0)
            val freq = 90.0 + sin(2.0 * PI * 15.0 * t) * 10.0 // Rumbly
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = sin(phase)
            arr[i] = (osc * env * 0.15f).toFloat() // Subtle
        }
        return arr
    }

    private fun generateZombieHit(): FloatArray {
        val size = (SAMPLE_RATE * 0.08f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 35.0)
            val noise = Random.nextFloat() * 2 - 1
            phase += 2.0 * PI * (60.0 + 100.0 * exp(-t * 50.0)) / SAMPLE_RATE
            val thud = sin(phase)
            arr[i] = ((noise * 0.3 + thud * 0.8) * env * 0.12f).toFloat() // Soft fleshy thud
        }
        return arr
    }

    private fun generateZombieDeath(): FloatArray {
        val size = (SAMPLE_RATE * 0.2f).toInt()
        val arr = FloatArray(size)
        var lp = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 18.0)
            val noise = Random.nextFloat() * 2 - 1
            lp += 0.2 * (noise - lp)
            arr[i] = (lp * env * 0.15f).toFloat() // Subtle crunch/poof
        }
        return arr
    }

    // Boss Generators
    private fun generateBossSpawn(): FloatArray {
        val size = (SAMPLE_RATE * 1.5f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val pulse = sin(2.0 * PI * 5.0 * t) * 0.5 + 0.5 // Alarm pulse
            val env = exp(-t * 2.5) * pulse
            val freq = 220.0 + sin(2.0 * PI * 10.0 * t) * 20.0
            val phase = (i.toFloat() / SAMPLE_RATE) * 2.0 * PI * freq
            val osc = (phase / PI) % 2.0 - 1.0 // Saw
            arr[i] = (osc * env * 0.45).toFloat()
        }
        return arr
    }

    private fun generateBossDeath(): FloatArray {
        val size = (SAMPLE_RATE * 2.5f).toInt()
        val arr = FloatArray(size)
        var phase1 = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val envBody = exp(-t * 1.5)
            val envCrack = exp(-t * 10.0)
            val noise = Random.nextFloat() * 2 - 1
            phase1 += 2.0 * PI * (40.0 + 300.0 * exp(-t * 5.0)) / SAMPLE_RATE
            val thump = sin(phase1) * exp(-t * 2.0)
            val mix = (noise * envCrack * 1.5) + (noise * envBody * 0.8) + (thump * 1.5)
            val limited = tanh(mix * 1.5)
            arr[i] = (limited * 0.7).toFloat()
        }
        return arr
    }

    // UI / Gameplay Generators
    private fun generateDollarEarn(): FloatArray {
        val size = (SAMPLE_RATE * 0.12f).toInt()
        val arr = FloatArray(size)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 25.0)
            phase1 += 2.0 * PI * 1318.51 / SAMPLE_RATE // E6
            phase2 += 2.0 * PI * 1760.00 / SAMPLE_RATE // A6
            val osc = (sin(phase1) + sin(phase2)) * 0.5
            arr[i] = (osc * env * 0.4).toFloat()
        }
        return arr
    }

    private fun generateCoin(): FloatArray {
        return generateDollarEarn()
    }

    private fun generatePurchase(): FloatArray {
        val size = (SAMPLE_RATE * 0.6f).toInt()
        val arr = FloatArray(size)
        val freqs = arrayOf(523.25, 659.25, 783.99, 1046.50, 1567.98) // C Major
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 4.0)
            val step = (t * 5.0 / 0.6).toInt().coerceIn(0, 4)
            val freq = freqs[step]
            val phase = (i.toFloat() / SAMPLE_RATE) * 2.0 * PI * freq
            val osc = sin(phase) + sin(phase * 2.0) * 0.3
            val stepEnv = exp(-((t % (0.6/5.0)) * 25.0))
            arr[i] = (osc * stepEnv * env * 0.5).toFloat()
        }
        return arr
    }

    private fun generateUpgrade(): FloatArray {
        return generatePurchase()
    }

    private fun generateMenuTransition(): FloatArray {
        val size = (SAMPLE_RATE * 0.15f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 20.0)
            val freq = 300.0 + 400.0 * exp(-t * 15.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = sin(phase)
            arr[i] = (osc * env * 0.25).toFloat()
        }
        return arr
    }

    private fun generateButtonClick(): FloatArray {
        val size = (SAMPLE_RATE * 0.05f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 60.0)
            val freq = 600.0 * exp(-t * 20.0)
            val osc = sin(2.0 * PI * freq * t)
            arr[i] = (osc * env * 0.2).toFloat()
        }
        return arr
    }

    private fun generateHeadshot(): FloatArray {
        val size = (SAMPLE_RATE * 0.15f).toInt()
        val arr = FloatArray(size)
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 30.0)
            val freq = 1200.0 * exp(-t * 20.0)
            val osc = sin(2.0 * PI * freq * t)
            arr[i] = (osc * env * 0.8).toFloat()
        }
        return arr
    }

    private fun generateWaveStart(): FloatArray {
        val size = (SAMPLE_RATE * 2.0f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = if (t < 0.2) t / 0.2 else exp(-(t - 0.2) * 1.0)
            val freq = 200.0 + sin(2.0 * PI * 1.5 * t) * 50.0 // Siren effect
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = sin(phase)
            arr[i] = (osc * env * 0.9).toFloat()
        }
        return arr
    }

    private fun generateWaveComplete(): FloatArray {
        val size = (SAMPLE_RATE * 1.5f).toInt()
        val arr = FloatArray(size)
        val freqs = arrayOf(440.0, 554.37, 659.25, 880.0)
        var phase = 0.0
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 3.0)
            val step = (t * 4.0 / 1.5).toInt().coerceIn(0, 3)
            val freq = freqs[step]
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = sin(phase)
            arr[i] = (osc * env * 0.8).toFloat()
        }
        return arr
    }

    private fun generateLowHealth(): FloatArray {
        val size = (SAMPLE_RATE * 0.5f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = if (t < 0.1) t / 0.1 else if (t > 0.4) exp(-(t - 0.4) * 10.0) else 1.0
            val freq = 180.0 // Heartbeat
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = sin(phase)
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = ((osc * 0.8 + noise * 0.2) * env * 1.2).toFloat()
        }
        return arr
    }

    private fun generateVictory(): FloatArray {
        val size = (SAMPLE_RATE * 3.0f).toInt()
        val arr = FloatArray(size)
        val freqs = arrayOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val step = (t * 5.0 / 3.0).toInt().coerceIn(0, 4)
            val freq = freqs[step]
            val phase = (i.toFloat() / SAMPLE_RATE) * 2.0 * PI * freq
            val osc = sin(phase)
            val env = exp(-(t % (3.0/5.0)) * 5.0)
            arr[i] = (osc * env * 0.9).toFloat()
        }
        return arr
    }

    private fun generateGameOver(): FloatArray {
        val size = (SAMPLE_RATE * 2.5f).toInt()
        val arr = FloatArray(size)
        var phase = 0.0
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = if (t < 0.2) t / 0.2 else exp(-(t - 0.2) * 1.5)
            val freq = 120.0 * exp(-t * 2.0)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val osc = (phase / PI) % 2.0 - 1.0 // Saw
            arr[i] = (osc * env * 0.8).toFloat()
        }
        return arr
    }
    
    // Procedural parameters for unique sounds
    private fun hashString(str: String): Float {
        var hash = 5381
        for (c in str) {
            hash = ((hash shl 5) + hash) + c.code
        }
        return (kotlin.math.abs(hash) % 1000) / 1000f
    }

    private fun generateWeaponFire(name: String, category: String): FloatArray {
        val dur = when(category) {
            "SHOTGUNS" -> 0.4f
            "MINIGUNS" -> 0.15f
            "PISTOLS" -> 0.25f
            "RIFLES", "SMG" -> 0.2f
            "LASER" -> 0.3f
            else -> 0.25f
        }
        val size = (SAMPLE_RATE * dur).toInt()
        val arr = FloatArray(size)

        var lp1 = 0.0
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val noiseRaw = Random.nextDouble() * 2.0 - 1.0
            
            var mix = 0.0

            if (category == "SHOTGUNS") {
                // Powerful blast, strong impact
                val crack = noiseRaw * exp(-t * 150.0)
                phase1 += 2.0 * PI * (40.0 + 300.0 * exp(-t * 100.0)) / SAMPLE_RATE
                val thump = sin(phase1) * exp(-t * 18.0)
                lp1 += 0.1 * (noiseRaw - lp1)
                val body = lp1 * exp(-t * 12.0)
                mix = crack * 1.5 + thump * 2.2 + body * 2.0
                
            } else if (category == "PISTOLS") {
                // Crisp and satisfying. Small but impactful.
                val crack = noiseRaw * exp(-t * 300.0)
                phase1 += 2.0 * PI * (80.0 + 400.0 * exp(-t * 150.0)) / SAMPLE_RATE
                val thump = sin(phase1) * exp(-t * 40.0)
                lp1 += 0.3 * (noiseRaw - lp1)
                val body = lp1 * exp(-t * 40.0)
                mix = crack * 1.5 + thump * 1.8 + body * 1.0
                
            } else if (category == "RIFLES" || category == "SMG") {
                // Clean modern fast firing sound
                val crack = noiseRaw * exp(-t * 220.0)
                phase1 += 2.0 * PI * (60.0 + 250.0 * exp(-t * 120.0)) / SAMPLE_RATE
                val thump = sin(phase1) * exp(-t * 45.0)
                phase2 += 2.0 * PI * 2000.0 / SAMPLE_RATE
                val mech = sin(phase2) * exp(-t * 200.0)
                lp1 += 0.2 * (noiseRaw - lp1)
                val body = lp1 * exp(-t * 60.0)
                mix = crack * 1.0 + thump * 1.5 + mech * 0.4 + body * 1.0

            } else if (category == "MINIGUNS") {
                // Smooth continuous firing sound
                val crack = noiseRaw * exp(-t * 180.0)
                phase1 += 2.0 * PI * (60.0 + 150.0 * exp(-t * 80.0)) / SAMPLE_RATE
                val thump = sin(phase1) * exp(-t * 50.0)
                lp1 += 0.25 * (noiseRaw - lp1)
                val body = lp1 * exp(-t * 45.0)
                phase2 += 2.0 * PI * 600.0 / SAMPLE_RATE
                val whine = sin(phase2) * 0.15 * exp(-t * 5.0)
                mix = crack * 1.0 + thump * 1.2 + body * 1.0 + whine
                
            } else if (category == "LASER") {
                // Futuristic energy
                phase1 += 2.0 * PI * (800.0 * exp(-t * 15.0) + 200.0) / SAMPLE_RATE
                val laserOsc = sin(phase1)
                phase2 += 2.0 * PI * (400.0 * exp(-t * 20.0) + 100.0) / SAMPLE_RATE
                val laserOsc2 = sin(phase2)
                mix = (laserOsc * 0.6 + laserOsc2 * 0.4) * exp(-t * 20.0)
            } else {
                val crack = noiseRaw * exp(-t * 200.0)
                phase1 += 2.0 * PI * (60.0 + 200.0 * exp(-t * 80.0)) / SAMPLE_RATE
                val thump = sin(phase1) * exp(-t * 30.0)
                mix = crack * 1.2 + thump * 1.2
            }

            // High gain hard-clipping for punch
            mix *= 2.5
            val clipped = if (mix > 1.0) 1.0 else if (mix < -1.0) -1.0 else mix
            
            // Output volume scaling
            arr[i] = (clipped * 0.5f).toFloat() // Moderated to avoid noise buildup
        }

        // Clean short slapback for thick action style sound
        val taps = intArrayOf(
            (SAMPLE_RATE * 0.025).toInt(),
            (SAMPLE_RATE * 0.05).toInt()
        )
        val gains = floatArrayOf(0.15f, 0.08f)

        val out = FloatArray(size)
        for (i in 0 until size) { out[i] = arr[i] }
        for (j in taps.indices) {
            val delay = taps[j]
            val gain = gains[j]
            for (i in delay until size) {
                out[i] += out[i - delay] * gain
            }
        }
        return out
    }

    private fun generateWeaponReload(name: String, category: String): FloatArray {
        val seed = hashString(name + "reload")
        val size = (SAMPLE_RATE * (0.3f + seed * 0.3f)).toInt() // 0.3 - 0.6s
        val arr = FloatArray(size)
        for (i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            // Click-clack env
            val env1 = exp(-t * 40.0)
            val env2 = if (t > 0.15) exp(-(t - 0.15) * 40.0) else 0.0
            val env = env1 + env2
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = (noise * env * 0.5 * (0.5 + seed * 0.5)).toFloat()
        }
        return arr
    }

    private fun generateWeaponImpact(name: String, category: String): FloatArray {
        val seed = hashString(name + "impact")
        val size = (SAMPLE_RATE * 0.08f).toInt()
        val arr = FloatArray(size)
        for(i in 0 until size) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * (30.0 + seed * 10.0))
            val noise = Random.nextFloat() * 2 - 1
            arr[i] = (noise * env * (0.3f + seed * 0.2f)).toFloat()
        }
        return arr
    }

    // API
    // Dynamic Caches
    private val weaponFireCache = mutableMapOf<String, FloatArray>()
    private val weaponReloadCache = mutableMapOf<String, FloatArray>()
    private val weaponImpactCache = mutableMapOf<String, FloatArray>()

    fun playWeaponFire(weaponName: String, categoryName: String) {
        // Disabled
    }

    fun playWeaponReload(weaponName: String, categoryName: String) {
        // Disabled
    }

    fun playWeaponImpact(weaponName: String, categoryName: String) {
        // Disabled
    }

    // New specific SFX Buf
    private val dollarBuf by lazy { generateDollarEarn() }

    fun playHeadshot() {}
    fun playDollarEarn() = playSfx(dollarBuf, 0.5f)
    fun playPurchase() {}
    fun playWaveStart() {}
    fun playWaveComplete() {}
    fun playLowHealth() {}
    fun playVictory() {}
    fun playGameOver() {}

    // Backward compatibility for generic calls
    fun playPistol() {}
    fun playSMG() {}
    fun playShotgun() {}
    fun playMinigun() {}
    fun playLaser() {}
    
    fun playZombieGrowl() {}
    fun playZombieHit() {}
    fun playZombieDeath() {}
    
    fun playBossSpawn() {}
    fun playBossDeath() {}
    
    fun playCoin() = playSfx(coinBuf, 0.5f)
    fun playUpgrade() {}
    
    fun playMenuTransition() {}
    fun playButtonClick() {}

    var gameModeName = "NORMAL"
    var isAppInForeground = false
    var isPlayingGameplay = false
    var isPlayingBossMusic = false

    fun onResume() {
        isAppInForeground = true
        startEngine()
    }

    fun onPause() {
        isAppInForeground = false
        stopEngine()
    }

    fun startGameplayMusic() {
        isPlayingGameplay = true
        isPlayingBossMusic = false
    }

    fun stopGameplayMusic() {
        isPlayingGameplay = false
        isPlayingBossMusic = false
    }

    fun playBossMusic() {
        isPlayingGameplay = false
        isPlayingBossMusic = true
    }

    fun stopBossMusic() {
        isPlayingBossMusic = false
        isPlayingGameplay = true // Resume normal track
    }

    // Master Mixer Loop
    fun startEngine() {
        if (!isAppInForeground) return
        if (masterJob != null) return
        
        masterJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                // Initialize Master AudioTrack
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, 
                    AudioFormat.CHANNEL_OUT_MONO, 
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
                
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setSessionId(android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                
                if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    builder.setContext(context!!)
                }

                masterTrack = builder.build()
                
                masterTrack?.play()

                // BGM Synthesizer State
                val bpm = 110.0
                val beatDuration = 60.0 / bpm
                val beatSamples = (SAMPLE_RATE * beatDuration).toInt()
                val chunkSamples = 1024 // Small chunks for low latency SFX
                val audioBuffer = ShortArray(chunkSamples)
                
                var globalSample = 0
                
                // Dark survival bass progression (A minor)
                val bassFreqs = arrayOf(55.0, 55.0, 58.27, 49.39) 
                
                while (isActive) {
                    val finalVol = masterVolume
                    
                    for (i in 0 until chunkSamples) {
                        var sum = 0f
                        
                        // --- 1. Synthesize Background Music ---
                        val actualMusicOn = musicOn && (isPlayingGameplay || isPlayingBossMusic)
                        if (actualMusicOn) {
                            val beatTime = (globalSample % beatSamples).toFloat() / SAMPLE_RATE
                            val beatIndex = (globalSample / beatSamples) % 4
                            val measureIndex = (globalSample / (beatSamples * 4)) % 4

                            // Kick Drum
                            var kick: Double = 0.0
                            // Bass
                            var bass: Double = 0.0
                            // Hi-hat / Percussion
                            var hat: Double = 0.0
                            // Pad
                            var pad: Double = 0.0

                            if (isPlayingBossMusic) {
                                // Boss Music (Let's make it a slightly faster but still natural tribal beat)
                                val flowBpm = 110.0
                                val beatSamples = (SAMPLE_RATE * (60.0 / flowBpm)).toInt()
                                val t = (globalSample % beatSamples).toFloat() / SAMPLE_RATE
                                // Tribal drum
                                kick = sin(2.0 * PI * (80.0 + 100.0 * exp(-t * 20.0)) * t) * exp(-t * 10.0) * 0.8
                                
                                val measure = (globalSample / (beatSamples * 4)) % 4
                                val freq = if (measure == 0) 130.81 else 146.83 // C3, D3
                                bass = sin(2.0 * PI * freq * ((globalSample % (beatSamples * 4)).toFloat() / SAMPLE_RATE)) * 0.3
                                
                            } else {
                                // Natural Background Music - Ambient & Relaxing
                                val cycleLength = SAMPLE_RATE * 8 // 8 seconds cycle
                                val t = (globalSample % cycleLength).toFloat() / SAMPLE_RATE
                                
                                // Wind noise base
                                val windNoise = Random.nextFloat() * 2 - 1
                                val windLfo = (sin(2.0 * PI * t / 8.0) * 0.5 + 0.5)
                                val wind = windNoise * windLfo * 0.05
                                
                                // Ambient pad (E Minor Pentatonic)
                                val padFreqs = arrayOf(164.81, 196.00, 220.00, 246.94) // E3, G3, A3, B3
                                val padNoteIdx = (globalSample / (SAMPLE_RATE * 2)) % 4 // Change note every 2 seconds
                                val padFreq = padFreqs[padNoteIdx.toInt()]
                                
                                val padPhase = (globalSample * padFreq / SAMPLE_RATE) % 1.0
                                val padOsc = sin(padPhase * 2.0 * PI) * 0.15
                                
                                // Gentle smooth envelope for the pad
                                val padEnv = sin(PI * ((globalSample % (SAMPLE_RATE * 2)).toFloat() / (SAMPLE_RATE * 2)))
                                pad = padOsc * padEnv
                                
                                // Occasional "bird chirp" or upper chime
                                var chime = 0.0
                                val chimeCycle = SAMPLE_RATE * 3 // every 3 seconds
                                val tChime = (globalSample % chimeCycle).toFloat() / SAMPLE_RATE
                                if (tChime < 0.5) {
                                    val chimeEnv = exp(-tChime * 10.0)
                                    val chimeFreq = 1318.51 + 100.0 * sin(2.0 * PI * 5.0 * tChime) // E6 with vibrato
                                    chime = sin(2.0 * PI * chimeFreq * tChime) * chimeEnv * 0.05
                                }
                                
                                hat = wind + chime
                            }

                            sum += ((kick + bass + hat + pad) * musicVolume).toFloat()
                        }
                        
                        // --- 2. Mix Active Sound Effects ---
                        synchronized(activeSounds) {
                            for (j in activeSounds.indices) {
                                val act = activeSounds[j]
                                if (act != null && !act.isDead) {
                                    sum += act.buffer[act.pos] * act.volume
                                    act.pos++
                                    if (act.pos >= act.buffer.size) {
                                        act.isDead = true
                                        // free memory reference implicitly handled if overwritten later, 
                                        // but we can null it out to be clean
                                        activeSounds[j] = null
                                    }
                                }
                            }
                        }
                        
                        // --- 3. Master Limiter & Output ---
                        // Soft clipping with tanh
                        val limited = tanh(sum * finalVol)
                        audioBuffer[i] = (limited * 32767f).toInt().toShort()
                        
                        globalSample++
                    }
                    
                    masterTrack?.write(audioBuffer, 0, chunkSamples)
                }
            } catch (e: Exception) {
                Log.e("GameAudioSynth", "Master mixer loop aborted", e)
            } finally {
                try {
                    masterTrack?.stop()
                    masterTrack?.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
                masterTrack = null
            }
        }
    }

    fun stopEngine() {
        masterJob?.cancel()
        masterJob = null
    }
}
