package com.nirvor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.R

/** Latin text uses Inter; Bengali text falls back to the phone's Bengali font automatically. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Colours sampled from the reference image. */
object C {
    val Sheet = Color(0xFFF6FAFC)
    val SkyTop = Color(0xFFCDECFC)
    val White = Color(0xFFFFFFFF)

    val HeaderInk = Color(0xFF0E1222)
    val Ink = Color(0xFF0B0E22)
    val InkNavy = Color(0xFF15124A)
    val Body = Color(0xFF0A0C14)
    val Hint = Color(0xFF8A97A8)

    // Today card
    val TodayBorder = Color(0xFFE8EEF3)
    val CalBg = Color(0xFFF2F5F8)
    val CalIcon = Color(0xFF364051)
    val Date = Color(0xFF495F7D)
    val DateChevron = Color(0xFF5E6F8A)
    val StatLabel = Color(0xFF41597A)
    val StatLabelBn = Color(0xFF63748A)
    val Divider = Color(0xFFE8EDF2)
    val Sun = Color(0xFF4AB2F7)
    val SunFill = Color(0xFFDDF0FE)
    val GreenA = Color(0xFF4CCB67)
    val GreenB = Color(0xFF2EB34F)
    val OrangeA = Color(0xFFFFB63C)
    val OrangeB = Color(0xFFFB9D2D)
    val RedA = Color(0xFFFF6B56)
    val RedB = Color(0xFFF6463D)

    // Tasks card
    val TasksBg = Color(0xFFF7FDFA)
    val TasksBorder = Color(0xFFD6EEE8)
    val TaskGreenA = Color(0xFF16C47B)
    val TaskGreenB = Color(0xFF03A35B)
    val TaskViewAll = Color(0xFF2A7D65)
    val TaskViewAllChevron = Color(0xFF4F9580)
    val CheckA = Color(0xFF0DAE68)
    val CheckB = Color(0xFF049A56)
    val OpenRing = Color(0xFF7B8187)
    val DoneText = Color(0xFF686F7C)
    val Time = Color(0xFF687179)
    val TaskDivider = Color(0xFFE3EAEB)

    // Reminders card
    val RemBg = Color(0xFFFCFCFE)
    val RemHeaderBg = Color(0xFFF6F2FE)
    val RemBorder = Color(0xFFE2E0F9)
    val PurpleA = Color(0xFFA963EC)
    val PurpleB = Color(0xFF8849D5)
    val RemViewAll = Color(0xFF7541C0)
    val RemItemBg = Color(0xFFEFE8FE)
    val RemIcon = Color(0xFF8445D0)
    val RemTime = Color(0xFF5A6886)
    val RemChevron = Color(0xFF8A9CB3)
    val RemDivider = Color(0xFFEAEDF5)

    // Expense card
    val ExpBgTop = Color(0xFFFDF7FA)
    val ExpBgBottom = Color(0xFFFEFBFC)
    val ExpBorder = Color(0xFFF1E6EE)
    val OrangeIconA = Color(0xFFFF8149)
    val OrangeIconB = Color(0xFFF55A21)
    val ViewAllGrey = Color(0xFF5C6D81)
    val ViewAllGreyChevron = Color(0xFF6B7A8E)
    val ExpSub = Color(0xFF4E5F80)
    val InnerBorder = Color(0xFFF6EDF2)
    val InnerDivider = Color(0xFFEEF0F4)
    val CatLabel = Color(0xFF31476A)
    val CatAmount = Color(0xFF0D1729)

    // Habit card
    val HabBg = Color(0xFFF8FDFE)
    val HabBorder = Color(0xFFD6ECEE)
    val HabGreenA = Color(0xFF4CCD6A)
    val HabGreenB = Color(0xFF2BB24E)
    val RingTrack = Color(0xFFDDE6E9)
    val RingProgress = Color(0xFF06A05E)
    val RingLabel = Color(0xFF919AA8)
    val HabCheck = Color(0xFF0BA062)
    val HabOpen = Color(0xFFAAB5C1)
    val HabText = Color(0xFF31485F)
    val HabIcon = Color(0xFF485669)
    val HabMoon = Color(0xFF516172)
    val Drop = Color(0xFF52C8F4)
    val DropEdge = Color(0xFF2F9AD6)

    // Quick notes
    val NotesBg = Color(0xFFFEF6E2)
    val NotesBorder = Color(0xFFF6E7C4)
    val NotesIconA = Color(0xFFFAC22B)
    val NotesIconB = Color(0xFFF4A905)
    val NotesTitle = Color(0xFF050608)
    val NotesText = Color(0xFF131F25)
    val NotesChevron = Color(0xFF5E6F79)

    // FAB + navigation
    val FabA = Color(0xFF15B97A)
    val FabB = Color(0xFF029C62)
    val NavBg = Color(0xFFFDFEFF)
    val NavLine = Color(0xFFE6EDF2)
    val NavActive = Color(0xFF0A955A)
    val NavActiveText = Color(0xFF12924F)
    val NavIcon = Color(0xFF5A7190)
    val NavText = Color(0xFF4E6385)

    // Shadows (opaque tints; the system applies the shadow alpha)
    val ShadowSoft = Color(0xFF8FA3B6)
    val ShadowGreen = Color(0xFF1C7F55)

    val Danger = Color(0xFFE5484D)
    val FieldLine = Color(0xFFDCE4EA)
}

private val base = Typography()

private val NirvorTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Inter),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter),
    titleLarge = base.titleLarge.copy(fontFamily = Inter),
    titleMedium = base.titleMedium.copy(fontFamily = Inter),
    titleSmall = base.titleSmall.copy(fontFamily = Inter),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter),
    labelMedium = base.labelMedium.copy(fontFamily = Inter),
    labelSmall = base.labelSmall.copy(fontFamily = Inter),
)

private val NirvorColors = lightColorScheme(
    primary = Color(0xFF0BA765),
    onPrimary = Color.White,
    secondary = Color(0xFF8849D5),
    background = C.Sheet,
    surface = Color.White,
    onSurface = C.Ink,
)

@Composable
fun NirvorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NirvorColors, typography = NirvorTypography, content = content)
}
