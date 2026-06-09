package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.GameStatsRepository

class MyApplication : Application() {
    lateinit var repository: GameStatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val database = AppDatabase.getDatabase(this)
        repository = GameStatsRepository(database.gameStatsDao())
    }

    companion object {
        lateinit var instance: MyApplication
            private set
    }
}
