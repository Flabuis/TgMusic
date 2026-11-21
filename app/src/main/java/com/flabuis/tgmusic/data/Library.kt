package com.flabuis.tgmusic.data

import android.content.Context
import com.flabuis.tgmusic.td.Td
import com.flabuis.tgmusic.td.TdException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

data class Channel(val id: Long, val title: String, val isOwner: Boolean, val isGroup: Boolean = false)

data class Track(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val artist: String,
    val duration: Int,
    val size: Long,
    /** Small album cover / document thumbnail (TDLib file id), if any. */
    val coverSmall: Int? = null,
    /** Biggest cover available, for the player screen and notification. */
    val coverLarge: Int? = null,
    /** Tiny blurred preview shown while the cover loads. */
    val mini: ByteArray? = null,
) {
    /** Unique id used by the player: chatId_messageId_fileId */
    val mediaId: String get() = "${chatId}_${messageId}_$fileId"
}

/** Which channels are playlists, and loading tracks from them. */
object Library {
    private const val PREFS = "library"
    private lateinit var ctx: Context

    private val _selected = MutableStateFlow<List<Channel>>(emptyList())
    /** Channels the user picked as playlists. */
    val selected: StateFlow<List<Channel>> = _selected

    private val trackCache = ConcurrentHashMap<Long, List<Track>>()

    fun init(context: Context) {
        ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet("channels", emptySet()) ?: emptySet()
        // stored as "id|owner|title"
        _selected.value = raw.mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            Channel(id, parts.getOrNull(2) ?: "", parts.getOrNull(1) == "1")
        }.sortedBy { it.title.lowercase() }
    }

    fun setSelected(channels: List<Channel>) {
        val sorted = channels.sortedBy { it.title.lowercase() }
        _selected.value = sorted
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("channels", sorted.map { "${it.id}|${if (it.isOwner) 1 else 0}|${it.title}" }.toSet())
            .apply()
    }

    fun clear() {
        trackCache.clear()
        setSelected(emptyList())
    }

    /** All channels and groups where the user is the owner or an admin. Owners first. */
    suspend fun loadMyChannels(): List<Channel> {
        loadAllChats(TdApi.ChatListMain())
        loadAllChats(TdApi.ChatListArchive())
        val ids = LinkedHashSet<Long>()
        ids += Td.send(TdApi.GetChats(TdApi.ChatListMain(), 2000)).chatIds.toList()
        ids += Td.send(TdApi.GetChats(TdApi.ChatListArchive(), 2000)).chatIds.toList()

        val result = mutableListOf<Channel>()
        for (id in ids) {
            val chat = try { Td.send(TdApi.GetChat(id)) } catch (e: TdException) { continue }
            // Channels, supergroups and small (basic) groups where the user is owner or admin.
            val status: TdApi.ChatMemberStatus = when (val type = chat.type) {
                is TdApi.ChatTypeSupergroup ->
                    try { Td.send(TdApi.GetSupergroup(type.supergroupId)).status } catch (e: TdException) { continue }
                is TdApi.ChatTypeBasicGroup -> {
                    val bg = try { Td.send(TdApi.GetBasicGroup(type.basicGroupId)) } catch (e: TdException) { continue }
                    if (!bg.isActive) continue // upgraded to a supergroup, that one is listed instead
                    bg.status
                }
                else -> continue
            }
            val isGroup = chat.type !is TdApi.ChatTypeSupergroup ||
                !(chat.type as TdApi.ChatTypeSupergroup).isChannel
            when (status) {
                is TdApi.ChatMemberStatusCreator -> result += Channel(chat.id, chat.title, true, isGroup)
                is TdApi.ChatMemberStatusAdministrator -> result += Channel(chat.id, chat.title, false, isGroup)
                else -> {}
            }
        }
        return result.sortedWith(compareBy({ !it.isOwner }, { it.title.lowercase() }))
    }

    private suspend fun loadAllChats(list: TdApi.ChatList) {
        repeat(30) {
            try {
                Td.send(TdApi.LoadChats(list, 200))
            } catch (e: TdException) {
                return // 404 = everything is loaded
            }
        }
    }

    fun cachedTracks(chatId: Long): List<Track>? = trackCache[chatId]

    /** All audio in a channel, newest first. Includes mp3/flac etc. sent as files. */
    suspend fun loadTracks(chatId: Long, force: Boolean = false): List<Track> {
        if (!force) trackCache[chatId]?.let { return it }
        val tracks = mutableListOf<Track>()
        collect(chatId, TdApi.SearchMessagesFilterAudio(), tracks)
        collect(chatId, TdApi.SearchMessagesFilterDocument(), tracks)
        val result = applyOrder(chatId, tracks.distinctBy { it.fileId }.sortedByDescending { it.messageId })
        trackCache[chatId] = result
        return result
    }

    // ----- custom order (drag & drop) -----

    private fun orderPrefs() = ctx.getSharedPreferences("order", Context.MODE_PRIVATE)

    /** Saved order first; tracks added to the chat later go on top. */
    private fun applyOrder(chatId: Long, tracks: List<Track>): List<Track> {
        val saved = orderPrefs().getString(chatId.toString(), null)
            ?.split(',')?.mapNotNull { it.toLongOrNull() } ?: return tracks
        val byId = tracks.associateBy { it.messageId }
        val savedSet = saved.toHashSet()
        val fresh = tracks.filter { it.messageId !in savedSet }
        return fresh + saved.mapNotNull { byId[it] }
    }

    fun saveOrder(chatId: Long, tracks: List<Track>) {
        trackCache[chatId] = tracks
        orderPrefs().edit().putString(chatId.toString(), tracks.joinToString(",") { it.messageId.toString() }).apply()
    }

    fun hasCustomOrder(chatId: Long) = orderPrefs().contains(chatId.toString())

    fun resetOrder(chatId: Long) {
        orderPrefs().edit().remove(chatId.toString()).apply()
        trackCache[chatId]?.let { list -> trackCache[chatId] = list.sortedByDescending { it.messageId } }
    }

    // ----- chat avatars -----

    private val photos = ConcurrentHashMap<Long, TdApi.ChatPhotoInfo>()
    private val noPhoto = ConcurrentHashMap.newKeySet<Long>()

    fun cachedPhoto(chatId: Long): TdApi.ChatPhotoInfo? = photos[chatId]

    suspend fun chatPhoto(chatId: Long): TdApi.ChatPhotoInfo? {
        photos[chatId]?.let { return it }
        if (chatId in noPhoto) return null
        val photo = try { Td.send(TdApi.GetChat(chatId)).photo } catch (e: TdException) { null }
        if (photo != null) photos[chatId] = photo else noPhoto += chatId
        return photo
    }

    private suspend fun collect(chatId: Long, filter: TdApi.SearchMessagesFilter, out: MutableList<Track>) {
        var from = 0L
        repeat(500) { // up to 50 000 messages
            val found = Td.send(
                TdApi.SearchChatMessages(chatId, null, "", null, from, 0, 100, filter)
            )
            for (m in found.messages) toTrack(m)?.let { out += it }
            if (found.nextFromMessageId == 0L || found.messages.isEmpty()) return
            from = found.nextFromMessageId
        }
    }

    private fun toTrack(m: TdApi.Message): Track? {
        return when (val c = m.content) {
            is TdApi.MessageAudio -> {
                val a = c.audio
                Track(
                    chatId = m.chatId,
                    messageId = m.id,
                    fileId = a.audio.id,
                    title = a.title.ifBlank { stripExt(a.fileName).ifBlank { "Без названия" } },
                    artist = a.performer.ifBlank { "Неизвестен" },
                    duration = a.duration,
                    size = a.audio.size.takeIf { it > 0 } ?: a.audio.expectedSize,
                    coverSmall = a.albumCoverThumbnail?.file?.id,
                    coverLarge = (a.externalAlbumCovers.maxByOrNull { it.width * it.height }
                        ?: a.albumCoverThumbnail)?.file?.id,
                    mini = a.albumCoverMinithumbnail?.data,
                )
            }
            is TdApi.MessageDocument -> {
                val d = c.document
                if (!d.mimeType.startsWith("audio/") && !isAudioName(d.fileName)) return null
                val (artist, title) = splitName(stripExt(d.fileName))
                Track(
                    chatId = m.chatId,
                    messageId = m.id,
                    fileId = d.document.id,
                    title = title,
                    artist = artist,
                    duration = 0,
                    size = d.document.size.takeIf { it > 0 } ?: d.document.expectedSize,
                    coverSmall = d.thumbnail?.file?.id,
                    coverLarge = d.thumbnail?.file?.id,
                    mini = d.minithumbnail?.data,
                )
            }
            else -> null
        }
    }

    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma")
    private fun isAudioName(name: String) = name.substringAfterLast('.', "").lowercase() in audioExt
    private fun stripExt(name: String) = if ('.' in name) name.substringBeforeLast('.') else name

    /** "Artist - Title" -> (Artist, Title) */
    private fun splitName(name: String): Pair<String, String> {
        val idx = name.indexOf(" - ")
        return if (idx > 0) name.substring(0, idx).trim() to name.substring(idx + 3).trim()
        else "Неизвестен" to name.ifBlank { "Без названия" }
    }
}
