package com.example

import org.junit.Test
import android.media.AudioTrack

class AudioApiTest {
    @Test
    fun testAudioTrackBuilderMethods() {
        val methods = AudioTrack.Builder::class.java.declaredMethods
        methods.forEach { println("AudioTrackBuilderMethod:" + it.name) }
    }
}
