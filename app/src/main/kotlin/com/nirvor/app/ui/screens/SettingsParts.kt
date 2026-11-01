package com.nirvor.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.reminders.Notifications
import com.nirvor.app.reminders.ReminderScheduler
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C

/** Current permission state plus the actions that fix each one. Shared by More and the side bar. */
class SettingsState(
    val notificationsOn: Boolean,
    val exactAlarmsOn: Boolean,
    val locationOn: Boolean,
    /** Downloads keep going with the screen off. */
    val batteryOn: Boolean,
    /** Only Android 9 and older need a permission to save into Download/. */
    val storageNeeded: Boolean,
    val storageOn: Boolean,
    val fixNotifications: () -> Unit,
    val fixExactAlarms: () -> Unit,
    val fixLocation: () -> Unit,
    val fixBattery: () -> Unit,
    val fixStorage: () -> Unit,
    val openCityPicker: () -> Unit,
) {
    private val all: List<Boolean>
        get() = listOfNotNull(notificationsOn, exactAlarmsOn, locationOn, batteryOn, storageOn.takeIf { storageNeeded })

    val total: Int get() = all.size
    val onCount: Int get() = all.count { it }
}

/**
 * Reads the permissions (again every time the app comes back to the front) and owns the
 * weather-city dialog. Call once per settings screen.
 */
@Composable
fun rememberSettings(vm: MainViewModel): SettingsState {
    val context = LocalContext.current
    val city by vm.city.collectAsStateWithLifecycle()
    var showCity by remember { mutableStateOf(false) }

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++
        vm.rescheduleAll()
    }
    val notifOk = remember(resumeTick) { Notifications.canPost(context) }
    val exactOk = remember(resumeTick) { ReminderScheduler.canScheduleExact(context) }
    val locationOk = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val storageNeeded = Build.VERSION.SDK_INT <= 28
    val storageOk = remember(resumeTick) {
        !storageNeeded || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        resumeTick++
        if (granted) vm.refreshWeather(force = true)
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++
    }

    if (showCity) {
        CityDialog(
            current = city?.name,
            onSearch = { vm.searchPlaces(it) },
            onPick = {
                vm.setCity(it)
                showCity = false
            },
            onDismiss = { showCity = false },
        )
    }

    return SettingsState(
        notificationsOn = notifOk,
        exactAlarmsOn = exactOk,
        locationOn = locationOk,
        batteryOn = batteryOk,
        storageNeeded = storageNeeded,
        storageOn = storageOk,
        fixNotifications = {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openAppSettings(context)
        },
        fixExactAlarms = {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    )
                } catch (e: Exception) {
                    openAppSettings(context)
                }
            }
        },
        fixLocation = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        fixBattery = {
            @Suppress("BatteryLife")
            val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
            try {
                context.startActivity(ask)
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e2: Exception) {
                    openAppSettings(context)
                }
            }
        },
        fixStorage = { storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
        openCityPicker = { showCity = true },
    )
}

/** One permission: icon, what it is for, and whether it is on. Tap to turn it on. */
@Composable
internal fun PermissionRow(
    glyph: Glyph,
    a: Color,
    b: Color,
    title: String,
    subtitle: String,
    granted: Boolean,
    onFix: () -> Unit,
) {
    ListCard(onClick = if (granted) null else onFix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(glyph, a, b)
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText(title, 22, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(subtitle, 17, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
            }
            Spacer(Modifier.width(12.r))
            StatusPill(granted)
        }
    }
}

@Composable
private fun StatusPill(on: Boolean) {
    val bg = if (on) Color(0xFFE3F6EC) else Color(0xFFFFF0E2)
    val fg = if (on) Color(0xFF0A955A) else Color(0xFFD0681A)
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 16.r, vertical = 8.r)
    ) {
        RText(if (on) "চালু" else "চালু করুন", 17, FontWeight.SemiBold, fg)
    }
}

/** Round gradient icon used on every settings row (same style as the home card icons). */
@Composable
internal fun IconBubble(glyph: Glyph, a: Color, b: Color, size: Number = 58) {
    val d = size.toFloat()
    Box(
        Modifier.size(d.r).background(Brush.linearGradient(listOf(a, b)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Color.White, Modifier.size((d * 0.55f).r), strokeWidth = 2f)
    }
}

/** A settings row that opens another page. */
@Composable
internal fun NavRow(glyph: Glyph, a: Color, b: Color, title: String, subtitle: String, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(glyph, a, b)
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText(title, 22, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(subtitle, 17, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
            }
            GlyphIcon(Glyph.ChevronRight, C.RemChevron, Modifier.size(26.r), strokeWidth = 2f)
        }
    }
}

/** 0-9 -> ০-৯ */
internal fun bnDigits(n: Int): String = n.toString().map { "০১২৩৪৫৬৭৮৯"[it - '0'] }.joinToString("")

internal fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        )
    } catch (e: Exception) {
        // No settings screen available.
    }
}

/** versionName from the installed app (e.g. "1.2.0"). */
internal fun appVersion(context: Context): String = try {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}
