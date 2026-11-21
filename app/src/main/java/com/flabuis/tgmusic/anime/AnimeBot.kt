package com.flabuis.tgmusic.anime

import com.flabuis.tgmusic.td.Td
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

/** One search result from the bot's inline search. */
data class AnimeResult(
    val queryId: Long,
    val resultId: String,
    val title: String,
    val description: String,
    val thumbFileId: Int?,
    val rating: String?,
    val year: String?,
    val episodes: Int?,
)

/** A button under a bot message (only callback buttons can be pressed from here). */
class BotButton(val text: String, val data: ByteArray?) {
    /** Text without emoji and spaces, e.g. "🎞 12" -> "12". */
    val clean: String get() = text.replace(Regex("[^\\p{L}\\p{N}/.:+-]"), "").trim()
    fun has(vararg parts: String) = parts.any { text.contains(it, ignoreCase = true) }
}

/** A message from the bot: text, maybe a photo or a video, and its buttons. */
class BotMessage(
    val id: Long,
    val text: String,
    val photoFileId: Int?,
    val video: TdApi.Video?,
    val buttons: List<List<BotButton>>,
) {
    val allButtons: List<BotButton> get() = buttons.flatten()

    /** The bot asks to pick a dubbing studio first (buttons like "🟢 1-12 🎙 NextVoice 👁 2 тыс. 📽 1080p"). */
    val voiceOptions: List<VoiceOption>
        get() = allButtons.filter { it.data != null && it.has("🎙") }.map { VoiceOption.parse(it) }
    fun find(vararg parts: String): BotButton? = allButtons.firstOrNull { b -> b.data != null && b.has(*parts) }
}

/** One dubbing studio from the bot's list. */
class VoiceOption(val name: String, val episodes: String?, val views: String?, val qualities: String?, val button: BotButton) {
    companion object {
        fun parse(b: BotButton): VoiceOption {
            val t = b.text
            val name = t.substringAfter("🎙").substringBefore("👁").trim().ifBlank { t }
            val episodes = Regex("""(\d+\s*-\s*\d+|\d+)""").find(t.substringBefore("🎙"))?.value
            val views = t.substringAfter("👁", "").substringBefore("📽").trim().ifBlank { null }
            val qualities = Regex("""(\d{3,4}p[,\s\dp]*)""").find(t.substringAfter("📽", ""))?.value?.trim(' ', ',')
            return VoiceOption(name, episodes, views, qualities, b)
        }
    }
}

/** What's on screen while watching: the episode video plus the controls the bot offers. */
class EpisodeState(val msg: BotMessage) {
    val video: TdApi.Video? get() = msg.video
    val episode: Int? = Regex("""Серия\s+(\d+)\s+из\s+(\d+)""").find(msg.text)?.groupValues?.get(1)?.toIntOrNull()
    val total: Int? = Regex("""Серия\s+(\d+)\s+из\s+(\d+)""").find(msg.text)?.groupValues?.get(2)?.toIntOrNull()
    val voice: String? = Regex("""Озвучка:\s*(.+)""").find(msg.text)?.groupValues?.get(1)?.cleanLabel()
    val quality: String? = Regex("""Качество:\s*(\S+)""").find(msg.text)?.groupValues?.get(1)

    /** Opening start..end in seconds, from "Опенинг: 00;54 - 02:25". */
    val opening: IntRange? = Regex("""Опенинг:.*?(\d{1,2})[:;](\d{2}).*?(\d{1,2})[:;](\d{2})""").find(msg.text)?.let {
        val (m1, s1, m2, s2) = it.destructured
        (m1.toInt() * 60 + s1.toInt())..(m2.toInt() * 60 + s2.toInt())
    }

    /** Episode buttons on the current page: number -> button (current one has 🔵). */
    val episodes: List<Pair<Int, BotButton>> = msg.allButtons.mapNotNull { b ->
        if (b.has("/", "➡", "⬅", "⏩", "⏪", "✅", "🔄", "💬", "📸", "🎥")) return@mapNotNull null
        if (b.text.any { it.isLetter() }) return@mapNotNull null
        val n = b.clean.toIntOrNull() ?: return@mapNotNull null
        n to b
    }
    val currentEpisodeButton: Int? = msg.allButtons.firstOrNull { it.has("🔵") }?.clean?.toIntOrNull()
    val nextPage: BotButton? = msg.allButtons.firstOrNull { it.has("➡") && it.data != null }
    val prevPage: BotButton? = msg.allButtons.firstOrNull { it.has("⬅") && it.data != null }
    val nextEpisode: BotButton? = msg.allButtons.firstOrNull { it.has("⏩") && it.data != null }
    val pageLabel: String? = msg.allButtons.firstOrNull { Regex("""\d+\s*/\s*\d+""").containsMatchIn(it.text) }?.clean

    /** Quality buttons: label like "720", selected one has ✅. */
    val qualities: List<Triple<String, BotButton, Boolean>> = msg.allButtons.mapNotNull { b ->
        if (!b.has("✅", "🔄")) return@mapNotNull null
        val q = Regex("""(2160|1440|1080|720|480|360|240)""").find(b.text)?.value ?: return@mapNotNull null
        Triple(q, b, b.has("✅"))
    }
    val changeVoice: BotButton? = msg.find("озвучк")
}

private fun String.cleanLabel() = replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()

/**
 * Talks to @AniMetkai the same way you do by hand: inline search, sending the chosen
 * result to the bot, pressing its buttons and reading what it answers.
 */
object AnimeBot {
    const val USERNAME = "AniMetkai"
    private var chatId = 0L
    private var botUserId = 0L

    /** Last raw answer from the bot, for the "what did the bot say" helper screen. */
    @Volatile var lastRaw: BotMessage? = null
        private set

    private suspend fun ensure() {
        if (chatId != 0L) return
        val chat = Td.send(TdApi.SearchPublicChat(USERNAME))
        val type = chat.type as? TdApi.ChatTypePrivate ?: error("@$USERNAME не бот")
        chatId = chat.id
        botUserId = type.userId
    }

    suspend fun search(query: String): List<AnimeResult> {
        ensure()
        val r = Td.send(TdApi.GetInlineQueryResults(botUserId, chatId, null, query, ""))
        return r.results.mapNotNull { res ->
            val (id, title, desc, thumb) = when (res) {
                is TdApi.InlineQueryResultArticle -> listOf(res.id, res.title, res.description, res.thumbnail?.file?.id)
                is TdApi.InlineQueryResultPhoto -> listOf(res.id, res.title, res.description, res.photo.sizes.maxByOrNull { it.width }?.photo?.id)
                is TdApi.InlineQueryResultVideo -> listOf(res.id, res.title, res.description, res.video.thumbnail?.file?.id)
                is TdApi.InlineQueryResultDocument -> listOf(res.id, res.document.fileName, res.description, res.document.thumbnail?.file?.id)
                else -> return@mapNotNull null
            }
            val t = (title as String).cleanLabel()
            if (t.isBlank() || t.startsWith("Найдено")) return@mapNotNull null
            val d = desc as String
            AnimeResult(
                queryId = r.inlineQueryId,
                resultId = id as String,
                title = t,
                description = d,
                thumbFileId = thumb as Int?,
                rating = Regex("""(\d+[.,]\d+)""").find(d)?.value,
                year = Regex("""\b(19|20)\d{2}\b""").find(d)?.value,
                episodes = Regex("""Серий:\s*(\d+)""").find(d)?.groupValues?.get(1)?.toIntOrNull(),
            )
        }
    }

    /** Opens an anime: the bot answers with its card (poster, info, "Смотреть" button). */
    suspend fun open(result: AnimeResult): BotMessage {
        ensure()
        return awaitBot(
            accept = { it.video != null || it.find("Смотреть") != null },
        ) {
            Td.send(TdApi.SendInlineQueryResultMessage(chatId, null, null, null, result.queryId, result.resultId, false))
        }
    }

    /**
     * Presses "Смотреть" on the card. The bot answers with the episode video, or first asks
     * which dubbing to use (then the answer has [BotMessage.voiceOptions]).
     */
    suspend fun watch(card: BotMessage): BotMessage {
        val button = card.find("Смотреть") ?: error("Нет кнопки «Смотреть»")
        return press(card.id, button) { it.video != null || it.voiceOptions.isNotEmpty() }
    }

    /** Picks a dubbing from the bot's list and waits for the video. */
    suspend fun chooseVoice(list: BotMessage, option: VoiceOption): EpisodeState =
        EpisodeState(press(list.id, option.button) { it.video != null })

    /**
     * Presses a button and waits for the bot's answer: the same message edited, or a new one.
     * [accept] decides which answer counts (e.g. "has a video").
     */
    suspend fun press(messageId: Long, button: BotButton, accept: (BotMessage) -> Boolean = { true }): BotMessage {
        val data = button.data ?: error("Эту кнопку нельзя нажать отсюда")
        return awaitBot(accept, editedId = messageId) {
            // The bot's popup answer doesn't matter; it may also time out while the bot works.
            try {
                Td.send(TdApi.GetCallbackQueryAnswer(chatId, messageId, TdApi.CallbackQueryPayloadData(data)))
            } catch (_: Exception) {
            }
        }
    }

    suspend fun message(id: Long): BotMessage = parse(Td.send(TdApi.GetMessage(chatId, id)))

    /** Runs [action] and returns the first bot message (new or [editedId] edited) that [accept]s. */
    private suspend fun awaitBot(
        accept: (BotMessage) -> Boolean,
        editedId: Long? = null,
        action: suspend () -> Unit,
    ): BotMessage = coroutineScope {
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(30_000) {
                Td.messageEvents.filter { ev ->
                    when (ev) {
                        is TdApi.UpdateNewMessage -> ev.message.chatId == chatId && !ev.message.isOutgoing
                        is TdApi.UpdateMessageContent -> ev.chatId == chatId
                        is TdApi.UpdateMessageEdited -> ev.chatId == chatId && (editedId == null || ev.messageId == editedId)
                        else -> false
                    }
                }.let { flow ->
                    var found: BotMessage? = null
                    flow.first { ev ->
                        val id = when (ev) {
                            is TdApi.UpdateNewMessage -> ev.message.id
                            is TdApi.UpdateMessageContent -> ev.messageId
                            is TdApi.UpdateMessageEdited -> ev.messageId
                            else -> 0L
                        }
                        // Content and buttons can arrive as separate updates: let both land, then read it whole.
                        delay(350)
                        val msg = try { message(id) } catch (e: Exception) { null }
                        if (msg != null) lastRaw = msg
                        if (msg != null && accept(msg)) { found = msg; true } else false
                    }
                    found
                }
            }
        }
        launch { action() }
        answer.await() ?: error("Бот не ответил. Проверь, что он работает в Telegram.")
    }

    private fun parse(m: TdApi.Message): BotMessage {
        val (text, photo, video) = when (val c = m.content) {
            is TdApi.MessageText -> Triple(c.text.text, null, null)
            is TdApi.MessagePhoto -> Triple(c.caption.text, c.photo.sizes.maxByOrNull { it.width }?.photo?.id, null)
            is TdApi.MessageVideo -> Triple(c.caption.text, null, c.video)
            is TdApi.MessageAnimation -> Triple(c.caption.text, null, null)
            is TdApi.MessageDocument -> Triple(c.caption.text, null, null)
            else -> Triple("", null, null)
        }
        val rows = (m.replyMarkup as? TdApi.ReplyMarkupInlineKeyboard)?.rows.orEmpty().map { row ->
            row.map { b -> BotButton(b.text, (b.type as? TdApi.InlineKeyboardButtonTypeCallback)?.data) }
        }
        return BotMessage(m.id, text, photo, video, rows)
    }
}
