package com.nirvor.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.updater.AppUpdater
import com.nirvor.app.updater.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private enum class UpdateStatus {
    Idle, Checking, Available, UpToDate, Downloading, Downloaded, Error
}

private val BlueGradA = Color(0xFF2563EB)
private val BlueGradB = Color(0xFF1D4ED8)
private val GreenGradA = Color(0xFF059669)
private val GreenGradB = Color(0xFF047857)

@Composable
fun AppUpdateScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedVersion = remember { appVersion(context) }

    var status by remember { mutableStateOf(UpdateStatus.Idle) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var downloadPercent by remember { mutableIntStateOf(0) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    var hasInstallPermission by remember { mutableStateOf(AppUpdater.canInstallApks(context)) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // Re-check install permission on resume (e.g. user toggles setting and comes back)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasInstallPermission = AppUpdater.canInstallApks(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startCheck() {
        status = UpdateStatus.Checking
        errorMessage = null
        scope.launch {
            val res = AppUpdater.checkForUpdates(installedVersion)
            res.fold(
                onSuccess = { info ->
                    updateInfo = info
                    if (info.isUpdateAvailable) {
                        val cacheTarget = File(context.cacheDir, "updates/Nirvor-${info.tagName}.apk")
                        if (cacheTarget.exists() && info.apkSize > 0 && cacheTarget.length() == info.apkSize) {
                            downloadedFile = cacheTarget
                            status = UpdateStatus.Downloaded
                        } else {
                            status = UpdateStatus.Available
                        }
                    } else {
                        status = UpdateStatus.UpToDate
                    }
                },
                onFailure = { err ->
                    status = UpdateStatus.Error
                    errorMessage = err.localizedMessage ?: "GitHub থেকে তথ্য আনা যায়নি।"
                },
            )
        }
    }

    // Auto-check on first launch
    LaunchedEffect(Unit) {
        startCheck()
    }

    fun startDownload(info: UpdateInfo) {
        val cacheTarget = File(context.cacheDir, "updates/Nirvor-${info.tagName}.apk")
        status = UpdateStatus.Downloading
        downloadPercent = 0
        downloadedBytes = 0L
        totalBytes = info.apkSize

        downloadJob = scope.launch {
            val res = AppUpdater.downloadApk(info.downloadUrl, cacheTarget) { dl, tot, pct ->
                downloadedBytes = dl
                if (tot > 0) totalBytes = tot
                if (pct >= 0) downloadPercent = pct
            }

            res.fold(
                onSuccess = { file ->
                    downloadedFile = file
                    status = UpdateStatus.Downloaded
                    Toast.makeText(context, "ডাউনলোড সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                    // If install permission is already granted, launch installer immediately
                    if (AppUpdater.canInstallApks(context)) {
                        AppUpdater.installApk(context, file)
                    }
                },
                onFailure = { err ->
                    status = UpdateStatus.Error
                    errorMessage = "ডাউনলোড ব্যর্থ: ${err.message}"
                },
            )
        }
    }

    PageScaffold(
        title = "App Update",
        subtitle = "সরাসরি অ্যাপ আপডেট (OTA) ও রিলিজ নোট",
        onBack = onBack,
        bottomPadding = bottomPadding,
    ) {
        // Hero Status Card
        item {
            ListCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (gradA, gradB, glyph) = when (status) {
                        UpdateStatus.UpToDate -> Triple(GreenGradA, GreenGradB, Glyph.Check)
                        UpdateStatus.Available, UpdateStatus.Downloaded -> Triple(BlueGradA, BlueGradB, Glyph.Download)
                        UpdateStatus.Downloading -> Triple(Color(0xFF8B5CF6), Color(0xFF6D28D9), Glyph.Refresh)
                        UpdateStatus.Checking -> Triple(Color(0xFF3B82F6), Color(0xFF1D4ED8), Glyph.Refresh)
                        UpdateStatus.Error -> Triple(Color(0xFFEF4444), Color(0xFFB91C1C), Glyph.Close)
                        UpdateStatus.Idle -> Triple(BlueGradA, BlueGradB, Glyph.Refresh)
                    }

                    Box(
                        Modifier
                            .size(92.r)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(gradA, gradB))),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(glyph, Color.White, Modifier.size(46.r), strokeWidth = 2.4f)
                    }

                    Spacer(Modifier.height(16.r))

                    RText("Nirvor v$installedVersion", 28, FontWeight.Bold, C.Ink)
                    Spacer(Modifier.height(6.r))

                    when (status) {
                        UpdateStatus.Checking -> {
                            RText("আপডেটের খোঁজ করা হচ্ছে...", 17, FontWeight.Normal, C.StatLabelBn)
                        }
                        UpdateStatus.UpToDate -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 14.r, vertical = 5.r),
                                ) {
                                    RText("✓ সম্পূর্ণ আপ-টু-ডেট", 15, FontWeight.SemiBold, Color(0xFF15803D))
                                }
                            }
                            Spacer(Modifier.height(8.r))
                            RText("আপনি সর্বশেষ ভার্সনটি ব্যবহার করছেন।", 16, FontWeight.Normal, C.StatLabelBn)
                        }
                        UpdateStatus.Available -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFDBEAFE))
                                        .padding(horizontal = 14.r, vertical = 5.r),
                                ) {
                                    RText("নতুন ভার্সন: ${updateInfo?.tagName.orEmpty()}", 15, FontWeight.SemiBold, Color(0xFF1D4ED8))
                                }
                            }
                            Spacer(Modifier.height(8.r))
                            RText("একটি নতুন আপডেট প্রকাশিত হয়েছে!", 16, FontWeight.Medium, C.Ink)
                        }
                        UpdateStatus.Downloading -> {
                            RText("নতুন ভার্সন ডাউনলোড হচ্ছে...", 16, FontWeight.Medium, Color(0xFF6D28D9))
                        }
                        UpdateStatus.Downloaded -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 14.r, vertical = 5.r),
                                ) {
                                    RText("✓ ডাউনলোড সম্পন্ন", 15, FontWeight.SemiBold, Color(0xFF15803D))
                                }
                            }
                            Spacer(Modifier.height(8.r))
                            RText("নতুন ভার্সন ইনস্টল করার জন্য প্রস্তুত।", 16, FontWeight.Medium, C.Ink)
                        }
                        UpdateStatus.Error -> {
                            RText(errorMessage ?: "তথ্য লোড করা যায়নি", 16, FontWeight.Medium, Color(0xFFDC2626), align = TextAlign.Center)
                        }
                        UpdateStatus.Idle -> {}
                    }
                }
            }
        }

        // Action & Progress Section
        item {
            ListCard {
                when (status) {
                    UpdateStatus.Checking -> {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.r),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.r),
                                color = BlueGradA,
                                strokeWidth = 2.5.r,
                            )
                            Spacer(Modifier.width(14.r))
                            RText("GitHub সার্ভার চেক করা হচ্ছে...", 17, FontWeight.Medium, C.Ink)
                        }
                    }

                    UpdateStatus.Available -> {
                        val info = updateInfo
                        if (info != null) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        RText("নতুন ভার্সন", 15, FontWeight.Normal, C.StatLabelBn)
                                        RText(info.tagName, 20, FontWeight.Bold, C.Ink)
                                    }
                                    if (info.apkSize > 0) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            RText("সাইজ", 15, FontWeight.Normal, C.StatLabelBn)
                                            val sizeMb = info.apkSize / (1024.0 * 1024.0)
                                            RText(String.format("%.1f MB", sizeMb), 18, FontWeight.SemiBold, C.Ink)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(18.r))

                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.r))
                                        .background(Brush.linearGradient(listOf(BlueGradA, BlueGradB)))
                                        .clickable { startDownload(info) }
                                        .padding(vertical = 15.r),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GlyphIcon(Glyph.Download, Color.White, Modifier.size(22.r), strokeWidth = 2f)
                                        Spacer(Modifier.width(10.r))
                                        RText("আপডেট ডাউনলোড করুন", 18, FontWeight.SemiBold, Color.White)
                                    }
                                }
                            }
                        }
                    }

                    UpdateStatus.Downloading -> {
                        Column(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RText("ডাউনলোড হচ্ছে...", 17, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
                                RText(
                                    if (downloadPercent >= 0) "${bnDigits(downloadPercent)}%" else "",
                                    18,
                                    FontWeight.Bold,
                                    BlueGradA,
                                )
                            }
                            Spacer(Modifier.height(10.r))
                            if (downloadPercent >= 0) {
                                LinearProgressIndicator(
                                    progress = { downloadPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.r).clip(RoundedCornerShape(4.r)),
                                    color = BlueGradA,
                                    trackColor = C.TodayBorder,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(8.r).clip(RoundedCornerShape(4.r)),
                                    color = BlueGradA,
                                    trackColor = C.TodayBorder,
                                )
                            }
                            Spacer(Modifier.height(8.r))
                            Row(Modifier.fillMaxWidth()) {
                                if (totalBytes > 0) {
                                    val dlMb = downloadedBytes / (1024.0 * 1024.0)
                                    val totMb = totalBytes / (1024.0 * 1024.0)
                                    RText(
                                        String.format("%.1f MB / %.1f MB", dlMb, totMb),
                                        14,
                                        FontWeight.Normal,
                                        C.StatLabelBn,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                RText(
                                    "বাতিল",
                                    14,
                                    FontWeight.Medium,
                                    Color(0xFFDC2626),
                                    Modifier.clickable {
                                        downloadJob?.cancel()
                                        status = UpdateStatus.Available
                                    },
                                )
                            }
                        }
                    }

                    UpdateStatus.Downloaded -> {
                        val file = downloadedFile
                        Column(Modifier.fillMaxWidth()) {
                            if (!hasInstallPermission) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.r))
                                        .background(Color(0xFFFEF3C7))
                                        .border(1.r, Color(0xFFFDE68A), RoundedCornerShape(14.r))
                                        .padding(14.r),
                                ) {
                                    Column {
                                        RText("⚠️ ইনস্টল অনুমতি প্রয়োজন", 16, FontWeight.SemiBold, Color(0xFFB45309))
                                        Spacer(Modifier.height(4.r))
                                        RText(
                                            "সরাসরি আপডেট দিতে Nirvor-কে 'Install unknown apps' অনুমতি দিন।",
                                            14,
                                            FontWeight.Normal,
                                            Color(0xFF92400E),
                                            maxLines = 2,
                                        )
                                        Spacer(Modifier.height(8.r))
                                        RText(
                                            "অনুমতি দিন ⚙️",
                                            15,
                                            FontWeight.SemiBold,
                                            Color(0xFFB45309),
                                            Modifier
                                                .clip(RoundedCornerShape(8.r))
                                                .background(Color.White)
                                                .clickable { AppUpdater.openInstallPermissionSettings(context) }
                                                .padding(horizontal = 12.r, vertical = 6.r),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.r))
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.r))
                                    .background(Brush.linearGradient(listOf(GreenGradA, GreenGradB)))
                                    .clickable {
                                        if (file != null) {
                                            if (!AppUpdater.canInstallApks(context)) {
                                                AppUpdater.openInstallPermissionSettings(context)
                                                Toast.makeText(context, "ইনস্টল করার অনুমতি চালু করুন", Toast.LENGTH_LONG).show()
                                            } else {
                                                val ok = AppUpdater.installApk(context, file)
                                                if (!ok) {
                                                    Toast.makeText(context, "ইনস্টলার ওপেন করা যায়নি", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 15.r),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlyphIcon(Glyph.Check, Color.White, Modifier.size(22.r), strokeWidth = 2.4f)
                                    Spacer(Modifier.width(10.r))
                                    RText("এখনই ইনস্টল করুন (Install Now)", 18, FontWeight.SemiBold, Color.White)
                                }
                            }
                        }
                    }

                    UpdateStatus.UpToDate -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.r))
                                .background(C.TodayBorder)
                                .clickable { startCheck() }
                                .padding(vertical = 14.r),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.Refresh, C.Ink, Modifier.size(20.r), strokeWidth = 2f)
                                Spacer(Modifier.width(10.r))
                                RText("পুনরায় আপডেট চেক করুন", 17, FontWeight.Medium, C.Ink)
                            }
                        }
                    }

                    UpdateStatus.Error -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.r))
                                .background(Brush.linearGradient(listOf(BlueGradA, BlueGradB)))
                                .clickable { startCheck() }
                                .padding(vertical = 14.r),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(20.r), strokeWidth = 2f)
                                Spacer(Modifier.width(10.r))
                                RText("আবার চেষ্টা করুন", 17, FontWeight.SemiBold, Color.White)
                            }
                        }
                    }

                    UpdateStatus.Idle -> {}
                }
            }
        }

        // Release Notes Section (রিলিজ নোট / কী কী পরিবর্তন হয়েছে)
        val info = updateInfo
        if (info != null && info.releaseNotes.isNotBlank()) {
            item {
                SectionLabel("রিলিজ নোট ও পরিবর্তন")
            }
            item {
                ListCard {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RText("📝 ${info.releaseTitle}", 19, FontWeight.Bold, C.Ink, Modifier.weight(1f))
                            if (info.publishedAt.isNotBlank()) {
                                val shortDate = info.publishedAt.take(10)
                                RText(shortDate, 14, FontWeight.Normal, C.StatLabelBn)
                            }
                        }
                        Spacer(Modifier.height(10.r))
                        HorizontalDivider(color = C.TodayBorder, thickness = 1.r)
                        Spacer(Modifier.height(12.r))

                        // Render release notes cleanly line-by-line
                        val lines = info.releaseNotes.lines()
                        lines.forEach { rawLine ->
                            val line = rawLine.trim()
                            if (line.isNotEmpty()) {
                                val isHeader = line.startsWith("#")
                                val isBullet = line.startsWith("*") || line.startsWith("-")
                                val isSeparator = line.startsWith("---") || line.startsWith("___")

                                if (isSeparator) {
                                    Spacer(Modifier.height(8.r))
                                    HorizontalDivider(color = C.TodayBorder, thickness = 1.r)
                                    Spacer(Modifier.height(8.r))
                                } else if (isHeader) {
                                    val headerText = line.replace(Regex("^#+\\s*"), "")
                                    Spacer(Modifier.height(6.r))
                                    RText(headerText, 17, FontWeight.SemiBold, C.Ink)
                                    Spacer(Modifier.height(4.r))
                                } else if (isBullet) {
                                    val bulletText = line.replace(Regex("^[*-]\\s*"), "")
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.r),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        RText("•", 16, FontWeight.Bold, BlueGradA, Modifier.padding(end = 8.r))
                                        RText(bulletText, 15, FontWeight.Normal, C.Ink, maxLines = 4, lineHeight = 22)
                                    }
                                } else {
                                    RText(line, 15, FontWeight.Normal, C.StatLabelBn, maxLines = 4, lineHeight = 22)
                                    Spacer(Modifier.height(3.r))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
