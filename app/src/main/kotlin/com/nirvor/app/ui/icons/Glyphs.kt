package com.nirvor.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Icons redrawn to match the reference artwork, on a 24 x 24 grid. */
enum class Glyph {
    Calendar, Check, ClockHands, Flame, Sun, Bell,
    Pill, CalendarFill, Bill, MoonStar,
    Wallet, Target, Drop, Book, Lotus, Phone, Moon, Star,
    Note, Home, TaskBox, WalletOutline, Person, PersonFill, Gear,
    ChevronRight, ChevronLeft, Plus, Trash, Camera, Shield, Info, Pin,
    Download, Globe, Link, Pause, Play, Close, Folder, Bolt, Refresh, Share, Film, Music, Image, FileDoc,
    Search, Dots, Clipboard, Engine, ArrowLeft, ArrowRight,
    Incognito, Exit, AdBlock, Binoculars, Layers, Grid, Qr, Cookie, External, Copy, Magnet, History, Code,
    Printer, Save, Desktop, Bookmark, BookmarkAdd, Filter, Sliders, AddBox, Edit, ChevronUp, ChevronDown,
    Lock, Eye, Coins, Swap, Gamepad,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Number = 1.8f,
    accent: Color = Color.Transparent,
) {
    Canvas(modifier) { drawGlyph(glyph, color, accent, strokeWidth.toFloat()) }
}

/** Path builder working in grid units. */
private class Pen(private val ox: Float, private val oy: Float, private val s: Float) {
    val path = Path()
    private var cx = 0f
    private var cy = 0f

    fun px(v: Number) = ox + v.toFloat() * s
    fun py(v: Number) = oy + v.toFloat() * s

    fun m(x: Number, y: Number) {
        path.moveTo(px(x), py(y)); cx = x.toFloat(); cy = y.toFloat()
    }

    fun l(x: Number, y: Number) {
        path.lineTo(px(x), py(y)); cx = x.toFloat(); cy = y.toFloat()
    }

    fun c(x1: Number, y1: Number, x2: Number, y2: Number, x: Number, y: Number) {
        path.cubicTo(px(x1), py(y1), px(x2), py(y2), px(x), py(y))
        cx = x.toFloat(); cy = y.toFloat()
    }

    /** Quadratic curve, written as the equivalent cubic. */
    fun q(qx: Number, qy: Number, x: Number, y: Number) {
        val qxf = qx.toFloat()
        val qyf = qy.toFloat()
        val xf = x.toFloat()
        val yf = y.toFloat()
        c(
            cx + 2f / 3f * (qxf - cx), cy + 2f / 3f * (qyf - cy),
            xf + 2f / 3f * (qxf - xf), yf + 2f / 3f * (qyf - yf),
            xf, yf,
        )
    }

    fun z() = path.close()

    fun oval(cxv: Number, cyv: Number, r: Number) {
        val rr = r.toFloat()
        path.addOval(
            Rect(
                Offset(px(cxv.toFloat() - rr), py(cyv.toFloat() - rr)),
                Offset(px(cxv.toFloat() + rr), py(cyv.toFloat() + rr)),
            )
        )
    }
}

fun DrawScope.drawGlyph(g: Glyph, color: Color, accent: Color, sw: Float) {
    val s = min(size.width, size.height) / 24f
    val ox = (size.width - 24f * s) / 2f
    val oy = (size.height - 24f * s) / 2f
    val k = Pen(ox, oy, s)
    val st = Stroke(width = sw * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun o(x: Number, y: Number) = Offset(k.px(x), k.py(y))
    fun path(block: Pen.() -> Unit): Path {
        val p = Pen(ox, oy, s)
        p.block()
        return p.path
    }
    fun line(x1: Number, y1: Number, x2: Number, y2: Number, c: Color = color, w: Float = sw) {
        drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = w * s, cap = StrokeCap.Round)
    }
    fun circle(cx: Number, cy: Number, r: Number, c: Color = color, stroke: Boolean = false, w: Float = sw) {
        drawCircle(c, r.toFloat() * s, o(cx, cy), style = if (stroke) Stroke(width = w * s) else Fill)
    }
    fun rrect(
        l: Number, t: Number, r: Number, b: Number, rad: Number,
        c: Color = color, stroke: Boolean = false, w: Float = sw,
    ) {
        drawRoundRect(
            color = c,
            topLeft = o(l, t),
            size = Size((r.toFloat() - l.toFloat()) * s, (b.toFloat() - t.toFloat()) * s),
            cornerRadius = CornerRadius(rad.toFloat() * s),
            style = if (stroke) Stroke(width = w * s, join = StrokeJoin.Round) else Fill,
        )
    }
    fun crescent(c1x: Number, c1y: Number, r1: Number, c2x: Number, c2y: Number, r2: Number): Path {
        val a = path { oval(c1x, c1y, r1) }
        val b = path { oval(c2x, c2y, r2) }
        val res = Path()
        res.op(a, b, PathOperation.Difference)
        return res
    }

    when (g) {
        Glyph.Calendar -> {
            rrect(3, 4.5, 21, 21, 3, stroke = true)
            line(3, 9.5, 21, 9.5)
            line(8, 2.5, 8, 6.5)
            line(16, 2.5, 16, 6.5)
            val dots = listOf(8.0 to 13.6, 12.0 to 13.6, 16.0 to 13.6, 8.0 to 17.2, 12.0 to 17.2)
            dots.forEach { (dx, dy) -> circle(dx, dy, 1.05) }
        }

        Glyph.Check -> drawPath(path { m(5.2, 12.6); l(9.9, 17.2); l(18.8, 7.4) }, color, style = st)

        Glyph.ClockHands -> drawPath(path { m(11.2, 5.0); l(11.2, 12.6); l(16.6, 17.4) }, color, style = st)

        Glyph.Flame -> {
            drawPath(
                path {
                    m(12.6, 2.4)
                    c(13.8, 6.2, 18.8, 8.6, 18.8, 14.6)
                    c(18.8, 18.6, 15.7, 21.6, 12.0, 21.6)
                    c(8.3, 21.6, 5.2, 18.6, 5.2, 14.8)
                    c(5.2, 11.8, 6.9, 9.7, 8.5, 8.4)
                    c(8.5, 10.4, 9.3, 11.9, 10.6, 12.5)
                    c(10.1, 8.6, 11.0, 5.0, 12.6, 2.4)
                    z()
                },
                color,
            )
            if (accent != Color.Transparent) {
                drawPath(
                    path {
                        m(12.0, 12.9)
                        c(12.7, 14.6, 14.9, 15.6, 14.9, 17.7)
                        c(14.9, 19.3, 13.6, 20.3, 12.0, 20.3)
                        c(10.4, 20.3, 9.1, 19.3, 9.1, 17.7)
                        c(9.1, 15.8, 11.2, 15.0, 12.0, 12.9)
                        z()
                    },
                    accent,
                )
            }
        }

        Glyph.Sun -> {
            if (accent != Color.Transparent) circle(12, 12, 5.3, c = accent)
            circle(12, 12, 5.3, stroke = true)
            for (i in 0 until 8) {
                val a = i * PI / 4.0
                line(12 + 8.2 * cos(a), 12 + 8.2 * sin(a), 12 + 10.6 * cos(a), 12 + 10.6 * sin(a))
            }
        }

        Glyph.Bell -> {
            drawPath(
                path {
                    m(12.0, 3.4)
                    c(8.7, 3.4, 6.7, 6.0, 6.7, 9.4)
                    l(6.7, 13.5)
                    l(4.9, 16.2)
                    c(4.5, 16.8, 4.9, 17.5, 5.6, 17.5)
                    l(18.4, 17.5)
                    c(19.1, 17.5, 19.5, 16.8, 19.1, 16.2)
                    l(17.3, 13.5)
                    l(17.3, 9.4)
                    c(17.3, 6.0, 15.3, 3.4, 12.0, 3.4)
                    z()
                },
                color,
            )
            circle(12, 2.9, 1.15)
            drawPath(path { m(9.9, 18.7); c(10.1, 20.0, 11.0, 20.8, 12.0, 20.8); c(13.0, 20.8, 13.9, 20.0, 14.1, 18.7); z() }, color)
        }

        Glyph.Pill -> {
            rotate(degrees = -45f, pivot = o(12, 12)) {
                rrect(2.6, 8.2, 21.4, 15.8, 3.8)
                if (accent != Color.Transparent) line(12, 8.6, 12, 15.4, c = accent, w = 1.1f)
            }
        }

        Glyph.CalendarFill -> {
            rrect(3.5, 5.0, 20.5, 20.8, 2.8)
            line(8, 2.8, 8, 6.8, w = 2.2f)
            line(16, 2.8, 16, 6.8, w = 2.2f)
            if (accent != Color.Transparent) {
                line(3.9, 9.6, 20.1, 9.6, c = accent, w = 1.0f)
                for (row in 0..1) for (col in 0..2) {
                    val l = 6.4 + col * 4.1
                    val t = 12.2 + row * 3.8
                    rrect(l, t, l + 2.6, t + 2.3, 0.6, c = accent)
                }
            }
        }

        Glyph.Bill -> {
            drawPath(
                path {
                    m(7.2, 3.0); l(14.2, 3.0); l(19.2, 8.0); l(19.2, 19.0)
                    q(19.2, 21.0, 17.2, 21.0); l(7.2, 21.0)
                    q(5.2, 21.0, 5.2, 19.0); l(5.2, 5.0)
                    q(5.2, 3.0, 7.2, 3.0); z()
                },
                color,
            )
            if (accent != Color.Transparent) {
                drawPath(path { m(14.2, 3.0); l(14.2, 7.0); q(14.2, 8.0, 15.2, 8.0); l(19.2, 8.0); z() }, accent)
                line(8.4, 12.6, 13.2, 12.6, c = accent, w = 1.3f)
                line(8.4, 16.2, 15.8, 16.2, c = accent, w = 1.3f)
            }
        }

        Glyph.MoonStar -> {
            drawPath(crescent(11.0, 13.0, 8.0, 15.4, 8.4, 6.6), color)
            drawPath(
                path {
                    m(18.2, 3.6); l(19.0, 5.8); l(21.2, 6.6); l(19.0, 7.4)
                    l(18.2, 9.6); l(17.4, 7.4); l(15.2, 6.6); l(17.4, 5.8); z()
                },
                color,
            )
        }

        Glyph.Wallet -> {
            drawPath(path { m(5.0, 7.0); l(15.6, 3.7); q(17.4, 3.1, 17.9, 4.9); l(18.4, 7.0); z() }, color.copy(alpha = 0.85f))
            rrect(2.8, 6.4, 21.2, 19.8, 3.0)
            if (accent != Color.Transparent) {
                rrect(14.4, 10.4, 21.2, 15.8, 2.6, c = accent)
                circle(16.8, 13.1, 1.05)
            }
        }

        Glyph.Target -> {
            circle(12, 12, 8.3, stroke = true)
            circle(12, 12, 4.6, stroke = true)
            circle(12, 12, 1.5)
            line(12, 12, 18.4, 5.6, w = sw * 0.9f)
            drawPath(path { m(16.3, 4.5); l(20.5, 3.5); l(19.5, 7.7); z() }, color)
        }

        Glyph.Drop -> {
            val p = path {
                m(12.0, 2.8)
                c(12.0, 2.8, 18.2, 9.8, 18.2, 14.4)
                c(18.2, 18.0, 15.4, 20.9, 12.0, 20.9)
                c(8.6, 20.9, 5.8, 18.0, 5.8, 14.4)
                c(5.8, 9.8, 12.0, 2.8, 12.0, 2.8)
                z()
            }
            drawPath(p, color)
            if (accent != Color.Transparent) drawPath(p, accent, style = Stroke(width = 1.1f * s))
        }

        Glyph.Book -> {
            drawPath(path { m(12, 7); c(10, 5.6, 7, 5.1, 3.4, 5.5); l(3.4, 18.2); c(7, 17.8, 10, 18.4, 12, 19.6) }, color, style = st)
            drawPath(path { m(12, 7); c(14, 5.6, 17, 5.1, 20.6, 5.5); l(20.6, 18.2); c(17, 17.8, 14, 18.4, 12, 19.6) }, color, style = st)
            line(12, 7, 12, 19.6)
        }

        Glyph.Lotus -> {
            drawPath(path { m(12, 5.6); c(14.4, 8, 14.6, 12.6, 12, 16); c(9.4, 12.6, 9.6, 8, 12, 5.6); z() }, color, style = st)
            drawPath(path { m(12, 16); c(10.3, 12, 7.4, 10.2, 4.4, 10.3); c(4.5, 13.7, 7.4, 16.1, 12, 16) }, color, style = st)
            drawPath(path { m(12, 16); c(13.7, 12, 16.6, 10.2, 19.6, 10.3); c(19.5, 13.7, 16.6, 16.1, 12, 16) }, color, style = st)
            drawPath(path { m(12, 16); c(8.4, 18.3, 4.6, 17.6, 2.6, 15.4) }, color, style = st)
            drawPath(path { m(12, 16); c(15.6, 18.3, 19.4, 17.6, 21.4, 15.4) }, color, style = st)
        }

        Glyph.Phone -> {
            rrect(6.8, 2.8, 17.2, 21.2, 2.4, stroke = true)
            line(10.6, 18.2, 13.4, 18.2)
        }

        Glyph.Moon -> drawPath(crescent(11.0, 12.8, 8.4, 16.4, 8.0, 7.2), color)

        Glyph.Star -> {
            drawPath(
                path {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) 9.2 else 4.0
                        val a = -PI / 2 + i * PI / 5
                        val x = 12 + r * cos(a)
                        val y = 12.8 + r * sin(a)
                        if (i == 0) m(x, y) else l(x, y)
                    }
                    z()
                },
                color,
                style = st,
            )
        }

        Glyph.Note -> {
            drawPath(
                path {
                    m(6.2, 4.4); l(17.8, 4.4); q(19.6, 4.4, 19.6, 6.2); l(19.6, 14.0)
                    l(14.0, 19.6); l(6.2, 19.6); q(4.4, 19.6, 4.4, 17.8); l(4.4, 6.2)
                    q(4.4, 4.4, 6.2, 4.4); z()
                },
                color,
                style = st,
            )
            drawPath(path { m(19.6, 14.0); l(15.6, 14.0); q(14.0, 14.0, 14.0, 15.6); l(14.0, 19.6) }, color, style = st)
            line(8.0, 9.0, 16.0, 9.0)
            line(8.0, 12.4, 12.8, 12.4)
        }

        Glyph.Home -> {
            drawPath(
                path {
                    m(10.5, 3.6)
                    q(12.0, 2.5, 13.5, 3.6)
                    l(20.0, 8.9)
                    q(21.0, 9.7, 21.0, 11.1)
                    l(21.0, 19.4)
                    q(21.0, 21.4, 19.0, 21.4)
                    l(15.2, 21.4)
                    l(15.2, 16.2)
                    q(15.2, 14.6, 13.6, 14.6)
                    l(10.4, 14.6)
                    q(8.8, 14.6, 8.8, 16.2)
                    l(8.8, 21.4)
                    l(5.0, 21.4)
                    q(3.0, 21.4, 3.0, 19.4)
                    l(3.0, 11.1)
                    q(3.0, 9.7, 4.0, 8.9)
                    z()
                },
                color,
            )
        }

        Glyph.TaskBox -> {
            rrect(3.4, 3.4, 20.6, 20.6, 3.0, stroke = true)
            drawPath(path { m(8.0, 12.2); l(11.0, 15.2); l(16.2, 9.4) }, color, style = st)
        }

        Glyph.WalletOutline -> {
            drawPath(path { m(4.6, 6.8); l(16.6, 3.7); q(18.4, 3.3, 18.8, 5.0); l(19.2, 6.6) }, color, style = st)
            rrect(2.6, 6.6, 21.0, 20.2, 3.0, stroke = true)
            rrect(15.4, 10.8, 21.6, 16.0, 2.2, stroke = true)
            circle(18.0, 13.4, 0.9)
        }

        Glyph.Person -> {
            circle(12, 7.6, 4.2, stroke = true)
            drawPath(
                path {
                    m(5.4, 21.0); q(4.6, 21.0, 4.6, 20.2); l(4.6, 19.4)
                    c(4.6, 15.9, 7.7, 13.8, 12.0, 13.8)
                    c(16.3, 13.8, 19.4, 15.9, 19.4, 19.4)
                    l(19.4, 20.2); q(19.4, 21.0, 18.6, 21.0); z()
                },
                color,
                style = st,
            )
        }

        Glyph.PersonFill -> {
            circle(12, 8.0, 4.6)
            drawPath(
                path {
                    m(3.6, 22.0); c(3.6, 16.8, 7.4, 14.2, 12.0, 14.2)
                    c(16.6, 14.2, 20.4, 16.8, 20.4, 22.0); z()
                },
                color,
            )
        }

        Glyph.Gear -> {
            drawPath(
                path {
                    for (i in 0 until 8) {
                        val a = i * PI / 4.0
                        val pts = listOf(
                            7.6 to a - 0.30,
                            10.0 to a - 0.19,
                            10.0 to a + 0.19,
                            7.6 to a + 0.30,
                        )
                        pts.forEachIndexed { j, (r, ang) ->
                            val x = 12 + r * cos(ang)
                            val y = 12 + r * sin(ang)
                            if (i == 0 && j == 0) m(x, y) else l(x, y)
                        }
                    }
                    z()
                },
                color,
                style = st,
            )
            circle(12, 12, 3.2, stroke = true)
        }

        Glyph.ChevronRight -> drawPath(path { m(9.0, 5.6); l(15.4, 12.0); l(9.0, 18.4) }, color, style = st)

        Glyph.ChevronLeft -> drawPath(path { m(15.0, 5.6); l(8.6, 12.0); l(15.0, 18.4) }, color, style = st)

        Glyph.Plus -> {
            line(12, 4.8, 12, 19.2)
            line(4.8, 12, 19.2, 12)
        }

        Glyph.Trash -> {
            line(4.4, 6.6, 19.6, 6.6)
            drawPath(path { m(9.4, 6.6); l(9.4, 4.8); q(9.4, 3.6, 10.6, 3.6); l(13.4, 3.6); q(14.6, 3.6, 14.6, 4.8); l(14.6, 6.6) }, color, style = st)
            drawPath(path { m(6.2, 6.6); l(7.1, 19.4); q(7.3, 21.0, 8.9, 21.0); l(15.1, 21.0); q(16.7, 21.0, 16.9, 19.4); l(17.8, 6.6) }, color, style = st)
            line(10.2, 10.6, 10.2, 17.0)
            line(13.8, 10.6, 13.8, 17.0)
        }

        Glyph.Shield -> {
            drawPath(
                path {
                    m(12.0, 2.8); l(19.4, 5.6); l(19.4, 11.4)
                    c(19.4, 15.9, 16.3, 19.5, 12.0, 21.2)
                    c(7.7, 19.5, 4.6, 15.9, 4.6, 11.4)
                    l(4.6, 5.6); z()
                },
                color,
                style = st,
            )
            drawPath(path { m(8.7, 12.0); l(11.0, 14.3); l(15.4, 9.8) }, color, style = st)
        }

        Glyph.Download -> {
            line(12, 3.6, 12, 15.2)
            drawPath(path { m(7.2, 10.6); l(12.0, 15.4); l(16.8, 10.6) }, color, style = st)
            drawPath(path { m(4.4, 15.8); l(4.4, 18.4); q(4.4, 20.4, 6.4, 20.4); l(17.6, 20.4); q(19.6, 20.4, 19.6, 18.4); l(19.6, 15.8) }, color, style = st)
        }

        Glyph.Globe -> {
            circle(12, 12, 9.0, stroke = true)
            line(3.2, 12, 20.8, 12)
            drawPath(path { m(12, 3); c(9.2, 5.6, 9.2, 18.4, 12, 21); c(14.8, 18.4, 14.8, 5.6, 12, 3) }, color, style = st)
        }

        Glyph.Link -> {
            drawPath(path { m(10.4, 13.6); c(11.6, 14.8, 13.4, 14.8, 14.6, 13.6); l(18.0, 10.2); c(19.2, 9.0, 19.2, 7.2, 18.0, 6.0); c(16.8, 4.8, 15.0, 4.8, 13.8, 6.0); l(12.6, 7.2) }, color, style = st)
            drawPath(path { m(13.6, 10.4); c(12.4, 9.2, 10.6, 9.2, 9.4, 10.4); l(6.0, 13.8); c(4.8, 15.0, 4.8, 16.8, 6.0, 18.0); c(7.2, 19.2, 9.0, 19.2, 10.2, 18.0); l(11.4, 16.8) }, color, style = st)
        }

        Glyph.Pause -> {
            rrect(6.6, 4.8, 10.2, 19.2, 1.4)
            rrect(13.8, 4.8, 17.4, 19.2, 1.4)
        }

        Glyph.Play -> drawPath(path { m(8.0, 5.0); l(19.0, 12.0); l(8.0, 19.0); z() }, color)

        Glyph.Close -> {
            line(6.2, 6.2, 17.8, 17.8)
            line(17.8, 6.2, 6.2, 17.8)
        }

        Glyph.Folder -> drawPath(
            path {
                m(3.4, 7.0); q(3.4, 5.0, 5.4, 5.0); l(9.6, 5.0); l(11.6, 7.2); l(18.6, 7.2)
                q(20.6, 7.2, 20.6, 9.2); l(20.6, 17.4); q(20.6, 19.4, 18.6, 19.4); l(5.4, 19.4)
                q(3.4, 19.4, 3.4, 17.4); z()
            },
            color,
            style = st,
        )

        Glyph.Bolt -> drawPath(path { m(13.4, 2.6); l(5.4, 13.4); l(11.4, 13.4); l(10.4, 21.4); l(18.6, 10.4); l(12.6, 10.4); z() }, color)

        Glyph.Refresh -> {
            drawPath(path { m(19.0, 12.0); c(19.0, 15.9, 15.9, 19.0, 12.0, 19.0); c(8.1, 19.0, 5.0, 15.9, 5.0, 12.0); c(5.0, 8.1, 8.1, 5.0, 12.0, 5.0); c(14.4, 5.0, 16.4, 6.2, 17.6, 8.0) }, color, style = st)
            drawPath(path { m(18.2, 3.8); l(18.0, 8.4); l(13.4, 8.2) }, color, style = st)
        }

        Glyph.Share -> {
            circle(17.4, 5.8, 2.6, stroke = true)
            circle(6.6, 12.0, 2.6, stroke = true)
            circle(17.4, 18.2, 2.6, stroke = true)
            line(8.9, 10.7, 15.1, 7.1)
            line(8.9, 13.3, 15.1, 16.9)
        }

        Glyph.Film -> {
            rrect(3.2, 5.6, 16.0, 18.4, 2.6, stroke = true)
            drawPath(path { m(16.0, 10.0); l(20.8, 7.2); l(20.8, 16.8); l(16.0, 14.0) }, color, style = st)
        }

        Glyph.Music -> {
            drawPath(path { m(9.4, 17.0); l(9.4, 5.6); l(19.0, 3.8); l(19.0, 15.2) }, color, style = st)
            circle(7.0, 17.2, 2.5)
            circle(16.6, 15.4, 2.5)
        }

        Glyph.Image -> {
            rrect(3.4, 4.6, 20.6, 19.4, 2.8, stroke = true)
            circle(8.8, 9.6, 1.7)
            drawPath(path { m(4.0, 17.6); l(9.4, 12.6); l(12.6, 15.4); l(15.4, 12.8); l(20.2, 17.0) }, color, style = st)
        }

        Glyph.FileDoc -> {
            drawPath(path { m(6.0, 2.8); l(14.0, 2.8); l(19.0, 7.8); l(19.0, 19.2); q(19.0, 21.2, 17.0, 21.2); l(6.0, 21.2); q(4.0, 21.2, 4.0, 19.2); l(4.0, 4.8); q(4.0, 2.8, 6.0, 2.8); z() }, color, style = st)
            drawPath(path { m(14.0, 2.8); l(14.0, 7.8); l(19.0, 7.8) }, color, style = st)
            line(7.8, 13.0, 15.2, 13.0)
            line(7.8, 16.6, 12.6, 16.6)
        }

        Glyph.Search -> {
            circle(10.6, 10.6, 6.4, stroke = true)
            line(15.4, 15.4, 20.2, 20.2)
        }

        Glyph.Dots -> {
            circle(12, 5.6, 1.6)
            circle(12, 12, 1.6)
            circle(12, 18.4, 1.6)
        }

        Glyph.Clipboard -> {
            rrect(5.0, 4.8, 19.0, 21.0, 2.6, stroke = true)
            rrect(8.6, 3.0, 15.4, 6.8, 1.4, stroke = true)
            line(8.6, 12.0, 15.4, 12.0)
            line(8.6, 15.6, 13.0, 15.6)
        }

        Glyph.Engine -> {
            rrect(5.0, 5.0, 19.0, 19.0, 3.0, stroke = true)
            rrect(9.0, 9.0, 15.0, 15.0, 1.2)
            for (i in 0..2) {
                val o = 8.4 + i * 3.6
                line(o, 2.4, o, 5.0); line(o, 19.0, o, 21.6); line(2.4, o, 5.0, o); line(19.0, o, 21.6, o)
            }
        }

        Glyph.ArrowLeft -> {
            line(19.0, 12.0, 5.4, 12.0)
            drawPath(path { m(11.0, 6.0); l(5.0, 12.0); l(11.0, 18.0) }, color, style = st)
        }

        Glyph.ArrowRight -> {
            line(5.0, 12.0, 18.6, 12.0)
            drawPath(path { m(13.0, 6.0); l(19.0, 12.0); l(13.0, 18.0) }, color, style = st)
        }

        Glyph.Pin -> {
            drawPath(
                path {
                    m(12.0, 21.4)
                    c(12.0, 21.4, 5.2, 14.9, 5.2, 9.6)
                    c(5.2, 5.8, 8.2, 2.8, 12.0, 2.8)
                    c(15.8, 2.8, 18.8, 5.8, 18.8, 9.6)
                    c(18.8, 14.9, 12.0, 21.4, 12.0, 21.4)
                    z()
                },
                color,
                style = st,
            )
            circle(12, 9.6, 2.6, stroke = true)
        }

        Glyph.Info -> {
            circle(12, 12, 9.2, stroke = true)
            line(12, 10.8, 12, 16.6)
            circle(12, 7.6, 1.15)
        }

        Glyph.Camera -> {
            drawPath(
                path {
                    m(5.0, 7.4); l(8.2, 7.4); l(9.8, 5.0); l(14.2, 5.0); l(15.8, 7.4); l(19.0, 7.4)
                    q(21.0, 7.4, 21.0, 9.4); l(21.0, 17.4); q(21.0, 19.4, 19.0, 19.4); l(5.0, 19.4)
                    q(3.0, 19.4, 3.0, 17.4); l(3.0, 9.4); q(3.0, 7.4, 5.0, 7.4); z()
                },
                color,
                style = st,
            )
            circle(12, 13.2, 3.4, stroke = true)
        }

        // ---------------------------------------------------------------- browser (v1.4)

        Glyph.Incognito -> {
            line(2.8, 10.6, 21.2, 10.6)
            drawPath(path { m(6.0, 10.6); l(7.6, 4.8); q(8.0, 3.8, 9.0, 4.2); l(12.0, 5.2); l(15.0, 4.2); q(16.0, 3.8, 16.4, 4.8); l(18.0, 10.6) }, color, style = st)
            circle(7.6, 16.2, 3.0, stroke = true)
            circle(16.4, 16.2, 3.0, stroke = true)
            drawPath(path { m(10.6, 16.0); q(12.0, 15.0, 13.4, 16.0) }, color, style = st)
        }

        Glyph.Exit -> {
            drawPath(path { m(13.0, 4.0); l(6.0, 4.0); q(4.4, 4.0, 4.4, 5.6); l(4.4, 18.4); q(4.4, 20.0, 6.0, 20.0); l(13.0, 20.0) }, color, style = st)
            line(10.0, 12.0, 20.0, 12.0)
            drawPath(path { m(16.6, 8.6); l(20.0, 12.0); l(16.6, 15.4) }, color, style = st)
        }

        Glyph.AdBlock -> {
            circle(12, 12, 8.8, stroke = true)
            line(5.8, 5.8, 18.2, 18.2)
        }

        Glyph.Binoculars -> {
            circle(7.0, 15.6, 3.8, stroke = true)
            circle(17.0, 15.6, 3.8, stroke = true)
            drawPath(path { m(3.6, 14.4); l(5.8, 6.0); q(6.2, 4.6, 7.6, 4.6); l(9.4, 4.6); q(10.4, 4.6, 10.4, 5.8); l(10.4, 13.6) }, color, style = st)
            drawPath(path { m(20.4, 14.4); l(18.2, 6.0); q(17.8, 4.6, 16.4, 4.6); l(14.6, 4.6); q(13.6, 4.6, 13.6, 5.8); l(13.6, 13.6) }, color, style = st)
            line(10.4, 10.2, 13.6, 10.2)
        }

        Glyph.Layers -> {
            drawPath(path { m(12.0, 3.6); l(21.0, 8.4); l(12.0, 13.2); l(3.0, 8.4); z() }, color, style = st)
            drawPath(path { m(3.0, 12.4); l(12.0, 17.2); l(21.0, 12.4) }, color, style = st)
            drawPath(path { m(3.0, 16.2); l(12.0, 21.0); l(21.0, 16.2) }, color, style = st)
        }

        Glyph.Grid -> {
            rrect(3.6, 3.6, 10.6, 10.6, 2.0, stroke = true)
            rrect(13.4, 3.6, 20.4, 10.6, 2.0, stroke = true)
            rrect(3.6, 13.4, 10.6, 20.4, 2.0, stroke = true)
            rrect(13.4, 13.4, 20.4, 20.4, 2.0, stroke = true)
        }

        Glyph.Qr -> {
            rrect(3.4, 3.4, 10.0, 10.0, 1.6, stroke = true)
            rrect(14.0, 3.4, 20.6, 10.0, 1.6, stroke = true)
            rrect(3.4, 14.0, 10.0, 20.6, 1.6, stroke = true)
            rrect(5.8, 5.8, 7.6, 7.6, 0.4)
            rrect(16.4, 5.8, 18.2, 7.6, 0.4)
            rrect(5.8, 16.4, 7.6, 18.2, 0.4)
            rrect(14.0, 14.0, 16.2, 16.2, 0.4)
            rrect(18.4, 14.0, 20.6, 16.2, 0.4)
            rrect(16.2, 16.2, 18.4, 18.4, 0.4)
            rrect(14.0, 18.4, 16.2, 20.6, 0.4)
            rrect(18.4, 18.4, 20.6, 20.6, 0.4)
        }

        Glyph.Cookie -> {
            drawPath(crescent(12.0, 12.0, 8.8, 19.6, 5.6, 3.6), color, style = st)
            circle(8.4, 9.6, 1.1)
            circle(13.4, 14.6, 1.1)
            circle(8.6, 15.4, 1.0)
            circle(12.8, 9.4, 0.9)
        }

        Glyph.External -> {
            drawPath(path { m(10.0, 4.4); l(6.0, 4.4); q(4.4, 4.4, 4.4, 6.0); l(4.4, 18.0); q(4.4, 19.6, 6.0, 19.6); l(18.0, 19.6); q(19.6, 19.6, 19.6, 18.0); l(19.6, 14.0) }, color, style = st)
            line(11.4, 12.6, 19.8, 4.2)
            drawPath(path { m(14.2, 4.2); l(19.8, 4.2); l(19.8, 9.8) }, color, style = st)
        }

        Glyph.Copy -> {
            rrect(8.4, 8.4, 20.0, 20.0, 2.4, stroke = true)
            drawPath(path { m(15.6, 8.4); l(15.6, 5.6); q(15.6, 4.0, 14.0, 4.0); l(5.6, 4.0); q(4.0, 4.0, 4.0, 5.6); l(4.0, 14.0); q(4.0, 15.6, 5.6, 15.6); l(8.4, 15.6) }, color, style = st)
        }

        Glyph.Magnet -> {
            drawPath(path { m(5.0, 4.0); l(5.0, 12.0); c(5.0, 16.2, 8.2, 19.4, 12.0, 19.4); c(15.8, 19.4, 19.0, 16.2, 19.0, 12.0); l(19.0, 4.0) }, color, style = st)
            drawPath(path { m(9.4, 4.0); l(9.4, 12.0); c(9.4, 13.6, 10.6, 14.8, 12.0, 14.8); c(13.4, 14.8, 14.6, 13.6, 14.6, 12.0); l(14.6, 4.0) }, color, style = st)
            line(5.0, 4.0, 9.4, 4.0)
            line(14.6, 4.0, 19.0, 4.0)
            line(5.0, 8.0, 9.4, 8.0)
            line(14.6, 8.0, 19.0, 8.0)
        }

        Glyph.History -> {
            drawPath(
                path {
                    m(4.6, 12.0); c(4.6, 7.9, 7.9, 4.6, 12.0, 4.6); c(16.1, 4.6, 19.4, 7.9, 19.4, 12.0)
                    c(19.4, 16.1, 16.1, 19.4, 12.0, 19.4); c(9.4, 19.4, 7.1, 18.0, 5.8, 15.9)
                },
                color,
                style = st,
            )
            drawPath(path { m(2.6, 10.0); l(4.6, 12.2); l(6.8, 10.2) }, color, style = st)
            drawPath(path { m(12.0, 8.0); l(12.0, 12.2); l(14.8, 14.0) }, color, style = st)
        }

        Glyph.Code -> {
            drawPath(path { m(8.4, 7.2); l(3.6, 12.0); l(8.4, 16.8) }, color, style = st)
            drawPath(path { m(15.6, 7.2); l(20.4, 12.0); l(15.6, 16.8) }, color, style = st)
            line(13.4, 5.4, 10.6, 18.6)
        }

        Glyph.Printer -> {
            drawPath(path { m(7.0, 8.4); l(7.0, 4.4); q(7.0, 3.6, 7.8, 3.6); l(16.2, 3.6); q(17.0, 3.6, 17.0, 4.4); l(17.0, 8.4) }, color, style = st)
            drawPath(path { m(7.0, 17.0); l(5.0, 17.0); q(3.4, 17.0, 3.4, 15.4); l(3.4, 10.0); q(3.4, 8.4, 5.0, 8.4); l(19.0, 8.4); q(20.6, 8.4, 20.6, 10.0); l(20.6, 15.4); q(20.6, 17.0, 19.0, 17.0); l(17.0, 17.0) }, color, style = st)
            rrect(7.0, 13.4, 17.0, 20.4, 0.8, stroke = true)
            circle(17.4, 11.4, 0.9)
        }

        Glyph.Save -> {
            drawPath(path { m(5.6, 3.6); l(16.0, 3.6); l(20.4, 8.0); l(20.4, 18.4); q(20.4, 20.4, 18.4, 20.4); l(5.6, 20.4); q(3.6, 20.4, 3.6, 18.4); l(3.6, 5.6); q(3.6, 3.6, 5.6, 3.6); z() }, color, style = st)
            drawPath(path { m(7.6, 3.6); l(7.6, 8.0); l(14.6, 8.0); l(14.6, 3.6) }, color, style = st)
            rrect(7.0, 13.0, 17.0, 20.4, 1.0, stroke = true)
        }

        Glyph.Desktop -> {
            rrect(2.8, 4.0, 21.2, 16.0, 2.0, stroke = true)
            line(12, 16, 12, 20)
            line(8, 20, 16, 20)
        }

        Glyph.Bookmark -> drawPath(
            path { m(6.4, 4.6); q(6.4, 3.4, 7.6, 3.4); l(16.4, 3.4); q(17.6, 3.4, 17.6, 4.6); l(17.6, 20.6); l(12.0, 16.4); l(6.4, 20.6); z() },
            color,
            style = st,
        )

        Glyph.BookmarkAdd -> {
            drawPath(
                path { m(6.4, 4.6); q(6.4, 3.4, 7.6, 3.4); l(16.4, 3.4); q(17.6, 3.4, 17.6, 4.6); l(17.6, 20.6); l(12.0, 16.4); l(6.4, 20.6); z() },
                color,
                style = st,
            )
            line(12.0, 7.2, 12.0, 12.8)
            line(9.2, 10.0, 14.8, 10.0)
        }

        Glyph.Filter -> drawPath(path { m(3.6, 4.6); l(20.4, 4.6); l(14.0, 12.4); l(14.0, 19.0); l(10.0, 20.6); l(10.0, 12.4); z() }, color, style = st)

        Glyph.Sliders -> {
            line(4, 7, 20, 7)
            circle(9.0, 7.0, 2.4)
            line(4, 12, 20, 12)
            circle(15.0, 12.0, 2.4)
            line(4, 17, 20, 17)
            circle(8.0, 17.0, 2.4)
        }

        Glyph.AddBox -> {
            rrect(3.6, 3.6, 20.4, 20.4, 3.4, stroke = true)
            line(12, 8, 12, 16)
            line(8, 12, 16, 12)
        }

        Glyph.Edit -> {
            drawPath(path { m(4.0, 20.0); l(4.6, 16.4); l(15.6, 5.4); q(16.6, 4.4, 17.6, 5.4); l(18.6, 6.4); q(19.6, 7.4, 18.6, 8.4); l(7.6, 19.4); z() }, color, style = st)
            line(13.8, 7.2, 16.8, 10.2)
        }

        Glyph.ChevronUp -> drawPath(path { m(5.6, 15.0); l(12.0, 8.6); l(18.4, 15.0) }, color, style = st)

        Glyph.ChevronDown -> drawPath(path { m(5.6, 9.0); l(12.0, 15.4); l(18.4, 9.0) }, color, style = st)

        Glyph.Lock -> {
            rrect(5.0, 10.4, 19.0, 20.6, 2.4, stroke = true)
            drawPath(path { m(8.0, 10.4); l(8.0, 7.6); c(8.0, 5.2, 9.8, 3.6, 12.0, 3.6); c(14.2, 3.6, 16.0, 5.2, 16.0, 7.6); l(16.0, 10.4) }, color, style = st)
            circle(12.0, 15.4, 1.3)
        }

        Glyph.Eye -> {
            drawPath(
                path {
                    m(2.6, 12.0); c(5.0, 7.4, 8.4, 5.4, 12.0, 5.4); c(15.6, 5.4, 19.0, 7.4, 21.4, 12.0)
                    c(19.0, 16.6, 15.6, 18.6, 12.0, 18.6); c(8.4, 18.6, 5.0, 16.6, 2.6, 12.0); z()
                },
                color,
                style = st,
            )
            circle(12, 12, 3.0, stroke = true)
        }

        // Income: a coin with a small stack of coins under it.
        Glyph.Coins -> {
            circle(15.2, 8.4, 5.6, stroke = true)
            circle(15.2, 8.4, 2.3, stroke = true, w = sw * 0.8f)
            rrect(3.0, 14.6, 13.4, 17.8, 1.6, stroke = true)
            rrect(3.0, 17.8, 13.4, 21.0, 1.6, stroke = true)
            rrect(15.4, 17.8, 21.0, 21.0, 1.6, stroke = true)
        }

        // দেনা-পাওনা: money going out and coming back.
        Glyph.Swap -> {
            line(4.2, 8.0, 18.6, 8.0)
            drawPath(path { m(15.0, 4.2); l(19.0, 8.0); l(15.0, 11.8) }, color, style = st)
            line(19.8, 16.0, 5.4, 16.0)
            drawPath(path { m(9.0, 12.2); l(5.0, 16.0); l(9.0, 19.8) }, color, style = st)
        }

        // Gamepad: game controller
        Glyph.Gamepad -> {
            rrect(2.5, 6.5, 21.5, 17.5, 4.5, stroke = true)
            line(7.0, 9.5, 7.0, 14.5)
            line(4.5, 12.0, 9.5, 12.0)
            circle(16.5, 10.2, 1.1)
            circle(18.8, 12.0, 1.1)
            circle(16.5, 13.8, 1.1)
            circle(14.2, 12.0, 1.1)
        }
    }
}
