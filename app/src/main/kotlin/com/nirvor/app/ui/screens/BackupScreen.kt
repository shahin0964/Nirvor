package com.nirvor.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nirvor.app.NirvorApplication
import com.nirvor.app.backup.AutoBackupScheduler
import com.nirvor.app.backup.BackupManager
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NirvorApplication
    val prefs = app.prefs
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Local Backup, 1: Google Drive
    var autoEnabled by remember { mutableStateOf(prefs.autoBackupEnabled) }
    var lastBackup by remember { mutableLongStateOf(prefs.lastBackupTime) }
    var recordsSummary by remember { mutableStateOf("লোড হচ্ছে...") }
    var isWorking by remember { mutableStateOf(false) }

    val singleFile = remember(lastBackup) { BackupManager.getSingleAutoBackupFile(context) }
    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }
    var confirmRestoreJson by remember { mutableStateOf<String?>(null) }

    fun refreshSummary() {
        scope.launch {
            recordsSummary = BackupManager.getRecordsSummary(context)
            lastBackup = prefs.lastBackupTime
        }
    }

    LaunchedEffect(Unit) {
        refreshSummary()
    }

    // Google Drive / SAF Save Launcher
    val saveDriveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isWorking = true
                try {
                    val json = BackupManager.exportJson(context)
                    val ok = BackupManager.writeJsonToUri(context, uri, json)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            prefs.lastBackupTime = System.currentTimeMillis()
                            lastBackup = prefs.lastBackupTime
                            Toast.makeText(context, "Google Drive-এ সফলভাবে ব্যাকআপ সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "সংরক্ষণ করা যায়নি। আবার চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                        }
                    }
                } finally {
                    isWorking = false
                }
            }
        }
    }

    // Google Drive / Storage Open/Restore Launcher
    val openDriveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = BackupManager.readJsonFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    if (json != null) {
                        confirmRestoreJson = json
                    } else {
                        Toast.makeText(context, "ফাইলটি পড়া যায়নি।", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Main Dark Modal Screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C))
            .padding(bottom = bottomPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.r),
        ) {
            // 1. Top Header with Icon, Title, Subtitle and Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.r, vertical = 20.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.r)
                        .clip(RoundedCornerShape(13.r))
                        .background(Color(0xFF064E3B)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Refresh, Color(0xFF00E676), Modifier.size(24.r), strokeWidth = 2.4f)
                }

                Spacer(Modifier.width(14.r))

                Column(modifier = Modifier.weight(1f)) {
                    RText("Backup & Restore", 22, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(3.r))
                    RText("Single-File Auto-Overwrite Backup", 13.5, FontWeight.Normal, Color(0xFF94A3B8))
                }

                Box(
                    modifier = Modifier
                        .size(38.r)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2633))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Close, Color(0xFFCBD5E1), Modifier.size(19.r), strokeWidth = 2.2f)
                }
            }

            // 2. Tab Switcher (Segmented Capsule)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.r)
                    .clip(RoundedCornerShape(14.r))
                    .background(Color(0xFF1B2230))
                    .padding(4.r),
                horizontalArrangement = Arrangement.spacedBy(4.r),
            ) {
                // Tab 0: 24/7 Local Backup
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.r)
                        .clip(RoundedCornerShape(11.r))
                        .background(if (selectedTab == 0) Color(0xFF00E676) else Color.Transparent)
                        .clickable { selectedTab = 0 },
                    contentAlignment = Alignment.Center,
                ) {
                    RText(
                        "📱 24/7 Local Backup",
                        14,
                        FontWeight.Bold,
                        if (selectedTab == 0) Color(0xFF052012) else Color(0xFF94A3B8),
                    )
                }

                // Tab 1: Google Drive Cloud
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.r)
                        .clip(RoundedCornerShape(11.r))
                        .background(if (selectedTab == 1) Color(0xFF00E676) else Color.Transparent)
                        .clickable { selectedTab = 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    RText(
                        "☁️ Google Drive Cloud",
                        14,
                        FontWeight.Bold,
                        if (selectedTab == 1) Color(0xFF052012) else Color(0xFF94A3B8),
                    )
                }
            }

            Spacer(Modifier.height(16.r))

            // 3. Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.r)
                    .clip(RoundedCornerShape(14.r))
                    .background(Color(0xFF064E3B).copy(alpha = 0.45f))
                    .border(1.r, Color(0xFF059669).copy(alpha = 0.55f), RoundedCornerShape(14.r))
                    .padding(horizontal = 16.r, vertical = 10.r),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.r).background(Color(0xFF00E676), CircleShape))
                    Spacer(Modifier.width(10.r))
                    RText(
                        if (selectedTab == 0) "Auto-Backup Active • 24/7 Protected" else "Google Drive Cloud • Ready to Sync",
                        13.5,
                        FontWeight.SemiBold,
                        Color(0xFF34D399),
                        Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoEnabled,
                        onCheckedChange = { checked ->
                            autoEnabled = checked
                            prefs.autoBackupEnabled = checked
                            if (checked) {
                                AutoBackupScheduler.schedule(context)
                                Toast.makeText(context, "প্রতি রাত ১১:৩০ PM অটো-ব্যাকআপ চালু হলো", Toast.LENGTH_SHORT).show()
                            } else {
                                AutoBackupScheduler.cancel(context)
                                Toast.makeText(context, "অটো-ব্যাকআপ বন্ধ করা হলো", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF052012),
                            checkedTrackColor = Color(0xFF00E676),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1B2230),
                        ),
                        modifier = Modifier.scale(0.82f),
                    )
                }
            }

            Spacer(Modifier.height(12.r))

            // 4. Description Text
            RText(
                if (selectedTab == 0) {
                    "Whenever you edit or update any data, the entire app database is automatically backed up and overwritten to a single secure file (Nirvor_AutoBackup.json). It also runs daily in the background at 11:30 PM."
                } else {
                    "Easily export your complete backup to Google Drive or any cloud folder, and restore on any new phone whenever you need."
                },
                13.5,
                FontWeight.Normal,
                Color(0xFF94A3B8),
                Modifier.padding(horizontal = 22.r),
                lineHeight = 20,
            )

            Spacer(Modifier.height(16.r))

            // 5. LOCAL BACKUP DETAILS / GOOGLE DRIVE DETAILS Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.r)
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFF141C28))
                    .border(1.r, Color(0xFF232E40), RoundedCornerShape(16.r))
                    .padding(18.r),
            ) {
                Column {
                    // Header of details card
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RText(
                            if (selectedTab == 0) "LOCAL BACKUP DETAILS" else "GOOGLE DRIVE DETAILS",
                            12.5,
                            FontWeight.SemiBold,
                            Color(0xFF64748B),
                            Modifier.weight(1f),
                        )
                        RText("READY ✓", 13, FontWeight.Bold, Color(0xFF00E676))
                    }

                    Spacer(Modifier.height(15.r))

                    // Row 1: File Name
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RText("File Name", 14, FontWeight.Normal, Color(0xFF94A3B8), Modifier.weight(1f))
                        RText(
                            if (selectedTab == 0) "Nirvor_AutoBackup.json" else "Nirvor_DriveBackup.json",
                            14,
                            FontWeight.SemiBold,
                            Color(0xFF38BDF8),
                        )
                    }

                    Spacer(Modifier.height(12.r))

                    // Row 2: Location
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RText("Location", 14, FontWeight.Normal, Color(0xFF94A3B8), Modifier.weight(1f))
                        RText(
                            if (selectedTab == 0) "Download/Nirvor_Backup/" else "Google Drive / Cloud Folder",
                            14,
                            FontWeight.Medium,
                            Color.White,
                        )
                    }

                    Spacer(Modifier.height(12.r))

                    // Row 3: Last Backup
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RText("Last Backup", 14, FontWeight.Normal, Color(0xFF94A3B8), Modifier.weight(1f))
                        val lastStr = if (lastBackup > 0L) {
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(lastBackup))
                        } else {
                            "Never"
                        }
                        RText(lastStr, 14, FontWeight.Medium, Color.White)
                    }

                    Spacer(Modifier.height(12.r))

                    // Row 4: File Size
                    if (selectedTab == 0) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RText("File Size", 14, FontWeight.Normal, Color(0xFF94A3B8), Modifier.weight(1f))
                            val sizeStr = if (singleFile.exists()) {
                                String.format(Locale.US, "%.1f KB", singleFile.length() / 1024.0)
                            } else {
                                "0.0 KB"
                            }
                            RText(sizeStr, 14, FontWeight.Bold, Color(0xFFF59E0B))
                        }
                        Spacer(Modifier.height(12.r))
                    }

                    // Row 5: Records Saved
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RText("Records Saved", 14, FontWeight.Normal, Color(0xFF94A3B8), Modifier.weight(1f))
                        RText(recordsSummary, 14, FontWeight.Medium, Color.White)
                    }
                }
            }

            Spacer(Modifier.height(20.r))

            // 6. Action Buttons Stack
            if (selectedTab == 0) {
                // Tab 0: Local Backup Actions
                // Button 1: Backup Now (Overwrite File)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(52.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF00E676))
                        .clickable {
                            scope.launch {
                                isWorking = true
                                try {
                                    BackupManager.saveSingleAutoBackup(context)
                                    refreshSummary()
                                    Toast.makeText(context, "ব্যাকআপ সফলভাবে সংরক্ষণ ও ওভাররাইট হয়েছে!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ব্যর্থ: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isWorking = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.r), color = Color(0xFF052012), strokeWidth = 2.5.r)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Refresh, Color(0xFF052012), Modifier.size(20.r), strokeWidth = 2.4f)
                            Spacer(Modifier.width(10.r))
                            RText("Backup Now (Overwrite File)", 16, FontWeight.Bold, Color(0xFF052012))
                        }
                    }
                }

                Spacer(Modifier.height(12.r))

                // Button 2: Restore Data from Backup
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(52.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF141F2D))
                        .border(1.2.r, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.r))
                        .clickable {
                            if (singleFile.exists()) {
                                confirmRestoreFile = singleFile
                            } else {
                                openDriveLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.ClockHands, Color(0xFF38BDF8), Modifier.size(20.r), strokeWidth = 2.2f)
                        Spacer(Modifier.width(10.r))
                        RText("Restore Data from Backup", 16, FontWeight.SemiBold, Color(0xFF38BDF8))
                    }
                }

                Spacer(Modifier.height(12.r))

                // Button 3: Share Backup File
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(50.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF141923))
                        .border(1.r, Color(0xFF263244), RoundedCornerShape(14.r))
                        .clickable {
                            scope.launch {
                                val file = if (singleFile.exists()) singleFile else BackupManager.saveSingleAutoBackup(context)
                                BackupManager.shareBackupFile(context, file)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Share, Color(0xFFCBD5E1), Modifier.size(19.r), strokeWidth = 2f)
                        Spacer(Modifier.width(10.r))
                        RText("Share Backup File", 15, FontWeight.Medium, Color.White)
                    }
                }
            } else {
                // Tab 1: Google Drive Actions
                // Button 1: Backup to Google Drive
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(52.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF00E676))
                        .clickable {
                            val timeTag = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            saveDriveLauncher.launch("Nirvor_DriveBackup_$timeTag.json")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.r), color = Color(0xFF052012), strokeWidth = 2.5.r)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Save, Color(0xFF052012), Modifier.size(20.r), strokeWidth = 2.4f)
                            Spacer(Modifier.width(10.r))
                            RText("Backup to Google Drive", 16, FontWeight.Bold, Color(0xFF052012))
                        }
                    }
                }

                Spacer(Modifier.height(12.r))

                // Button 2: Restore from Google Drive
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(52.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF141F2D))
                        .border(1.2.r, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.r))
                        .clickable {
                            openDriveLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Download, Color(0xFF38BDF8), Modifier.size(20.r), strokeWidth = 2.2f)
                        Spacer(Modifier.width(10.r))
                        RText("Restore from Google Drive", 16, FontWeight.SemiBold, Color(0xFF38BDF8))
                    }
                }

                Spacer(Modifier.height(12.r))

                // Button 3: Share to Drive / Other Apps
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.r)
                        .height(50.r)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color(0xFF141923))
                        .border(1.r, Color(0xFF263244), RoundedCornerShape(14.r))
                        .clickable {
                            scope.launch {
                                val file = if (singleFile.exists()) singleFile else BackupManager.saveSingleAutoBackup(context)
                                BackupManager.shareBackupFile(context, file)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Share, Color(0xFFCBD5E1), Modifier.size(19.r), strokeWidth = 2f)
                        Spacer(Modifier.width(10.r))
                        RText("Share to Drive / Other Apps", 15, FontWeight.Medium, Color.White)
                    }
                }
            }
        }
    }

    // Confirmation dialog for restoring backup
    val restoreTargetFile = confirmRestoreFile
    val restoreTargetJson = confirmRestoreJson

    if (restoreTargetFile != null || restoreTargetJson != null) {
        AlertDialog(
            onDismissRequest = {
                confirmRestoreFile = null
                confirmRestoreJson = null
            },
            title = { RText("ডেটা রিস্টোর নিশ্চিত করুন", 18, FontWeight.Bold, Color.White) },
            text = {
                RText(
                    "ব্যাকআপ রিস্টোর করলে বর্তমান ডেটা ব্যাকআপের ডেটা দ্বারা প্রতিস্থাপিত হবে। আপনি কি রিস্টোর করতে চান?",
                    15,
                    FontWeight.Normal,
                    Color(0xFF94A3B8),
                    lineHeight = 22,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = confirmRestoreFile
                        val rawJson = confirmRestoreJson
                        confirmRestoreFile = null
                        confirmRestoreJson = null
                        scope.launch {
                            val json = rawJson ?: file?.readText(Charsets.UTF_8)
                            if (json != null) {
                                val res = BackupManager.restoreFromJson(context, json)
                                res.fold(
                                    onSuccess = { count ->
                                        Toast.makeText(context, "$count টি রেকর্ড সফলভাবে রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                        refreshSummary()
                                        onBack()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "রিস্টোর ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
                                    },
                                )
                            }
                        }
                    },
                ) {
                    RText("হ্যাঁ, রিস্টোর করুন", 15, FontWeight.Bold, Color(0xFF00E676))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmRestoreFile = null
                        confirmRestoreJson = null
                    },
                ) {
                    RText("বাতিল", 15, FontWeight.Medium, Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF141923),
        )
    }
}
