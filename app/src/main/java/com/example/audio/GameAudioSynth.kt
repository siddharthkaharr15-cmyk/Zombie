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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

object GameAudioSynth {
    private const val SAMPLE_RATE = 22050
    
    // Volume adjustments and status properties
    var masterVolume = 0.8f
    var musicVolume = 0.4f
    var sfxVolume = 0.6f
    var musicOn = true
    var soundOn = true

    // Background thread pool to play sound effects concurrently without pausing the loop
    private val sfxExecutor = ThreadPoolExecutor(
        2, 5, 5, TimeUnit.SECONDS, LinkedBlockingQueue(15)
    )

    private var musicJob: Job? = null
    private var musicTrack: AudioTrack? = null

    // 1. Pistol Bullet Sound
    val pistolBuffer: ShortArray by lazy {
        val durationSec = 0.15f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(42)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = 310.0 - t * 220.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            
            val noiseVal = rand.nextFloat() * 2f - 1f
            val noiseMix = 0.22f * (1.0f - t)
            val waveMix = sineValue * (1.0f - noiseMix) + noiseVal * noiseMix
            val env = (1.0f - t) * (1.0f - t)
            
            arr[i] = (waveMix * env * 22000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 2. SMG Fire Sound
    val smgBuffer: ShortArray by lazy {
        val durationSec = 0.08f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(13)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = 420.0 - t * 260.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            
            val noiseVal = rand.nextFloat() * 2f - 1f
            val noiseMix = 0.32f * (1.0f - t)
            val waveMix = sineValue * (1.0f - noiseMix) + noiseVal * noiseMix
            val env = 1.0f - t
            
            arr[i] = (waveMix * env * 20000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 3. Shotgun Fire Sound
    val shotgunBuffer: ShortArray by lazy {
        val durationSec = 0.28f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(99)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = 130.0 - t * 80.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            
            val noiseVal = rand.nextFloat() * 2f - 1f
            val noiseMix = 0.68f * (1.0f - t)
            val waveMix = sineValue * (1.0f - noiseMix) + noiseVal * noiseMix
            val env = (1.0f - t) * (1.0f - t) * (1.0f - t)
            
            arr[i] = (waveMix * env * 26000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 4. Minigun Fire Sound
    val minigunBuffer: ShortArray by lazy {
        val durationSec = 0.06f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(7)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = 470.0 - t * 190.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            
            val noiseVal = rand.nextFloat() * 2f - 1f
            val noiseMix = 0.25f * (1.0f - t)
            val waveMix = sineValue * (1.0f - noiseMix) + noiseVal * noiseMix
            val env = 1.0f - t
            
            arr[i] = (waveMix * env * 18000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 5. Laser Fire Sound
    val laserBuffer: ShortArray by lazy {
        val durationSec = 0.20f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val baseFreq = 1200.0 - t * 900.0
            val freq = baseFreq + sin(2.0 * PI * 35.0 * t) * 45.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            val env = (1.0f - t)
            
            arr[i] = (sineValue * env * 18000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 6. Zombie Growl (Ambient / Spawning)
    val zombieGrowlBuffer: ShortArray by lazy {
        val durationSec = 0.60f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(666)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val lfo = sin(2.0 * PI * 16.0 * t.toDouble())
            val baseFreq = 80.0 + t * 40.0
            val freq = baseFreq + lfo * 20.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            
            val sineVal = sin(phase)
            val noiseVal = rand.nextFloat() * 2f - 1f
            val waveMix = sineVal * 0.35f + noiseVal * 0.65f
            val env = if (t < 0.12f) (t / 0.12f) else (1.0f - t) / 0.88f * (1.0f - t)
            
            arr[i] = (waveMix * env * 12000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 7. Zombie Bullet Damage Hit Sound
    val zombieHitBuffer: ShortArray by lazy {
        val durationSec = 0.05f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        val rand = Random(45)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val noiseVal = rand.nextFloat() * 2f - 1f
            val env = (1.0f - t) * (1.0f - t)
            
            arr[i] = (noiseVal * env * 13000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 8. Zombie Death Cry Sound
    val zombieDeathBuffer: ShortArray by lazy {
        val durationSec = 0.80f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val rand = Random(777)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val lfo = sin(2.0 * PI * 12.0 * t.toDouble())
            val baseFreq = 90.0 - t * 45.0
            val freq = baseFreq + lfo * 18.0
            phase += 2.0 * PI * freq / SAMPLE_RATE
            
            val sineVal = sin(phase)
            val noiseVal = rand.nextFloat() * 2f - 1f
            val waveMix = sineVal * 0.30f + noiseVal * 0.70f
            val env = (1.0f - t) * (1.0f - t)
            
            arr[i] = (waveMix * env * 13000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 9. Golden Coins Collection Sound
    val coinBuffer: ShortArray by lazy {
        val durationSec = 0.15f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        val firstPartSamples = (numSamples * 0.35f).toInt()
        for (i in 0 until numSamples) {
            val freq = if (i < firstPartSamples) 988.0 else 1318.0 // B5 -> E6
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            val t = i.toFloat() / numSamples
            val env = (1.0f - t)
            
            arr[i] = (sineValue * env * 15000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // 10. Upgrade Purchase Sound
    val upgradeBuffer: ShortArray by lazy {
        val durationSec = 0.32f
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val arr = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val freq = when {
                t < 0.25f -> 523.25 // C5
                t < 0.50f -> 659.25 // E5
                t < 0.75f -> 783.99 // G5
                else -> 1046.50      // C6
            }
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sineValue = sin(phase)
            val localT = (i % (numSamples / 4)) / (numSamples / 4f)
            val env = (1.0f - t) * (1.0f - 0.45f * localT)
            
            arr[i] = (sineValue * env * 15000).toInt().coerceIn(-32768, 32767).toShort()
        }
        arr
    }

    // Generic worker wrapper to queue audio playback to static buffers
    private fun playBuffer(buffer: ShortArray, isSfx: Boolean, localVolume: Float = 1.0f) {
        if (isSfx && !soundOn) return
        val sfxMultiplier = if (isSfx) sfxVolume else musicVolume
        val finalVol = masterVolume * sfxMultiplier * localVolume
        if (finalVol <= 0.01f) return

        sfxExecutor.execute {
            try {
                val size = buffer.size
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, size)
                track.setVolume(finalVol)
                track.play()
                
                val durationMs = (size * 1000L) / SAMPLE_RATE
                Thread.sleep(durationMs + 50L)
                
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.e("GameAudioSynth", "Error during playing buffer", e)
            }
        }
    }

    // SFX Triggers
    fun playPistol() = playBuffer(pistolBuffer, true)
    fun playSMG() = playBuffer(smgBuffer, true)
    fun playShotgun() = playBuffer(shotgunBuffer, true)
    fun playMinigun() = playBuffer(minigunBuffer, true)
    fun playLaser() = playBuffer(laserBuffer, true)

    fun playZombieGrowl() = playBuffer(zombieGrowlBuffer, true, 0.42f)
    fun playZombieHit() = playBuffer(zombieHitBuffer, true, 0.35f)
    fun playZombieDeath() = playBuffer(zombieDeathBuffer, true, 0.45f)

    fun playCoin() = playBuffer(coinBuffer, true)
    fun playUpgrade() = playBuffer(upgradeBuffer, true)

    // Background Soundtrack Procedural generator
    fun startMusic() {
        if (musicJob != null) return
        
        musicJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, 
                    AudioFormat.CHANNEL_OUT_MONO, 
                    AudioFormat.ENCODING_PCM_16BIT
                ) * 2
                
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
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
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                musicTrack = track
                track.play()

                // Ambient progression chords in A natural minor key (highly atmospheric)
                val chords = listOf(
                    listOf(55.0, 110.0, 220.0, 261.63, 329.63),   // Am
                    listOf(43.65, 87.31, 174.61, 261.63, 349.23),  // F
                    listOf(58.27, 116.54, 233.08, 293.66, 349.23), // Dm
                    listOf(41.20, 82.41, 164.81, 246.94, 329.63)   // Em
                )
                
                val melodyNotes = listOf(440.0, 493.88, 523.25, 587.33, 659.25, 783.99, 0.0)
                
                var chordIndex = 0
                var melodyStep = 0
                
                val beatSamples = (SAMPLE_RATE * 0.75).toInt() // 750ms beat speed (80 BPM)
                val audioBuffer = ShortArray(beatSamples)
                
                var basePhase0 = 0.0
                var basePhase1 = 0.0
                var basePhase2 = 0.0
                var melodyPhase = 0.0
                
                while (isActive) {
                    val currentChord = chords[chordIndex]
                    val finalVol = masterVolume * musicVolume
                    
                    val melodyFreq = if (Random.nextFloat() > 0.45f) {
                        melodyNotes[Random.nextInt(melodyNotes.size)]
                    } else 0.0
                    
                    for (i in 0 until beatSamples) {
                        val t = i.toDouble() / beatSamples
                        val chordEnv = 1.0 - t * 0.45
                        
                        basePhase0 += 2.0 * PI * currentChord[0] / SAMPLE_RATE
                        val bass = sin(basePhase0) * 0.35
                        
                        basePhase1 += 2.0 * PI * currentChord[1] / SAMPLE_RATE
                        val midVal = sin(basePhase1) * 0.20
                        
                        basePhase2 += 2.0 * PI * currentChord[2] / SAMPLE_RATE
                        val highVal = sin(basePhase2) * 0.12
                        
                        var melodyVal = 0.0
                        if (melodyFreq > 10.0) {
                            melodyPhase += 2.0 * PI * melodyFreq / SAMPLE_RATE
                            val rawSine = sin(melodyPhase)
                            val triangle = 2.0 * Math.abs(rawSine) - 1.0
                            val melEnv = if (t < 0.1) t / 0.1 else (1.0 - t) * (1.0 - t)
                            melodyVal = triangle * melEnv * 0.25
                        }
                        
                        val combined = (bass + midVal + highVal + melodyVal) * chordEnv
                        val masterFactor = if (musicOn) finalVol else 0f
                        
                        audioBuffer[i] = (combined * masterFactor * 18000).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    
                    if (isActive) {
                        track.write(audioBuffer, 0, beatSamples)
                    }
                    
                    melodyStep++
                    if (melodyStep % 4 == 0) {
                        chordIndex = (chordIndex + 1) % chords.size
                    }
                }
            } catch (e: Exception) {
                Log.e("GameAudioSynth", "Music streamer session aborted", e)
            } finally {
                try {
                    musicTrack?.stop()
                    musicTrack?.release()
                    musicTrack = null
                } catch (e: Exception) {}
            }
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
        try {
            musicTrack?.stop()
            musicTrack?.release()
            musicTrack = null
        } catch (e: Exception) {}
    }
}
