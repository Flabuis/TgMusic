package com.flabuis.tgmusic

import android.app.Application
import com.flabuis.tgmusic.anime.AnimeHistory
import com.flabuis.tgmusic.data.Downloads
import com.flabuis.tgmusic.data.Library
import com.flabuis.tgmusic.data.Lyrics
import com.flabuis.tgmusic.data.Settings
import kotlinx.coroutines.MainScope
import com.flabuis.tgmusic.td.ApiConfig
import com.flabuis.tgmusic.td.Td

class TgMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiConfig.init(this)
        Library.init(this)
        AnimeHistory.init(this)
        Settings.init(this)
        Td.init(this)
        Downloads.observe(MainScope())
        Lyrics.start(this, MainScope())
    }
}
