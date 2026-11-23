package com.nirvor.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.PlayerProfile
import com.nirvor.app.games.net.DeniedException
import com.nirvor.app.games.net.Matchmaker
import com.nirvor.app.games.net.Online
import com.nirvor.app.games.net.Session
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Finding an opponent: quick match, own room with a code, or a friend's code. */
@Composable
internal fun OnlineLobby(profile: GameProfile, onBack: () -> Unit, onReady: (Session) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf(profile.player.nick) }

    fun stop() {
        job?.cancel()
        job = null
        code = ""
    }

    fun start(label: String, block: suspend (Matchmaker) -> Session?) {
        if (job != null) return
        error = ""
        status = label
        job = scope.launch {
            try {
                val s = block(Online.matchmaker(context, profile.game, profile.player))
                if (s != null) onReady(s) else error = "এই কোডে খেলার রুম পাওয়া যায়নি"
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeniedException) {
                error = "অনলাইন সার্ভার এখনো চালু করা হয়নি"
            } catch (e: Exception) {
                error = "ইন্টারনেট সংযোগে সমস্যা, আবার চেষ্টা করুন"
            }
            job = null
            code = ""
        }
    }
    BackHandler { if (job != null) stop() else onBack() }

    ToolScaffold("অনলাইন", profile.game.title, { if (job != null) stop() else onBack() }) {
        when {
            !Online.available -> GameCard { RText("এই বিল্ডে অনলাইন খেলা নেই", 20, FontWeight.Medium, C.Ink, maxLines = 2) }
            profile.player.nick.isBlank() -> GameCard {
                RText("অনলাইনে আপনার ডাকনাম", 21, FontWeight.Bold, C.Ink)
                RText("অন্য খেলোয়াড়রা এই নাম দেখবে। আসল নাম না দিলেও চলবে।", 16, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
                Spacer(Modifier.height(12.r))
                OutlinedTextField(nick, { nick = it.take(18) }, Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.r))
                RoundButton("ঠিক আছে", TGreen, Modifier.fillMaxWidth()) {
                    if (PlayerProfile.cleanNick(nick).isNotEmpty()) profile.player.rename(nick)
                }
            }
            job != null -> GameCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(profile.game.colorB))
                    Spacer(Modifier.height(16.r))
                    RText(status, 20, FontWeight.SemiBold, C.Ink, align = TextAlign.Center, maxLines = 2)
                    if (code.isNotEmpty()) {
                        Spacer(Modifier.height(12.r))
                        RText(code, 52, FontWeight.Bold, Color(profile.game.colorB))
                        RText("এই কোড বন্ধুকে দিন", 17, FontWeight.Medium, C.StatLabelBn)
                    }
                    Spacer(Modifier.height(18.r))
                    SmallButton("বাতিল", C.Danger) { stop() }
                }
            }
            else -> {
                if (error.isNotEmpty()) GameCard { RText("⚠️ $error", 18, FontWeight.SemiBold, C.Danger, maxLines = 3) }
                RoundButton("⚡ দ্রুত ম্যাচ", TGreen, Modifier.fillMaxWidth()) { start("খেলোয়াড় খোঁজা হচ্ছে…") { it.quick() } }
                RoundButton("🏠 রুম বানান", Color(profile.game.colorB), Modifier.fillMaxWidth()) {
                    start("বন্ধুর অপেক্ষায়…") { m ->
                        val c = m.create()
                        code = c
                        m.waitForGuest(c)
                    }
                }
                GameCard {
                    RText("বন্ধুর কোড দিয়ে ঢুকুন", 20, FontWeight.Bold, C.Ink)
                    Spacer(Modifier.height(10.r))
                    OutlinedTextField(
                        typed,
                        { v -> typed = v.filter { it in '0'..'9' }.take(6) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.r))
                    SmallButton("ঢুকুন", TGreen, typed.length == 6) { start("রুমে ঢোকা হচ্ছে…") { it.join(typed) } }
                }
                RText("ডাকনাম: ${profile.player.nick}", 17, FontWeight.Medium, C.Hint)
            }
        }
    }
}
