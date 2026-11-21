@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.td.ApiConfig
import com.flabuis.tgmusic.td.Td
import com.flabuis.tgmusic.td.TdException
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Composable
fun LoadingScreen(text: String = "Загрузка…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Common layout for every login step. */
@Composable
private fun AuthForm(
    title: String,
    hint: String,
    label: String,
    keyboard: KeyboardType,
    button: String,
    password: Boolean = false,
    initial: String = "",
    onSubmit: suspend (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Note, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; error = null },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation()
                                   else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        onSubmit(value.trim())
                    } catch (e: TdException) {
                        error = humanError(e)
                    } catch (e: Exception) {
                        error = e.message ?: "Ошибка"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && value.isNotBlank(),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(button, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

private fun humanError(e: TdException): String = when {
    e.error.message.contains("PHONE_NUMBER_INVALID") -> "Неправильный номер"
    e.error.message.contains("PHONE_CODE_INVALID") -> "Неправильный код"
    e.error.message.contains("PHONE_CODE_EXPIRED") -> "Код устарел, запроси новый"
    e.error.message.contains("PASSWORD_HASH_INVALID") -> "Неправильный пароль"
    e.error.message.contains("API_ID_INVALID") -> "api_id или api_hash неправильные"
    e.error.message.contains("FLOOD") -> "Слишком много попыток, подожди"
    else -> e.error.message
}

@Composable
fun ApiKeysScreen() {
    var id by remember { mutableStateOf("") }
    if (id.isEmpty()) {
        AuthForm(
            title = "Ключи Telegram API",
            hint = "Их нет в сборке. Возьми на my.telegram.org → API development tools.\nСначала api_id (цифры).",
            label = "api_id",
            keyboard = KeyboardType.Number,
            button = "Дальше",
        ) { value ->
            value.toIntOrNull() ?: throw IllegalArgumentException("api_id — это только цифры")
            id = value
        }
    } else {
        AuthForm(
            title = "api_hash",
            hint = "Теперь api_hash (длинная строка из букв и цифр)",
            label = "api_hash",
            keyboard = KeyboardType.Ascii,
            button = "Сохранить",
        ) { hash ->
            if (hash.length < 20) throw IllegalArgumentException("Слишком короткий api_hash")
            ApiConfig.save(id.toInt(), hash)
        }
    }
}

@Composable
fun PhoneScreen() = AuthForm(
    title = "Вход в Telegram",
    hint = "Номер в международном формате, например +7 777 123 45 67",
    label = "Номер телефона",
    keyboard = KeyboardType.Phone,
    button = "Получить код",
    initial = "+7",
) { phone ->
    Td.send(TdApi.SetAuthenticationPhoneNumber(phone, TdApi.PhoneNumberAuthenticationSettings()))
}

@Composable
fun CodeScreen(state: TdApi.AuthorizationStateWaitCode) {
    val where = when (state.codeInfo.type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> "Код пришёл в Telegram — открой свой клиент"
        is TdApi.AuthenticationCodeTypeSms -> "Код пришёл по SMS"
        else -> "Введи код подтверждения"
    }
    AuthForm(
        title = "Код",
        hint = where,
        label = "Код",
        keyboard = KeyboardType.Number,
        button = "Войти",
    ) { code ->
        Td.send(TdApi.CheckAuthenticationCode(code))
    }
}

@Composable
fun PasswordScreen(state: TdApi.AuthorizationStateWaitPassword) = AuthForm(
    title = "Облачный пароль",
    hint = if (state.passwordHint.isNotEmpty()) "Подсказка: ${state.passwordHint}"
           else "У тебя включена двухэтапная проверка",
    label = "Пароль",
    keyboard = KeyboardType.Password,
    button = "Войти",
    password = true,
) { pass ->
    Td.send(TdApi.CheckAuthenticationPassword(pass))
}

@Composable
fun EmailScreen() = AuthForm(
    title = "Почта",
    hint = "Telegram просит привязать почту для входа",
    label = "Email",
    keyboard = KeyboardType.Email,
    button = "Дальше",
) { email ->
    Td.send(TdApi.SetAuthenticationEmailAddress(email))
}

@Composable
fun EmailCodeScreen() = AuthForm(
    title = "Код с почты",
    hint = "Введи код, который пришёл на почту",
    label = "Код",
    keyboard = KeyboardType.Number,
    button = "Войти",
) { code ->
    Td.send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code)))
}

@Composable
fun MessageScreen(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}
