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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nirvor.app.NirvorApplication
import com.nirvor.app.backup.AutoBackupScheduler
import com.nirvor.app.backup.BackupManager
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
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

    var autoEnabled by remember { mutableStateOf(prefs.autoBackupEnabled) }
    var lastBackup by remember { mutableStateOf(prefs.lastBackupTime) }
    var localFiles by remember { mutableStateOf(BackupManager.listLocalBackups(context)) }
    var isWorking by remember { mutableStateOf(false) }

    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }
    var confirmRestoreJson by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFile by remember { mutableStateOf<File?>(null) }

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
                            Toast.makeText(context, "গুগল ড্রাইভ / ক্লাউডে সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
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

    // Google Drive / SAF Open/Restore Launcher
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

    fun refreshList() {
        localFiles = BackupManager.listLocalBackups(context)
        lastBackup = prefs.lastBackupTime
    }

    PageScaffold(
        title = "Backup & Restore",
        subtitle = "অফলাইন ও গুগল ড্রাইভ ব্যাকআপ",
        onBack = onBack,
        bottomPadding = bottomPadding,
    ) {
        // Auto Backup section
        item { SectionLabel("স্বয়ংক্রিয় ব্যাকআপ") }
        item {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.r).background(Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0F766E))), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.ClockHands, Color.White, Modifier.size(28.r), strokeWidth = 2f)
                    }
                    Spacer(Modifier.width(16.r))
                    Column(Modifier.weight(1f)) {
                        RText("দৈনিক অটো-ব্যাকআপ", 21, FontWeight.Bold, C.Ink)
                        Spacer(Modifier.height(3.r))
                        RText("প্রতিদিন রাত ১১:৩০ PM-এ ব্যাকআপ হবে", 16, FontWeight.Normal, C.StatLabelBn)
                    }
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
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0D9488),
                        ),
                    )
                }
                Spacer(Modifier.height(10.r))
                val lastStr = if (lastBackup > 0L) {
                    "সর্বশেষ ব্যাকআপ: " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(lastBackup))
                } else {
                    "এখনো কোনো ব্যাকআপ নেওয়া হয়নি"
                }
                RText(lastStr, 15, FontWeight.Medium, Color(0xFF0D9488))
            }
        }

        // Google Drive / Cloud section
        item { SectionLabel("গুগল ড্রাইভ ও ক্লাউড") }
        item {
            ListCard {
                RText("গুগল ড্রাইভ ব্যাকআপ ও রিস্টোর", 20, FontWeight.Bold, C.Ink)
                Spacer(Modifier.height(5.r))
                RText(
                    "আপনার গুগল ড্রাইভ বা যেকোনো ক্লাউড ফোল্ডারে ডেটা নিরাপদে সংরক্ষণ করুন এবং যেকোনো নতুন ফোনে রিস্টোর করুন।",
                    16,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    lineHeight = 23,
                )
                Spacer(Modifier.height(16.r))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    Button(
                        onClick = {
                            val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            saveDriveLauncher.launch("nirvor_backup_$timeTag.json")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.r),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    ) {
                        RText("☁️ ড্রাইভে সেভ", 16, FontWeight.SemiBold, Color.White)
                    }
                    Button(
                        onClick = { openDriveLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.r),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    ) {
                        RText("📥 ড্রাইভ রিস্টোর", 16, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        // Offline storage section
        item { SectionLabel("অফলাইন লোকাল ব্যাকআপ") }
        item {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RText("এখনই অফলাইন ব্যাকআপ নিন", 20, FontWeight.Bold, C.Ink)
                        Spacer(Modifier.height(3.r))
                        RText("Downloads/Nirvor_Backup ফোল্ডারে সেভ হবে", 15, FontWeight.Normal, C.StatLabelBn)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                try {
                                    val file = BackupManager.saveOfflineBackup(context)
                                    withContext(Dispatchers.Main) {
                                        refreshList()
                                        Toast.makeText(context, "ব্যাকআপ সংরক্ষিত হয়েছে: ${file.name}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isWorking = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.r),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    ) {
                        RText("+ ব্যাকআপ নিন", 16, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        // List of backups
        item { SectionLabel("সংরক্ষিত ব্যাকআপ ফাইল (${localFiles.size})") }
        if (localFiles.isEmpty()) {
            item {
                ListCard {
                    RText(
                        "এখনো কোনো লোকাল ব্যাকআপ ফাইল নেই। উপরের বাটনে চাপ দিয়ে এখনই একটি অফলাইন ব্যাকআপ তৈরি করুন।",
                        16,
                        FontWeight.Normal,
                        C.Hint,
                        lineHeight = 24,
                    )
                }
            }
        } else {
            items(localFiles.size) { idx ->
                val f = localFiles[idx]
                val sizeKb = (f.length() / 1024L).coerceAtLeast(1L)
                val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(f.lastModified()))

                ListCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(46.r).background(Color(0xFFE6F4EA), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphIcon(Glyph.FileDoc, Color(0xFF0D9488), Modifier.size(24.r))
                            }
                            Spacer(Modifier.width(14.r))
                            Column(Modifier.weight(1f)) {
                                RText(f.name, 17, FontWeight.Bold, C.Ink, maxLines = 1)
                                Spacer(Modifier.height(2.r))
                                RText("$dateStr  •  ${sizeKb} KB", 14, FontWeight.Normal, C.StatLabel)
                            }
                        }
                        Spacer(Modifier.height(12.r))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.r),
                        ) {
                            // Restore
                            Button(
                                onClick = { confirmRestoreFile = f },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.r),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            ) {
                                RText("রিস্টোর", 14.5, FontWeight.SemiBold, Color.White)
                            }
                            // Share
                            Button(
                                onClick = { BackupManager.shareBackupFile(context, f) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.r),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            ) {
                                RText("শেয়ার", 14.5, FontWeight.SemiBold, Color.White)
                            }
                            // Delete
                            Button(
                                onClick = { confirmDeleteFile = f },
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.r),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            ) {
                                RText("মুছুন", 14.5, FontWeight.SemiBold, Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // Working overlay
    if (isWorking) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.White,
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color(0xFF0D9488))
                    Spacer(Modifier.width(18.r))
                    RText("কাজ চলছে, অনুগ্রহ করে অপেক্ষা করুন…", 18, FontWeight.Medium, C.Ink)
                }
            },
        )
    }

    // Confirm file restore dialog
    confirmRestoreFile?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRestoreFile = null },
            containerColor = Color.White,
            title = { RText("ডেটা রিস্টোর করবেন?", 22, FontWeight.Bold, C.Ink) },
            text = {
                RText(
                    "‘${f.name}’ ব্যাকআপ থেকে সব তথ্য রিস্টোর করা হবে। আপনার বর্তমান তথ্যের জায়গায় ব্যাকআপের তথ্য প্রতিস্থাপিত হবে।",
                    17,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    lineHeight = 25,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val fileToRestore = f
                    confirmRestoreFile = null
                    scope.launch {
                        isWorking = true
                        try {
                            val json = fileToRestore.readText(Charsets.UTF_8)
                            val res = BackupManager.restoreFromJson(context, json)
                            withContext(Dispatchers.Main) {
                                if (res.isSuccess) {
                                    Toast.makeText(context, "সফলভাবে ${res.getOrNull()} টি তথ্য রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "রিস্টোর ব্যর্থ হয়েছে: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } finally {
                            isWorking = false
                        }
                    }
                }) {
                    RText("হ্যাঁ, রিস্টোর করুন", 18, FontWeight.Bold, Color(0xFF059669))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreFile = null }) {
                    RText("বাতিল", 18, FontWeight.Normal, C.StatLabel)
                }
            },
        )
    }

    // Confirm JSON (Drive) restore dialog
    confirmRestoreJson?.let { json ->
        AlertDialog(
            onDismissRequest = { confirmRestoreJson = null },
            containerColor = Color.White,
            title = { RText("গুগল ড্রাইভ ব্যাকআপ রিস্টোর করবেন?", 22, FontWeight.Bold, C.Ink) },
            text = {
                RText(
                    "বাছাইকৃত ব্যাকআপ ফাইল থেকে আপনার সমস্ত কাজ, রিমাইন্ডার, হিসাব এবং ইসলামিক রেকর্ড রিস্টোর করা হবে।",
                    17,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    lineHeight = 25,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val j = json
                    confirmRestoreJson = null
                    scope.launch {
                        isWorking = true
                        try {
                            val res = BackupManager.restoreFromJson(context, j)
                            withContext(Dispatchers.Main) {
                                if (res.isSuccess) {
                                    Toast.makeText(context, "সফলভাবে ${res.getOrNull()} টি তথ্য রিস্টোর হয়েছে!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "রিস্টোর ব্যর্থ হয়েছে। ফাইলের বিন্যাস সঠিক কিনা দেখুন।", Toast.LENGTH_LONG).show()
                                }
                            }
                        } finally {
                            isWorking = false
                        }
                    }
                }) {
                    RText("হ্যাঁ, রিস্টোর করুন", 18, FontWeight.Bold, Color(0xFF059669))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreJson = null }) {
                    RText("বাতিল", 18, FontWeight.Normal, C.StatLabel)
                }
            },
        )
    }

    // Confirm delete file dialog
    confirmDeleteFile?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFile = null },
            containerColor = Color.White,
            title = { RText("ব্যাকআপ ফাইল মুছবেন?", 22, FontWeight.Bold, C.Ink) },
            text = { RText("‘${f.name}’ ফাইলটি স্থায়ীভাবে মুছে ফেলা হবে।", 17, FontWeight.Normal, C.StatLabelBn) },
            confirmButton = {
                TextButton(onClick = {
                    f.delete()
                    confirmDeleteFile = null
                    refreshList()
                    Toast.makeText(context, "ফাইল মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                }) {
                    RText("মুছে ফেলুন", 18, FontWeight.Bold, Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFile = null }) {
                    RText("বাতিল", 18, FontWeight.Normal, C.StatLabel)
                }
            },
        )
    }
}
