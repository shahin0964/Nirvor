package com.nirvor.app.ui.player

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirvor.app.player.LayoutMode
import com.nirvor.app.player.MediaFolder
import com.nirvor.app.player.MediaItem
import com.nirvor.app.player.MediaScanner
import com.nirvor.app.player.SortField
import com.nirvor.app.player.SortOrder
import com.nirvor.app.player.ViewMode
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NirvorPlayerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permissions check
    var hasPermission by remember { mutableStateOf(MediaScanner.hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.any { it } || MediaScanner.hasStoragePermission(context)
    }

    // Media list and scanning
    var isLoading by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<MediaFolder>>(emptyList()) }
    var activeFolder by remember { mutableStateOf<MediaFolder?>(null) }

    // Search and filter state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMusicOnly by remember { mutableStateOf(false) }

    // View options state (MX Player style)
    var viewMode by remember { mutableStateOf(ViewMode.AllFolders) }
    var layoutMode by remember { mutableStateOf(LayoutMode.List) }
    var sortField by remember { mutableStateOf(SortField.Date) }
    var sortOrder by remember { mutableStateOf(SortOrder.Descending) }
    var isViewOptionsOpen by remember { mutableStateOf(false) }

    // Dialog states
    var isNetworkStreamOpen by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }
    var propertiesItem by remember { mutableStateOf<MediaItem?>(null) }
    var deleteConfirmItem by remember { mutableStateOf<MediaItem?>(null) }

    // Private folder vault
    var isPrivateVaultOpen by remember { mutableStateOf(false) }
    var privatePinInput by remember { mutableStateOf("") }
    var isPrivateUnlocked by remember { mutableStateOf(false) }

    // Active player playback state
    var playingPlaylist by remember { mutableStateOf<List<MediaItem>?>(null) }
    var playingIndex by remember { mutableIntStateOf(0) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }

    fun refreshMedia() {
        scope.launch {
            isLoading = true
            val scanned = MediaScanner.scanAllMedia(
                context,
                includeAudio = true,
                includeVideo = !filterMusicOnly
            )
            folders = scanned
            // Refresh active folder if one was open
            activeFolder?.let { cur ->
                activeFolder = scanned.firstOrNull { it.name == cur.name }
            }
            isLoading = false
        }
    }

    LaunchedEffect(hasPermission, filterMusicOnly) {
        if (hasPermission) {
            refreshMedia()
        }
    }

    // Intercept back navigation: if player is open -> close player; if in folder -> exit to folders; else -> onBack()
    BackHandler {
        when {
            playingPlaylist != null || activeStreamUrl != null -> {
                playingPlaylist = null
                activeStreamUrl = null
            }
            activeFolder != null -> {
                activeFolder = null
            }
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
            }
            else -> onBack()
        }
    }

    // 1. ACTIVE FULLSCREEN PLAYER
    if (playingPlaylist != null || activeStreamUrl != null) {
        NirvorVideoPlayer(
            playlist = playingPlaylist ?: emptyList(),
            initialIndex = playingIndex,
            streamUrl = activeStreamUrl,
            onClose = {
                playingPlaylist = null
                activeStreamUrl = null
            }
        )
        return
    }

    // 2. MAIN MX-STYLE BROWSER SCREEN
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Sleek dark MX Player aesthetic
    ) {
        // TOP APP BAR
        Surface(
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeFolder != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { activeFolder = null },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Title
                    Text(
                        text = activeFolder?.name ?: "Folders",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Search button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { isSearchActive = !isSearchActive },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Search, Color.White, Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(4.dp))

                    // Layout / View Options button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { isViewOptionsOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Grid, Color.White, Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(4.dp))

                    // Refresh / Scan button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { refreshMedia() },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(20.dp))
                    }
                }

                // Expandable Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF334155))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(Glyph.Search, Color(0xFF94A3B8), Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Search videos, audios, folders...", color = Color(0xFF94A3B8), fontSize = 15.sp)
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { searchQuery = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlyphIcon(Glyph.Close, Color(0xFF94A3B8), Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // QUICK ACTION CHIPS ROW (MX Player Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Music / Audio toggle chip
            QuickChip(
                glyph = Glyph.Music,
                title = "Music",
                isSelected = filterMusicOnly,
                onClick = { filterMusicOnly = !filterMusicOnly }
            )

            // Network Stream chip
            QuickChip(
                glyph = Glyph.Globe,
                title = "Network Stream",
                isSelected = false,
                onClick = { isNetworkStreamOpen = true }
            )

            // Private Folder chip
            QuickChip(
                glyph = Glyph.Lock,
                title = "Privacy",
                isSelected = false,
                onClick = { isPrivateVaultOpen = true }
            )

            // All Files view toggle
            QuickChip(
                glyph = Glyph.Folder,
                title = if (viewMode == ViewMode.Files) "All Files" else "Folders",
                isSelected = viewMode == ViewMode.Files,
                onClick = {
                    viewMode = if (viewMode == ViewMode.Files) ViewMode.AllFolders else ViewMode.Files
                }
            )
        }

        // PERMISSION REQUIRED BANNER
        if (!hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.Film, Color(0xFF3B82F6), Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Storage Permission Required",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "To browse and play videos and audio files on your device, please allow media storage access.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_MEDIA_VIDEO,
                                        android.Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                )
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            return
        }

        // LOADING STATE
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3B82F6))
            }
            return
        }

        // MAIN CONTENT AREA
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeFolder != null) {
                // INSIDE FOLDER: LIST OR GRID OF VIDEOS/AUDIOS
                val items = activeFolder!!.items.filter { item ->
                    if (searchQuery.isEmpty()) true else item.displayName.contains(searchQuery, ignoreCase = true)
                }.sortedWith { a, b ->
                    val cmp = when (sortField) {
                        SortField.Title -> a.displayName.compareTo(b.displayName, ignoreCase = true)
                        SortField.Date -> a.dateAddedSec.compareTo(b.dateAddedSec)
                        SortField.Size -> a.sizeBytes.compareTo(b.sizeBytes)
                        SortField.Length -> a.durationMs.compareTo(b.durationMs)
                    }
                    if (sortOrder == SortOrder.Ascending) cmp else -cmp
                }

                if (items.isEmpty()) {
                    EmptyMediaPlaceholder("No media found in this folder")
                } else if (layoutMode == LayoutMode.List) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id.toString() + it.path }) { item ->
                            MediaItemRow(
                                item = item,
                                onClick = {
                                    playingPlaylist = items
                                    playingIndex = items.indexOf(item).coerceAtLeast(0)
                                },
                                onPlayAsAudio = {
                                    val audioOnly = item.copy(isVideo = false)
                                    playingPlaylist = listOf(audioOnly)
                                    playingIndex = 0
                                },
                                onProperties = { propertiesItem = item },
                                onDelete = { deleteConfirmItem = item }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id.toString() + it.path }) { item ->
                            MediaItemGridCard(
                                item = item,
                                onClick = {
                                    playingPlaylist = items
                                    playingIndex = items.indexOf(item).coerceAtLeast(0)
                                },
                                onProperties = { propertiesItem = item }
                            )
                        }
                    }
                }
            } else if (viewMode == ViewMode.Files) {
                // ALL FILES VIEW
                val allItems = folders.flatMap { it.items }.filter { item ->
                    if (searchQuery.isEmpty()) true else item.displayName.contains(searchQuery, ignoreCase = true)
                }.sortedWith { a, b ->
                    val cmp = when (sortField) {
                        SortField.Title -> a.displayName.compareTo(b.displayName, ignoreCase = true)
                        SortField.Date -> a.dateAddedSec.compareTo(b.dateAddedSec)
                        SortField.Size -> a.sizeBytes.compareTo(b.sizeBytes)
                        SortField.Length -> a.durationMs.compareTo(b.durationMs)
                    }
                    if (sortOrder == SortOrder.Ascending) cmp else -cmp
                }

                if (allItems.isEmpty()) {
                    EmptyMediaPlaceholder("No media files found")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allItems, key = { it.id.toString() + it.path }) { item ->
                            MediaItemRow(
                                item = item,
                                onClick = {
                                    playingPlaylist = allItems
                                    playingIndex = allItems.indexOf(item).coerceAtLeast(0)
                                },
                                onPlayAsAudio = {
                                    val audioOnly = item.copy(isVideo = false)
                                    playingPlaylist = listOf(audioOnly)
                                    playingIndex = 0
                                },
                                onProperties = { propertiesItem = item },
                                onDelete = { deleteConfirmItem = item }
                            )
                        }
                    }
                }
            } else {
                // FOLDER LIST VIEW (Exact MX Player Style)
                val visibleFolders = folders.filter { folder ->
                    if (searchQuery.isEmpty()) true else folder.name.contains(searchQuery, ignoreCase = true)
                }

                if (visibleFolders.isEmpty()) {
                    EmptyMediaPlaceholder("No video or audio folders found")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleFolders, key = { it.name + it.path }) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { activeFolder = folder }
                            )
                        }
                    }
                }
            }
        }
    }

    // 3. VIEW OPTIONS & SORT BOTTOM SHEET (Matches MX Player frame 00:06 - 00:12)
    if (isViewOptionsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { isViewOptionsOpen = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E293B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // View Mode & Layout row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("View Mode", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OptionPill("All folders", viewMode == ViewMode.AllFolders) { viewMode = ViewMode.AllFolders }
                            OptionPill("Files", viewMode == ViewMode.Files) { viewMode = ViewMode.Files }
                        }
                    }

                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Layout", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OptionPill("List", layoutMode == LayoutMode.List) { layoutMode = LayoutMode.List }
                            OptionPill("Grid", layoutMode == LayoutMode.Grid) { layoutMode = LayoutMode.Grid }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Sort section
                Text("Sort", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionPill("Title", sortField == SortField.Title) { sortField = SortField.Title }
                    OptionPill("Date", sortField == SortField.Date) { sortField = SortField.Date }
                    OptionPill("Size", sortField == SortField.Size) { sortField = SortField.Size }
                    OptionPill("Length", sortField == SortField.Length) { sortField = SortField.Length }
                }

                Spacer(Modifier.height(10.dp))

                // Ascending / Descending
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionPill("Oldest", sortOrder == SortOrder.Ascending) { sortOrder = SortOrder.Ascending }
                    OptionPill("Newest", sortOrder == SortOrder.Descending) { sortOrder = SortOrder.Descending }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isViewOptionsOpen = false }) {
                        Text("Done", color = Color(0xFF38BDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 4. NETWORK STREAM DIALOG (Matches MX Player frame 00:32)
    if (isNetworkStreamOpen) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AlertDialog(
            onDismissRequest = { isNetworkStreamOpen = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("Network Stream", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Please enter a network URL:\nExample: http://www.example.com/video.mkv",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = streamUrlInput,
                            onValueChange = { streamUrlInput = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (streamUrlInput.isEmpty()) {
                                    Text("http://...", color = Color(0xFF64748B), fontSize = 14.sp)
                                }
                                inner()
                            }
                        )
                        if (streamUrlInput.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { streamUrlInput = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(Glyph.Close, Color.White, Modifier.size(14.dp))
                            }
                        } else {
                            Text(
                                text = "PASTE",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) streamUrlInput = clip
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = streamUrlInput.trim()
                        if (url.isNotEmpty()) {
                            isNetworkStreamOpen = false
                            activeStreamUrl = url
                        } else {
                            Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("OK", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isNetworkStreamOpen = false }) {
                    Text("CANCEL", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 5. PROPERTIES DIALOG
    propertiesItem?.let { item ->
        AlertDialog(
            onDismissRequest = { propertiesItem = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("Properties", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropertyRow("File Name", item.displayName)
                    PropertyRow("Location", item.path.ifEmpty { item.uri.toString() })
                    PropertyRow("Size", item.formattedSize)
                    PropertyRow("Duration", item.formattedDuration)
                    PropertyRow("Format", item.resolutionOrFormat)
                    PropertyRow("Type", if (item.isVideo) "Video" else "Audio")
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesItem = null }) {
                    Text("OK", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 6. DELETE CONFIRMATION DIALOG
    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("Delete File", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete '${item.displayName}'? This cannot be undone.",
                    color = Color(0xFF94A3B8)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmItem = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (item.path.isNotEmpty()) {
                                    val f = File(item.path)
                                    if (f.exists()) f.delete()
                                }
                                context.contentResolver.delete(item.uri, null, null)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    refreshMedia()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Cannot delete this system file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("DELETE", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmItem = null }) {
                    Text("CANCEL", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 7. PRIVATE VAULT PIN DIALOG
    if (isPrivateVaultOpen) {
        val sp = remember { context.getSharedPreferences("nirvor_private_vault", Context.MODE_PRIVATE) }
        val savedPin = remember { sp.getString("pin", "1234") ?: "1234" }

        AlertDialog(
            onDismissRequest = {
                isPrivateVaultOpen = false
                privatePinInput = ""
            },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Lock, Color(0xFF38BDF8), Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Private Folder", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Enter 4-digit PIN to access private folder (Default: 1234):",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    BasicTextField(
                        value = privatePinInput,
                        onValueChange = { if (it.length <= 4) privatePinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155))
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (privatePinInput == savedPin) {
                            isPrivateVaultOpen = false
                            isPrivateUnlocked = true
                            Toast.makeText(context, "Private vault unlocked", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("UNLOCK", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isPrivateVaultOpen = false }) {
                    Text("CANCEL", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- COMPONENT VIEWS

@Composable
private fun QuickChip(
    glyph: Glyph,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(glyph, if (isSelected) Color.White else Color(0xFF38BDF8), Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * MX Player Folder Row (Matches frame 00:00 - 00:02 of video)
 */
@Composable
private fun FolderRow(
    folder: MediaFolder,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder Icon with notification count badge
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(
                    if (folder.name.contains("Camera", ignoreCase = true)) Glyph.Camera
                    else if (folder.name.contains("Screen", ignoreCase = true)) Glyph.Film
                    else if (folder.videoCount == 0 && folder.audioCount > 0) Glyph.Music
                    else Glyph.Folder,
                    Color(0xFF94A3B8),
                    Modifier.size(24.dp)
                )
            }

            // Notification Badge (Red pill with count)
            if (folder.totalCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = folder.totalCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        // Folder Title & Subtitle
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = folder.summaryText,
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }

        GlyphIcon(Glyph.ChevronRight, Color(0xFF64748B), Modifier.size(20.dp))
    }
}

/**
 * MX Player Video / Audio Item Row (Matches frame 00:03 - 00:04 of video)
 */
@Composable
private fun MediaItemRow(
    item: MediaItem,
    onClick: () -> Unit,
    onPlayAsAudio: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, item.path) {
        thumbnail = MediaScanner.getThumbnail(context, item)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with NEW badge and duration tag
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(
                        if (item.isVideo) Glyph.Film else Glyph.Music,
                        Color(0xFF475569),
                        Modifier.size(28.dp)
                    )
                }
            }

            // Red "NEW" badge on top-left of thumbnail
            if (item.isRecent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xFFEF4444), RoundedCornerShape(bottomEnd = 4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Duration badge on bottom-right of thumbnail
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topLeft = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = item.formattedDuration,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Title and Metadata
        Column(Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.formattedSize.isNotEmpty()) {
                    Text(item.formattedSize, color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(" • ", color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Text(item.resolutionOrFormat, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 3-dots Menu
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Dots, Color(0xFF94A3B8), Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    onClick = {
                        menuOpen = false
                        onClick()
                    }
                )
                if (item.isVideo) {
                    DropdownMenuItem(
                        text = { Text("Play as Audio") },
                        onClick = {
                            menuOpen = false
                            onPlayAsAudio()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        menuOpen = false
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = item.mimeType
                                putExtra(Intent.EXTRA_STREAM, item.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Properties") },
                    onClick = {
                        menuOpen = false
                        onProperties()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFF87171)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Grid Card representation for Grid layout
 */
@Composable
private fun MediaItemGridCard(
    item: MediaItem,
    onClick: () -> Unit,
    onProperties: () -> Unit,
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id, item.path) {
        thumbnail = MediaScanner.getThumbnail(context, item)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .background(Color(0xFF0F172A))
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlyphIcon(if (item.isVideo) Glyph.Film else Glyph.Music, Color(0xFF475569), Modifier.size(32.dp))
                }
            }

            if (item.isRecent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xFFEF4444), RoundedCornerShape(bottomEnd = 4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topLeft = 4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(item.formattedDuration, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        Column(Modifier.padding(10.dp)) {
            Text(
                text = item.displayName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${item.formattedSize} • ${item.resolutionOrFormat}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun OptionPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF334155))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyMediaPlaceholder(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155).copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Film, Color(0xFF64748B), Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(msg, color = Color(0xFF94A3B8), fontSize = 15.sp)
        }
    }
}
