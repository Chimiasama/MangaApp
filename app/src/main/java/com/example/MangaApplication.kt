package com.example

import android.app.Application
import com.example.data.network.MangaDexChromiumTransport

class MangaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Workers can fetch chapters before an Activity is created. The engine itself stays lazy.
        MangaDexChromiumTransport.initialize(this)
    }
}
