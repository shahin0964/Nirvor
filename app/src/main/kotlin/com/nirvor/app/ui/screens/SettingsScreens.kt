package com.nirvor.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C

// Colours for the settings icons, taken from the home screen's own palette.
private val SkyA = Color(0xFF5BBEF8)
private val SkyB = Color(0xFF2F8FDB)
private val SlateA = Color(0xFF6E86A6)
private val SlateB = Color(0xFF4A6182)
private val DlA = Color(0xFF9B7BF7)
private val DlB = Color(0xFF6D4AE0)

/*
 * Settings structure
 *
 *   Side bar
 *   ├── সেটিংস
 *   │   ├── অ্যাকাউন্ট → প্রোফাইল (নাম ও ছবি)
 *   │   ├── অ্যাপ      → আবহাওয়া (লোকেশন, আপডেট)
 *   │   │              → পারমিশন (নোটিফিকেশন, অ্যালার্ম, লোকেশন, ব্যাটারি, স্টোরেজ*, ফোনের অ্যাপ সেটিংস)
 *   │   │              → ডাউনলোড (সেভের জায়গা, গতি, Wi-Fi, নোটিফিকেশন, ইঞ্জিন)
 *   │   └── অন্যান্য   → অ্যাপ সম্পর্কে
 *   └── অ্যাপ সম্পর্কে
 */

// ---------------------------------------------------------------- Settings (main list)

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onWeather: () -> Unit,
    onPermissions: () -> Unit,
    onDownloads: () -> Unit,
    onAbout: () -> Unit,
    onUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val name by vm.name.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val s = rememberSettings(vm)

    PageScaffold("Settings", "সেটিংস", onBack = onBack, bottomPadding = bottomPadding) {
        item { SectionLabel("অ্যাকাউন্ট") }
        item {
            NavRow(
                Glyph.PersonFill, C.PurpleA, C.PurpleB,
                "প্রোফাইল",
                if (name.isBlank()) "নাম ও ছবি যোগ করুন" else "$name · নাম ও ছবি",
                onProfile,
            )
        }

        item { SectionLabel("অ্যাপ") }
        item {
            NavRow(
                Glyph.Sun, SkyA, SkyB,
                "আবহাওয়া",
                (city?.name ?: "অটো লোকেশন") + " · " + weather.temp + " " + weather.desc,
                onWeather,
            )
        }
        item {
            NavRow(
                Glyph.Shield, C.OrangeIconA, C.OrangeIconB,
                "পারমিশন",
                "${bnDigits(s.total)}টার মধ্যে ${bnDigits(s.onCount)}টা চালু",
                onPermissions,
            )
        }
        item {
            NavRow(
                Glyph.Download, DlA, DlB,
                "ডাউনলোড",
                "সেভের জায়গা, গতি, Wi-Fi, ইঞ্জিন আপডেট",
                onDownloads,
            )
        }

        item { SectionLabel("অন্যান্য") }
        item {
            NavRow(
                Glyph.Refresh, Color(0xFF2563EB), Color(0xFF1D4ED8),
                "অ্যাপ আপডেট (OTA)",
                "নতুন আপডেট ডাউনলোড ও রিলিজ নোট",
                onUpdate,
            )
        }
        item {
            NavRow(
                Glyph.Info, C.TaskGreenA, C.TaskGreenB,
                "অ্যাপ সম্পর্কে",
                "ভার্সন " + appVersion(context),
                onAbout,
            )
        }
    }
}

// ---------------------------------------------------------------- Settings → পারমিশন

@Composable
fun PermissionsScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = rememberSettings(vm)

    PageScaffold("Permission", "পারমিশন", onBack = onBack, bottomPadding = bottomPadding) {
        item {
            RText(
                "Nirvor ঠিকমতো কাজ করতে এই অনুমতিগুলো লাগে। কোনোটা বন্ধ থাকলে চাপ দিয়ে চালু করুন।",
                19,
                FontWeight.Normal,
                C.StatLabelBn,
                Modifier.padding(start = 6.r, end = 6.r, top = 4.r),
                maxLines = 3,
                lineHeight = 28,
            )
        }
        item {
            PermissionRow(
                Glyph.Bell, C.PurpleA, C.PurpleB,
                "নোটিফিকেশন",
                "রিমাইন্ডারের সময় নোটিফিকেশন দেখাতে",
                s.notificationsOn, s.fixNotifications,
            )
        }
        item {
            PermissionRow(
                Glyph.ClockHands, C.OrangeA, C.OrangeB,
                "সঠিক সময়ে রিমাইন্ডার",
                "অ্যালার্ম ও রিমাইন্ডার, যাতে ঠিক সময়ে বাজে",
                s.exactAlarmsOn, s.fixExactAlarms,
            )
        }
        item {
            PermissionRow(
                Glyph.Pin, SkyA, SkyB,
                "লোকেশন",
                "আবহাওয়া আর সূর্যোদয়-সূর্যাস্তের সময় জানতে (আনুমানিক)",
                s.locationOn, s.fixLocation,
            )
        }
        item {
            PermissionRow(
                Glyph.Bolt, C.TaskGreenA, C.TaskGreenB,
                "ব্যাটারি সীমা বন্ধ",
                "স্ক্রিন বন্ধ থাকলেও ডাউনলোড আর রিমাইন্ডার যেন না থামে",
                s.batteryOn, s.fixBattery,
            )
        }
        if (s.storageNeeded) {
            item {
                PermissionRow(
                    Glyph.Folder, C.NotesIconA, C.NotesIconB,
                    "স্টোরেজ",
                    "ডাউনলোড করা ফাইল Download/Nirvor ফোল্ডারে রাখতে",
                    s.storageOn, s.fixStorage,
                )
            }
        }

        item { SectionLabel("আরও") }
        item {
            NavRow(
                Glyph.Gear, SlateA, SlateB,
                "ফোনের অ্যাপ সেটিংস",
                "সব পারমিশন দেখতে বা বন্ধ করতে",
            ) { openAppSettings(context) }
        }
    }
}

// ---------------------------------------------------------------- Settings → আবহাওয়া

@Composable
fun WeatherSettingsScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = rememberSettings(vm)
    val city by vm.city.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    PageScaffold("আবহাওয়া", "লোকেশন ও আপডেট", onBack = onBack, bottomPadding = bottomPadding) {
        item {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Glyph.Sun, SkyA, SkyB, size = 76)
                    Spacer(Modifier.width(22.r))
                    Column(Modifier.weight(1f)) {
                        RText(weather.temp, 40, FontWeight.Bold, C.Ink)
                        Spacer(Modifier.height(2.r))
                        RText(weather.desc, 20, FontWeight.Normal, C.StatLabel)
                        Spacer(Modifier.height(4.r))
                        RText(city?.name ?: "ফোনের লোকেশন থেকে", 17, FontWeight.Normal, C.Hint)
                    }
                }
            }
        }

        item { SectionLabel("লোকেশন") }
        item {
            ChoiceRow(
                selected = city == null,
                title = "অটো (ফোনের লোকেশন)",
                subtitle = "ফোনের GPS / নেটওয়ার্ক থেকে নিজে নিজে",
            ) {
                if (city != null) vm.setCity(null)
                if (!s.locationOn) s.fixLocation()
            }
        }
        item {
            ChoiceRow(
                selected = city != null,
                title = "শহর বেছে নিন",
                subtitle = city?.name ?: "নাম লিখে খুঁজুন (যেমন: Dhaka)",
                onClick = s.openCityPicker,
            )
        }

        item { SectionLabel("আপডেট") }
        item {
            NavRow(
                Glyph.Sun, C.TaskGreenA, C.TaskGreenB,
                "এখনই আপডেট করুন",
                "এমনিতে প্রতি ৩০ মিনিটে নিজে থেকেই আপডেট হয়",
            ) {
                vm.refreshWeather(force = true)
                Toast.makeText(context, "আবহাওয়া আপডেট হচ্ছে…", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            RText(
                "আবহাওয়ার তথ্য: Open-Meteo",
                17,
                FontWeight.Normal,
                C.Hint,
                Modifier.fillMaxWidth().padding(top = 18.r),
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.r)
                    .border(2.4.r, if (selected) Color(0xFF0BA765) else C.HabOpen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(16.r).background(Color(0xFF0BA765), CircleShape))
            }
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText(title, 22, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(subtitle, 17, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- Settings → অ্যাপ সম্পর্কে

@Composable
fun AboutScreen(bottomPadding: Dp, onBack: () -> Unit, onUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val version = appVersion(context)

    PageScaffold("অ্যাপ সম্পর্কে", "Nirvor", onBack = onBack, bottomPadding = bottomPadding) {
        item {
            ListCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBubble(Glyph.Check, C.FabA, C.FabB, size = 110)
                    Spacer(Modifier.height(16.r))
                    RText("Nirvor", 34, FontWeight.Bold, C.Ink)
                    RText("নির্ভর", 20, FontWeight.Normal, C.StatLabelBn)
                    Spacer(Modifier.height(6.r))
                    RText("ভার্সন $version", 18, FontWeight.Normal, C.Hint)
                    Spacer(Modifier.height(14.r))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFDBEAFE))
                            .clickable(onClick = onUpdate)
                            .padding(horizontal = 20.r, vertical = 9.r),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Refresh, Color(0xFF1D4ED8), Modifier.size(18.r), strokeWidth = 2f)
                            Spacer(Modifier.width(8.r))
                            RText("আপডেট চেক করুন (OTA)", 15, FontWeight.SemiBold, Color(0xFF1D4ED8))
                        }
                    }
                }
            }
        }
        item {
            ListCard {
                AboutLine("অ্যাপ", "Nirvor (নির্ভর)")
                AboutLine("ভার্সন", version)
                AboutLine("আবহাওয়ার তথ্য", "Open-Meteo")
            }
        }
        item { SectionLabel("আপনার তথ্য") }
        item {
            ListCard {
                RText(
                    "Nirvor আপনার কাজ, খরচ, হ্যাবিট বা নোট কোথাও পাঠায় না, সব ফোনেই সেভ থাকে। " +
                        "আবহাওয়া আনতে শুধু আনুমানিক লোকেশন Open-Meteo-তে যায়।",
                    18,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 5,
                    lineHeight = 28,
                )
            }
        }
    }
}

@Composable
internal fun AboutLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.r), verticalAlignment = Alignment.CenterVertically) {
        RText(label, 19, FontWeight.Normal, C.StatLabelBn, Modifier.weight(1f))
        RText(value, 20, FontWeight.Medium, C.Ink)
    }
}
