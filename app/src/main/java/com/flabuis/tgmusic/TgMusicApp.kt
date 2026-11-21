package com.flabuis.tgmusic

import android.app.Application
import com.flabuis.tgmusic.data.Library
import com.flabuis.tgmusic.td.ApiConfig
import com.flabuis.tgmusic.td.Td

class TgMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiConfig.init(this)
        Library.init(this)
        Td.init(this)
    }
}
