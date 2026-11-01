package com.nirvor.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The whole UI is laid out in "reference pixels": the coordinates of the design image,
 * whose phone screen is 766 px wide. Everything is scaled by screen width, so the app
 * keeps exactly the same proportions as the reference on every phone.
 */
const val REF_WIDTH = 766f

@Immutable
data class Ref(val k: Float, val density: Float, val fontScale: Float) {
    /** Physical pixels per reference pixel. */
    val px: Float get() = k * density
}

val LocalRef = staticCompositionLocalOf { Ref(0.5f, 2f, 1f) }

@Composable
fun ProvideRef(content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val ref = remember(cfg.screenWidthDp, d.density, d.fontScale) {
        Ref(cfg.screenWidthDp / REF_WIDTH, d.density, d.fontScale)
    }
    CompositionLocalProvider(LocalRef provides ref, content = content)
}

/** Reference pixels -> dp. */
val Number.r: Dp
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalRef.current.k).dp

/** Reference pixels -> sp (independent of the system font size, to keep the layout exact). */
val Number.rs: TextUnit
    @Composable @ReadOnlyComposable
    get() = (toFloat() * LocalRef.current.k / LocalRef.current.fontScale).sp

enum class HAlign { Start, Center, End }

/** Puts the element's top-left corner at (x, y) reference px inside a parent Box. */
fun Modifier.at(ref: Ref, x: Number, y: Number): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place((x.toFloat() * ref.px).roundToInt(), (y.toFloat() * ref.px).roundToInt())
    }
}

/** Puts the element's centre at (cx, cy) reference px inside a parent Box. */
fun Modifier.atCenter(ref: Ref, cx: Number, cy: Number): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) {
        p.place(
            (cx.toFloat() * ref.px).roundToInt() - p.width / 2,
            (cy.toFloat() * ref.px).roundToInt() - p.height / 2,
        )
    }
}

/** Puts a text so that its first baseline sits at y = [baseline] reference px. */
fun Modifier.atBaseline(
    ref: Ref,
    x: Number,
    baseline: Number,
    align: HAlign = HAlign.Start,
): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val b = p[FirstBaseline].let { if (it == AlignmentLine.Unspecified) 0 else it }
    val left = (x.toFloat() * ref.px).roundToInt() - when (align) {
        HAlign.Start -> 0
        HAlign.Center -> p.width / 2
        HAlign.End -> p.width
    }
    val top = (baseline.toFloat() * ref.px).roundToInt() - b
    layout(p.width, p.height) { p.place(left, top) }
}
