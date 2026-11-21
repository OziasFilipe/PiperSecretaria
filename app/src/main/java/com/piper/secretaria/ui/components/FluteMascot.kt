package com.piper.secretaria.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * A Piper: uma flauta simpática com olhos e boca.
 * [talking] anima a boca (fala), [listening] arregala os olhos e faz notas "vibrarem".
 */
@Composable
fun FluteMascot(modifier: Modifier = Modifier, talking: Boolean = false, listening: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "piper")
    val bob by inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val mouth by inf.animateFloat(
        0.1f, 1f, infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "mouth",
    )
    val blink by inf.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0
                0f at 3900
                1f at 4000
                0f at 4120
            },
        ),
        label = "blink",
    )
    val wiggle by inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse), label = "wiggle",
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2 + bob * h * 0.025f
        val tilt = -24f + if (talking) wiggle * 2.5f else 0f

        // brilho atrás
        drawCircle(
            Brush.radialGradient(listOf(Color(0x668B5CF6), Color.Transparent), center = Offset(cx, cy), radius = w * 0.5f),
            radius = w * 0.5f, center = Offset(cx, cy),
        )

        rotate(tilt, pivot = Offset(cx, cy)) {
            val bodyH = h * 0.27f
            val left = w * 0.04f
            val right = w * 0.96f
            val top = cy - bodyH / 2

            // sombra
            drawRoundRect(
                Color(0x33000000), topLeft = Offset(left + 4f, top + 8f), size = Size(right - left, bodyH),
                cornerRadius = CornerRadius(bodyH / 2),
            )
            // corpo
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFDDD6FE), Color(0xFF8B5CF6)), startY = top, endY = top + bodyH,
                ),
                topLeft = Offset(left, top), size = Size(right - left, bodyH), cornerRadius = CornerRadius(bodyH / 2),
            )
            // anéis das junções
            listOf(0.25f, 0.71f).forEach { x ->
                drawRect(Color(0x556D28D9), topLeft = Offset(w * x, top), size = Size(w * 0.022f, bodyH))
            }
            // bocal (embocadura) na ponta esquerda
            drawOval(Color(0xFF6D28D9), topLeft = Offset(w * 0.11f, cy - bodyH * 0.12f), size = Size(bodyH * 0.34f, bodyH * 0.22f))
            // reflexo
            drawRoundRect(
                Color.White.copy(alpha = 0.7f), topLeft = Offset(left + bodyH * 0.5f, top + bodyH * 0.1f),
                size = Size((right - left) * 0.78f, bodyH * 0.1f), cornerRadius = CornerRadius(bodyH * 0.05f),
            )
            // furos/chaves à direita
            repeat(4) { i ->
                val hc = Offset(w * (0.765f + i * 0.05f), cy + bodyH * 0.06f)
                drawCircle(Color(0xFFC4B5FD), radius = bodyH * 0.12f, center = hc)
                drawCircle(Color(0xFF4C1D95), radius = bodyH * 0.075f, center = hc)
            }

            // rosto
            val fx = w * 0.48f
            val eyeY = cy - bodyH * 0.07f
            val eyeR = bodyH * (if (listening) 0.23f else 0.2f)
            listOf(fx - bodyH * 0.42f, fx + bodyH * 0.42f).forEach { ex ->
                val c = Offset(ex, eyeY)
                scale(scaleX = 1f, scaleY = 1f - blink * 0.92f, pivot = c) {
                    drawCircle(Color.White, radius = eyeR, center = c)
                    drawCircle(Color(0xFF2E1065), radius = eyeR, center = c, style = Stroke(width = bodyH * 0.03f))
                    val pupil = Offset(ex + eyeR * 0.12f, eyeY - eyeR * 0.05f)
                    drawCircle(Color(0xFF2E1065), radius = eyeR * 0.58f, center = pupil)
                    drawCircle(Color.White, radius = eyeR * 0.2f, center = pupil + Offset(eyeR * 0.18f, -eyeR * 0.2f))
                }
            }
            // bochechas
            drawCircle(Color(0x55EC4899), radius = bodyH * 0.1f, center = Offset(fx - bodyH * 0.78f, cy + bodyH * 0.14f))
            drawCircle(Color(0x55EC4899), radius = bodyH * 0.1f, center = Offset(fx + bodyH * 0.78f, cy + bodyH * 0.14f))

            // boca
            val my = cy + bodyH * 0.25f
            if (talking) {
                val mw = bodyH * 0.34f
                val mh = bodyH * (0.06f + 0.18f * mouth)
                drawOval(Color(0xFF3B0764), topLeft = Offset(fx - mw / 2, my - mh / 2), size = Size(mw, mh))
                drawOval(
                    Color(0xFFF472B6), topLeft = Offset(fx - mw * 0.25f, my + mh * 0.05f),
                    size = Size(mw * 0.5f, mh * 0.4f),
                )
            } else {
                val mw = bodyH * 0.36f
                drawArc(
                    Color(0xFF3B0764), startAngle = 15f, sweepAngle = 150f, useCenter = false,
                    topLeft = Offset(fx - mw / 2, my - mw * 0.55f), size = Size(mw, mw * 0.7f),
                    style = Stroke(width = bodyH * 0.06f, cap = StrokeCap.Round),
                )
            }
        }

        // notinhas musicais quando fala ou ouve
        if (talking || listening) {
            val phase = (bob + 1f) / 2f
            listOf(0.82f to 0.12f, 0.92f to 0.3f, 0.12f to 0.78f).forEachIndexed { i, (nx, ny) ->
                val p = Offset(w * nx, h * ny - phase * h * 0.04f * (i + 1))
                drawCircle(Color(0xFFC4B5FD), radius = w * 0.022f, center = p)
                drawLine(
                    Color(0xFFC4B5FD), p + Offset(w * 0.02f, 0f), p + Offset(w * 0.02f, -h * 0.07f),
                    strokeWidth = w * 0.01f,
                )
            }
        }
    }
}

/** Ondas de áudio animadas (mostradas enquanto a Piper escuta). */
@Composable
fun SoundWave(active: Boolean, modifier: Modifier = Modifier, barCount: Int = 24, color: Color = Color(0xFF8B5CF6)) {
    val inf = rememberInfiniteTransition(label = "wave")
    val t by inf.animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t",
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(barCount) { i ->
            val amp = if (active) 0.25f + 0.75f * abs(sin(t + i * 0.55f)) * (0.5f + 0.5f * abs(sin(i * 1.7f))) else 0.18f
            Bar(heightDp = 36.dp * amp, color = color.copy(alpha = 0.45f + 0.55f * amp))
        }
    }
}

@Composable
private fun Bar(heightDp: Dp, color: Color) {
    Box(
        Modifier
            .width(4.dp)
            .height(heightDp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}
