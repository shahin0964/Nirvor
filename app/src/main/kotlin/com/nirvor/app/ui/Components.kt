package com.nirvor.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter

/** Text sized in reference px. */
@Composable
fun RText(
    text: String,
    size: Number,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    decoration: TextDecoration? = null,
    shadow: Shadow? = null,
    align: TextAlign = TextAlign.Start,
    lineHeight: Number = 0f,
) {
    val lh = lineHeight.toFloat()
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = size.rs,
            fontWeight = weight,
            color = color,
            textDecoration = decoration,
            shadow = shadow,
            textAlign = align,
            lineHeight = if (lh > 0f) lh.rs else TextUnit.Unspecified,
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Shadow + fill + hairline border, the way the cards in the reference are drawn. */
fun Modifier.cardSurface(
    shape: Shape,
    fill: Brush,
    border: Color,
    borderWidth: Dp,
    elevation: Dp,
    shadowColor: Color = C.ShadowSoft,
): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
    .background(fill, shape)
    .border(borderWidth, border, shape)

@Composable
fun Avatar(photo: ImageBitmap?, modifier: Modifier = Modifier) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFFE7F2F8), Color(0xFFD2E3EE)))),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.PersonFill, Color(0xFF9CB1C2), Modifier.fillMaxSize(0.6f))
        }
    }
}

/** Screen used for every page that is not the home screen. */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    bottomPadding: Dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Sheet)) {
        PageHeader(title, subtitle, onBack, actions)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 22.r, end = 22.r, top = 6.r, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.r),
            content = content,
        )
    }
}

/** The sky-tinted title bar used by every page (back button, title, subtitle, actions). */
@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(C.SkyTop, C.Sheet)))
            .statusBarsPadding()
            .padding(start = 26.r, end = 26.r, top = 30.r, bottom = 22.r)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                GlyphIcon(
                    Glyph.ChevronLeft,
                    C.Ink,
                    Modifier
                        .size(58.r)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                        .clickable(onClick = onBack)
                        .padding(14.r),
                    strokeWidth = 2.2f,
                )
                Spacer(Modifier.width(18.r))
            }
            Column(Modifier.weight(1f)) {
                RText(title, 36, FontWeight.Bold, C.Ink)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.r))
                    RText(subtitle, 20, FontWeight.Normal, C.StatLabelBn)
                }
            }
            actions()
        }
    }
}

/** White rounded card used in list pages. */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.r)
    var m = modifier
        .fillMaxWidth()
        .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.r, 5.r)
        .clip(shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(horizontal = 26.r, vertical = 22.r), content = content)
}

@Composable
fun SectionLabel(text: String) {
    RText(text, 21, FontWeight.SemiBold, C.StatLabel, Modifier.padding(start = 6.r, top = 10.r))
}

@Composable
fun EmptyText(text: String) {
    RText(
        text,
        20,
        FontWeight.Normal,
        C.Hint,
        Modifier.fillMaxWidth().padding(vertical = 30.r),
        maxLines = 3,
        align = TextAlign.Center,
    )
}

@Composable
fun TrashButton(onClick: () -> Unit) {
    GlyphIcon(
        Glyph.Trash,
        Color(0xFF97A3B2),
        Modifier.size(52.r).clip(CircleShape).clickable(onClick = onClick).padding(13.r),
        strokeWidth = 1.7f,
    )
}

/** Small round tick used for tasks and habits. */
@Composable
fun TickCircle(done: Boolean, diameter: Number, modifier: Modifier = Modifier, ringColor: Color = C.OpenRing) {
    val d = diameter.toFloat()
    if (done) {
        Box(
            modifier
                .size(d.r)
                .background(Brush.linearGradient(listOf(C.CheckA, C.CheckB)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Check, Color.White, Modifier.size((d * 0.68f).r), strokeWidth = 2.7f)
        }
    } else {
        Box(modifier.size(d.r).border((d * 0.06f).r, ringColor, CircleShape))
    }
}

/** Selectable pill used inside the add sheets. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0xFFE3F6EC) else Color(0xFFF1F4F7))
            .border(1.2.r, if (selected) Color(0xFF0BA765) else Color(0xFFE1E7ED), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.r, vertical = 12.r)
    ) {
        RText(label, 19, FontWeight.Medium, if (selected) Color(0xFF0A8F56) else C.StatLabel)
    }
}
