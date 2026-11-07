package com.nirvor.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.downloader.NirvorFolder
import com.nirvor.app.reminders.Notifications
import com.nirvor.app.ui.browser.BrowserScreen
import com.nirvor.app.ui.download.DownloadsScreen
import com.nirvor.app.ui.download.FolderSetupScreen
import com.nirvor.app.ui.download.FormatSheet
import com.nirvor.app.ui.home.BottomBar
import com.nirvor.app.ui.home.Fab
import com.nirvor.app.ui.home.HomeScreen
import com.nirvor.app.ui.home.Tab
import com.nirvor.app.ui.home.sky.SkyClock
import com.nirvor.app.ui.screens.HabitsScreen
import com.nirvor.app.ui.screens.MoreScreen
import com.nirvor.app.ui.screens.NotesScreen
import com.nirvor.app.ui.screens.ProfileScreen
import com.nirvor.app.ui.screens.RemindersScreen
import com.nirvor.app.ui.screens.AboutScreen
import com.nirvor.app.ui.screens.AppUpdateScreen
import com.nirvor.app.ui.screens.DownloadSettingsScreen
import com.nirvor.app.ui.screens.PermissionsScreen
import com.nirvor.app.ui.screens.SettingsScreen
import com.nirvor.app.ui.screens.SideBar
import com.nirvor.app.ui.screens.WeatherSettingsScreen
import com.nirvor.app.ui.screens.TasksScreen
import com.nirvor.app.ui.sheets.AddHabitSheet
import com.nirvor.app.ui.sheets.AddReminderSheet
import com.nirvor.app.ui.sheets.AddTaskSheet
import com.nirvor.app.ui.sheets.ChooserSheet
import com.nirvor.app.ui.sheets.NameDialog
import com.nirvor.app.ui.sheets.NoteSheet
import com.nirvor.app.ui.sheets.SheetKind
import com.nirvor.app.ui.money.AddMoneySheet
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.ui.ledger.InstallmentScreen
import com.nirvor.app.tools.ToolId
import com.nirvor.app.ui.tools.ToolBoxScreen
import com.nirvor.app.ui.tools.ToolHost
import com.nirvor.app.ui.islamic.IslamicHost
import com.nirvor.app.ui.islamic.IslamicHubScreen
import com.nirvor.app.ui.islamic.IslamicTool
import com.nirvor.app.ui.games.GameHostScreen
import com.nirvor.app.ui.games.GamesHubScreen
import com.nirvor.app.ui.games.duelx.DuelXMainHubScreen
import com.nirvor.app.ui.ledger.KistiSheet
import com.nirvor.app.ui.ledger.LEDGER_TAB_KISTI
import com.nirvor.app.ui.ledger.LEDGER_TAB_OWE
import com.nirvor.app.ui.ledger.LEDGER_TAB_OWED
import com.nirvor.app.ui.ledger.LedgerEntrySheet
import com.nirvor.app.ui.ledger.LedgerPersonScreen
import com.nirvor.app.ui.ledger.LedgerScreen
import com.nirvor.app.ui.money.MoneyCategoriesSheet
import com.nirvor.app.ui.money.MoneyScreen
import com.nirvor.app.money.MoneyType
import com.nirvor.app.ui.sheets.SkyPreviewSheet
import com.nirvor.app.weather.WeatherService
import kotlinx.coroutines.launch
import com.nirvor.app.ui.theme.C

/** Full-screen pages opened on top of the tabs. */
enum class Page {
    Reminders, Habits, Notes, Profile, Settings, Permissions, WeatherSettings, About, AppUpdate,
    Downloads, Browser, DownloadSettings,
    Ledger, LedgerPerson, Installment,
    ToolBox, Tool,
    Islamic, IslamicItem,
    Games, GamePlay,
}

/** Pages where the green + button makes sense. */
private val pagesWithFab = setOf(Page.Reminders, Page.Habits, Page.Notes, Page.Ledger)

/** Reference: bottom bar is 125 px tall, of which the last 22 px sit under the gesture area. */
private const val NAV_HEIGHT = 125f
private const val NAV_BOTTOM_SPACE = 22f

@Composable
fun NirvorRoot(vm: MainViewModel = viewModel()) {
    val ref = LocalRef.current
    val context = LocalContext.current
    val density = LocalDensity.current

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Open pages, newest last (e.g. Settings → Permissions). Saved as names so it survives rotation.
    var pageStack by rememberSaveable { mutableStateOf("") }
    val pages = pageStack.split(',').filter { it.isNotEmpty() }.map { Page.valueOf(it) }
    val page = pages.lastOrNull()
    fun openPage(p: Page) {
        // A page opened again moves to the top instead of being stacked twice.
        pageStack = (pages.filter { it != p } + p).joinToString(",") { it.name }
    }
    fun goBack() {
        pageStack = pages.dropLast(1).joinToString(",") { it.name }
    }
    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    // দেনা-পাওনা: open tab, opened person / plan, and what the add sheets start with.
    var ledgerTab by rememberSaveable { mutableIntStateOf(LEDGER_TAB_OWED) }
    var ledgerPersonId by rememberSaveable { mutableLongStateOf(0L) }
    var installmentId by rememberSaveable { mutableLongStateOf(0L) }
    var entryPersonId by remember { mutableStateOf<Long?>(null) }
    var entryGave by remember { mutableStateOf(true) }
    var editingKisti by remember { mutableStateOf<InstallmentEntity?>(null) }
    // Tool Box: the tool that is open.
    var toolName by rememberSaveable { mutableStateOf("") }
    // Islamic Tools: the tool that is open.
    var islamicName by rememberSaveable { mutableStateOf("") }
    // Game Hub: the game that is open.
    var gameId by rememberSaveable { mutableStateOf("") }
    val skyClock = remember { SkyClock() }
    var headerDark by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Download module: the link being analysed (format sheet) and a page for the browser to open.
    var analyzeUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var browserUrl by rememberSaveable { mutableStateOf<String?>(null) }
    fun openBrowser(url: String?) {
        browserUrl = url
        openPage(Page.Browser)
    }

    // First run: set up the Nirvor folder (shown once, after the name; again from the manager / settings).
    val onboarded by vm.onboarded.collectAsStateWithLifecycle()
    var folderSetupSeen by remember { mutableStateOf(NirvorFolder.setupSeen(context)) }
    var folderSetupAgain by remember { mutableStateOf(false) }
    val showFolderSetup = onboarded && (!folderSetupSeen || folderSetupAgain)
    fun closeFolderSetup() {
        NirvorFolder.markSetupSeen(context)
        folderSetupSeen = true
        folderSetupAgain = false
    }

    // Requests from outside the screens: a download notification, the share window.
    val navRequest by AppNav.request.collectAsStateWithLifecycle()
    LaunchedEffect(navRequest) {
        when (val r = navRequest) {
            null -> return@LaunchedEffect
            NavRequest.Downloads -> openPage(Page.Downloads)
            is NavRequest.Browser -> openBrowser(r.url)
        }
        drawerState.close()
        AppNav.request.value = null
    }

    // Status bar icons: white over the night sky on Home, dark everywhere else.
    val view = LocalView.current
    val lightIcons = headerDark && tab == Tab.Home && page == null && drawerState.isClosed && !showFolderSetup
    LaunchedEffect(lightIcons) {
        val window = view.context.findActivity()?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
    }

    BackHandler(enabled = page != null) { goBack() }
    BackHandler(enabled = page == null && tab != Tab.Home) { tab = Tab.Home }
    // Registered last, so when the side bar is open Back closes it first.
    BackHandler(enabled = !drawerState.isClosed) { scope.launch { drawerState.close() } }

    val navInsetRef = WindowInsets.navigationBars.getBottom(density) / ref.px
    val navExtra = (navInsetRef - NAV_BOTTOM_SPACE).coerceAtLeast(0f)
    val navHeight = NAV_HEIGHT + navExtra
    val showNav = page == null
    val listBottom = if (showNav) (navHeight + 140f).r else (navInsetRef + 150f).r

    // ---- permissions
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.refreshWeather(force = true) else vm.weatherPermissionDenied()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun hasLocation() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentName by vm.name.collectAsStateWithLifecycle()

    // Location is asked after the name and the folder page, so only one thing asks at a time.
    LaunchedEffect(onboarded, folderSetupSeen) {
        if (onboarded && folderSetupSeen) {
            if (hasLocation()) {
                vm.refreshWeather(force = false)
            } else if (!vm.askedLocationBefore()) {
                vm.markAskedLocation()
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    val city by vm.city.collectAsStateWithLifecycle()

    // Coming back to the app (e.g. after turning location on): try the weather again if needed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshWeather(force = false) }
    val onWeatherTap: () -> Unit = {
        when {
            city != null -> vm.refreshWeather(force = true)
            !hasLocation() -> {
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                Toast.makeText(context, "আবহাওয়া দেখাতে লোকেশন পারমিশন দিন", Toast.LENGTH_SHORT).show()
            }
            !WeatherService.isLocationEnabled(context) -> {
                Toast.makeText(context, "ফোনের লোকেশন চালু করুন, অথবা সেটিংস → আবহাওয়া থেকে শহর বেছে নিন", Toast.LENGTH_LONG).show()
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    // no settings screen
                }
            }
            else -> {
                vm.refreshWeather(force = true)
                Toast.makeText(context, "আবহাওয়া আপডেট হচ্ছে…", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Left side bar: swipe from the left edge, or the gear on the home header.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = (page == null && !showFolderSetup) || !drawerState.isClosed,
        drawerContent = {
            SideBar(
                onClose = { scope.launch { drawerState.close() } },
                onSettings = {
                    openPage(Page.Settings)
                    scope.launch { drawerState.close() }
                },
                onAbout = {
                    openPage(Page.About)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(C.Sheet)) {
            when (page) {
                null -> when (tab) {
                    Tab.Home -> HomeScreen(
                        vm = vm,
                        bottomExtra = navExtra.r,
                        onOpenTasks = { tab = Tab.Tasks },
                        onOpenMoney = { tab = Tab.Money },
                        onOpenReminders = { openPage(Page.Reminders) },
                        onOpenHabits = { openPage(Page.Habits) },
                        onOpenNotes = { openPage(Page.Notes) },
                        onOpenProfile = { openPage(Page.Profile) },
                        onAddTask = { sheet = SheetKind.Task },
                        onAddReminder = { sheet = SheetKind.Reminder },
                        onAddHabit = { sheet = SheetKind.Habit },
                        onWeatherTap = onWeatherTap,
                        skyClock = skyClock,
                        onSkyLongPress = { sheet = SheetKind.SkyPreview },
                        onHeaderDark = { headerDark = it },
                        onOpenSideBar = { scope.launch { drawerState.open() } },
                    )

                    Tab.Tasks -> TasksScreen(vm, listBottom)
                    Tab.Money -> MoneyScreen(
                        vm = vm,
                        bottomPadding = listBottom,
                        onAdd = { type -> sheet = if (type == MoneyType.Income) SheetKind.Income else SheetKind.Expense },
                        onCategories = { sheet = SheetKind.MoneyCategories },
                    )
                    Tab.More -> MoreScreen(
                        bottomPadding = listBottom,
                        onDownloads = { openPage(Page.Downloads) },
                        onLedger = { openPage(Page.Ledger) },
                        onTools = { openPage(Page.ToolBox) },
                        onIslamic = { openPage(Page.Islamic) },
                        onGames = { openPage(Page.Games) },
                        onReminders = { openPage(Page.Reminders) },
                        onHabits = { openPage(Page.Habits) },
                        onNotes = { openPage(Page.Notes) },
                        onUpdate = { openPage(Page.AppUpdate) },
                    )
                }

                Page.Reminders -> RemindersScreen(vm, listBottom, onBack = { goBack() })
                Page.Habits -> HabitsScreen(vm, listBottom, onBack = { goBack() })
                Page.Notes -> NotesScreen(vm, listBottom, onBack = { goBack() }, onEdit = {
                    editingNote = it
                    sheet = SheetKind.Note
                })

                Page.Profile -> ProfileScreen(vm, listBottom, onBack = { goBack() })

                Page.Ledger -> LedgerScreen(
                    vm = vm,
                    bottomPadding = listBottom,
                    tab = ledgerTab,
                    onTab = { ledgerTab = it },
                    onBack = { goBack() },
                    onPerson = {
                        ledgerPersonId = it
                        openPage(Page.LedgerPerson)
                    },
                    onKisti = {
                        installmentId = it
                        openPage(Page.Installment)
                    },
                    onAddEntry = { gave ->
                        entryPersonId = null
                        entryGave = gave
                        sheet = SheetKind.LedgerEntry
                    },
                    onAddKisti = {
                        editingKisti = null
                        sheet = SheetKind.Kisti
                    },
                )

                Page.LedgerPerson -> LedgerPersonScreen(
                    vm = vm,
                    bottomPadding = listBottom,
                    personId = ledgerPersonId,
                    onBack = { goBack() },
                    onAdd = { gave ->
                        entryPersonId = ledgerPersonId
                        entryGave = gave
                        sheet = SheetKind.LedgerEntry
                    },
                )

                Page.ToolBox -> ToolBoxScreen(
                    bottomPadding = listBottom,
                    onBack = { goBack() },
                    onOpen = {
                        toolName = it.name
                        openPage(Page.Tool)
                    },
                )

                Page.Islamic -> IslamicHubScreen(
                    onBack = { goBack() },
                    onOpen = {
                        islamicName = it.name
                        openPage(Page.IslamicItem)
                    },
                    onLocation = { openPage(Page.WeatherSettings) },
                )

                Page.IslamicItem -> {
                    // a small stack inside the page: e.g. নামাজ → সময়ের নিয়ম, back returns to নামাজ
                    val stack = islamicName.split(',').filter { it.isNotEmpty() }
                    val tool = stack.lastOrNull()?.let { IslamicTool.of(it) }
                    fun back() {
                        if (stack.size > 1) islamicName = stack.dropLast(1).joinToString(",") else goBack()
                    }
                    BackHandler(enabled = stack.size > 1) { back() }
                    if (tool != null) {
                        IslamicHost(
                            tool,
                            onBack = { back() },
                            onOpen = { islamicName = (stack + it.name).joinToString(",") },
                            onLocation = { openPage(Page.WeatherSettings) },
                        )
                    } else {
                        LaunchedEffect(Unit) { goBack() }
                    }
                }

                Page.Tool -> {
                    val tool = ToolId.of(toolName)
                    if (tool != null) {
                        ToolHost(tool, vm, onBack = { goBack() }, onOpenTool = { toolName = it.name })
                    } else {
                        LaunchedEffect(Unit) { goBack() }
                    }
                }

                Page.Games -> DuelXMainHubScreen(
                    onExitGameHub = { goBack() },
                )

                Page.Installment -> InstallmentScreen(
                    vm = vm,
                    bottomPadding = listBottom,
                    installmentId = installmentId,
                    onBack = { goBack() },
                    onEdit = {
                        editingKisti = it
                        sheet = SheetKind.Kisti
                    },
                )

                Page.Settings -> SettingsScreen(
                    vm = vm,
                    bottomPadding = listBottom,
                    onBack = { goBack() },
                    onProfile = { openPage(Page.Profile) },
                    onWeather = { openPage(Page.WeatherSettings) },
                    onPermissions = { openPage(Page.Permissions) },
                    onDownloads = { openPage(Page.DownloadSettings) },
                    onAbout = { openPage(Page.About) },
                    onUpdate = { openPage(Page.AppUpdate) },
                )

                Page.Permissions -> PermissionsScreen(vm, listBottom, onBack = { goBack() })
                Page.WeatherSettings -> WeatherSettingsScreen(vm, listBottom, onBack = { goBack() })
                Page.About -> AboutScreen(listBottom, onBack = { goBack() }, onUpdate = { openPage(Page.AppUpdate) })
                Page.AppUpdate -> AppUpdateScreen(listBottom, onBack = { goBack() })

                Page.Downloads -> DownloadsScreen(
                    bottomPadding = listBottom,
                    onBack = { goBack() },
                    onBrowser = { openBrowser(null) },
                    onSettings = { openPage(Page.DownloadSettings) },
                    onAnalyze = { analyzeUrl = it },
                    onSetupFolder = { folderSetupAgain = true },
                )

                Page.Browser -> BrowserScreen(
                    initialUrl = browserUrl,
                    onInitialUsed = { browserUrl = null },
                    onClose = { goBack() },
                    onHub = { openPage(Page.Downloads) },
                    onAnalyze = { analyzeUrl = it },
                )

                Page.DownloadSettings -> DownloadSettingsScreen(
                    bottomPadding = listBottom,
                    onBack = { goBack() },
                    onOpenHub = { openPage(Page.Downloads) },
                    onFolder = { folderSetupAgain = true },
                )
            }

            if (showNav) {
                BottomBar(
                    heightRef = navHeight,
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (page == null || page in pagesWithFab) {
                // Reference: FAB centre is 72.5 px above the bottom bar, at x = 696.5.
                val fabBottom = if (showNav) navHeight + 72.5f - 48f else navInsetRef + 40f
                Fab(
                    onClick = {
                        sheet = when {
                            page == Page.Reminders -> SheetKind.Reminder
                            page == Page.Habits -> SheetKind.Habit
                            page == Page.Notes -> SheetKind.Note
                            page == Page.Ledger && ledgerTab == LEDGER_TAB_KISTI -> {
                                editingKisti = null
                                SheetKind.Kisti
                            }
                            page == Page.Ledger -> {
                                entryPersonId = null
                                entryGave = ledgerTab != LEDGER_TAB_OWE
                                SheetKind.LedgerEntry
                            }
                            tab == Tab.Tasks -> SheetKind.Task
                            tab == Tab.Money -> SheetKind.Expense
                            else -> SheetKind.Chooser
                        }
                        editingNote = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = (696.5f - 48f).r, bottom = fabBottom.r),
                )
            }

            if (showFolderSetup) {
                FolderSetupScreen(
                    auto = !folderSetupSeen,
                    onDone = { closeFolderSetup() },
                    onSkip = { closeFolderSetup() },
                )
            }
        }
    }

    // ---- sheets
    when (sheet) {
        SheetKind.Chooser -> ChooserSheet(onDismiss = { sheet = null }, onPick = { sheet = it })
        SheetKind.Task -> AddTaskSheet(onDismiss = { sheet = null }) { title, day, minute ->
            vm.addTask(title, day, minute)
        }

        SheetKind.Reminder -> AddReminderSheet(onDismiss = { sheet = null }) { title, day, minute, daily, icon ->
            vm.addReminder(title, day, minute, daily, icon)
            askNotificationsIfNeeded()
        }

        SheetKind.Expense -> AddMoneySheet(vm, MoneyType.Expense, onDismiss = { sheet = null })
        SheetKind.Income -> AddMoneySheet(vm, MoneyType.Income, onDismiss = { sheet = null })
        SheetKind.MoneyCategories -> MoneyCategoriesSheet(vm, onDismiss = { sheet = null })
        SheetKind.LedgerEntry -> LedgerEntrySheet(
            vm = vm,
            presetPersonId = entryPersonId,
            presetGave = entryGave,
            onDismiss = { sheet = null },
            onNeedNotifications = { askNotificationsIfNeeded() },
        )
        SheetKind.Kisti -> KistiSheet(
            vm = vm,
            existing = editingKisti,
            onDismiss = {
                sheet = null
                editingKisti = null
            },
            onNeedNotifications = { askNotificationsIfNeeded() },
        )

        SheetKind.Habit -> AddHabitSheet(onDismiss = { sheet = null }) { name, icon ->
            vm.addHabit(name, icon)
        }

        SheetKind.Note -> NoteSheet(editingNote, onDismiss = {
            sheet = null
            editingNote = null
        }) { text ->
            vm.saveNote(editingNote, text)
        }

        SheetKind.SkyPreview -> {
            val current by vm.skyPreview.collectAsStateWithLifecycle()
            SkyPreviewSheet(
                current = current,
                onDismiss = { sheet = null },
                onTimelapse = {
                    skyClock.startTimelapse()
                    sheet = null
                },
                onWeather = { vm.setSkyPreview(it) },
            )
        }

        null -> Unit
    }

    // ---- download: which formats does this link have?
    analyzeUrl?.let { link ->
        FormatSheet(
            url = link,
            onDismiss = { analyzeUrl = null },
            onOpenBrowser = {
                analyzeUrl = null
                openBrowser(it)
            },
            onStarted = { analyzeUrl = null },
        )
    }

    if (!onboarded) {
        NameDialog(initial = currentName) { vm.finishOnboarding(it) }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
