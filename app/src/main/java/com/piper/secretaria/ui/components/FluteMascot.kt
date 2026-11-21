package com.piper.secretaria.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.piper.secretaria.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Posição (fração da imagem) dos olhos e da boca, para piscar e falar por cima da arte. */
private class Face(
    val leftEye: Offset, val leftR: Size,
    val rightEye: Offset, val rightR: Size,
    val mouth: Offset, val mouthSize: Size,
)

private val FULL_FACE = Face(
    Offset(0.5064f, 0.2767f), Size(0.0303f, 0.0271f),
    Offset(0.5901f, 0.2648f), Size(0.0240f, 0.0263f),
    Offset(0.5598f, 0.3325f), Size(0.0678f, 0.0439f),
)
private val HEAD_FACE = Face(
    Offset(0.5068f, 0.4689f), Size(0.0514f, 0.0459f),
    Offset(0.6486f, 0.4486f), Size(0.0405f, 0.0446f),
    Offset(0.5973f, 0.5635f), Size(0.1149f, 0.0743f),
)

private val Skin = Color(0xFFF5BD78)
private val Lash = Color(0xFF4A2410)

/**
 * A Piper: flauta-maga de chapéu roxo, capa e botas (arte do usuário), com animações interativas:
 * acena ao aparecer, pisca, mexe a boca ao falar, se inclina para onde você toca,
 * pula/gira e solta faíscas ao toque, "apresenta" quando ouve e comemora quando [celebrate] muda.
 *
 * [avatar] = true usa só o rosto (para tamanhos pequenos).
 */
@Composable
fun FluteMascot(
    modifier: Modifier = Modifier,
    talking: Boolean = false,
    listening: Boolean = false,
    celebrate: Int = 0,
    avatar: Boolean = false,
    onTap: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val inf = rememberInfiniteTransition(label = "piper")
    val bob by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val mouth by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse), label = "mouth")
    val blink by inf.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 4300; 0f at 0; 0f at 4000; 1f at 4080; 1f at 4140; 0f at 4230 }),
        label = "blink",
    )
    val glow by inf.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")

    val jump = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val sparkle = remember { Animatable(1f) }
    var sparkleSeed by remember { mutableIntStateOf(0) }
    var lean by remember { mutableFloatStateOf(0f) }
    val leanAnim by animateFloatAsState(lean + if (listening) -6f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "lean")

    suspend fun burst() {
        sparkleSeed++
        sparkle.snapTo(0f)
        sparkle.animateTo(1f, tween(900))
    }

    // Aceno ao aparecer
    LaunchedEffect(Unit) {
        delay(250)
        for (t in listOf(-9f, 8f, -6f, 5f, 0f)) wave.animateTo(t, tween(140))
    }
    // Comemoração (ex.: tarefa concluída)
    LaunchedEffect(celebrate) {
        if (celebrate == 0) return@LaunchedEffect
        launch { burst() }
        repeat(2) {
            jump.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
            jump.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
        }
    }

    val face = if (avatar) HEAD_FACE else FULL_FACE
    val shape = if (avatar) CircleShape else RoundedCornerShape(28.dp)

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { p ->
                        // inclina na direção do toque ("segue" o dedo)
                        lean = ((p.x - size.width / 2f) / size.width) * 16f
                        tryAwaitRelease()
                        lean = 0f
                    },
                    onTap = {
                        onTap?.invoke()
                        scope.launch { burst() }
                        scope.launch {
                            jump.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
                            jump.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                        scope.launch {
                            spin.snapTo(0f)
                            spin.animateTo(360f, tween(650, easing = FastOutSlowInEasing))
                            spin.snapTo(0f)
                        }
                    },
                )
            }
            .drawWithContent {
                // brilho mágico atrás
                val pulse = if (listening || talking) glow else 0.9f
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x99A855F7), Color.Transparent), radius = size.minDimension * 0.55f * pulse),
                    radius = size.minDimension * 0.55f * pulse,
                )
                drawContent()
                drawSparkles(sparkle.value, sparkleSeed)
                if (listening) drawListeningRing(glow)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (bob * 0.02f - jump.value * 0.14f) * size.height
                    rotationZ = wave.value + leanAnim + if (talking) (mouth - 0.5f) * 2f else 0f
                    rotationY = spin.value
                    cameraDistance = 12f * density
                    val squash = if (talking) 1f + (mouth - 0.5f) * 0.02f else 1f
                    scaleY = squash
                    scaleX = 2f - squash
                }
                .clip(shape)
                .drawWithContent {
                    drawContent()
                    if (talking) drawMouth(face, mouth)
                    if (blink > 0f && !talking) drawBlink(face, blink)
                },
        ) {
            Image(
                painterResource(if (avatar) R.drawable.piper_head else R.drawable.piper_mascot),
                contentDescription = "Piper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun DrawScope.drawBlink(f: Face, amount: Float) {
    listOf(f.leftEye to f.leftR, f.rightEye to f.rightR).forEach { (c, r) ->
        val center = Offset(c.x * size.width, c.y * size.height)
        val rx = r.width * size.width * 1.25f
        val ry = r.height * size.height * 1.3f * amount
        drawOval(Skin, topLeft = Offset(center.x - rx, center.y - r.height * size.height * 1.3f), size = Size(rx * 2, ry * 2))
        drawArc(
            Lash, 10f, 160f, false,
            topLeft = Offset(center.x - rx * 0.9f, center.y - r.height * size.height * 1.3f + ry * 2 - ry * 0.6f),
            size = Size(rx * 1.8f, ry * 0.9f + 1f), style = Stroke(width = size.width * 0.006f, cap = StrokeCap.Round),
        )
    }
}

/** Fecha e abre a boca rapidamente por cima da arte (lábio cobrindo parte da boca aberta). */
private fun DrawScope.drawMouth(f: Face, phase: Float) {
    val c = Offset(f.mouth.x * size.width, f.mouth.y * size.height)
    val w = f.mouthSize.width * size.width * 1.2f
    val h = f.mouthSize.height * size.height * 1.25f
    val cover = 0.25f + 0.75f * phase // quanto da boca fica coberto
    drawOval(Skin, topLeft = Offset(c.x - w / 2, c.y - h / 2), size = Size(w, h * cover))
    if (cover > 0.8f) {
        drawArc(
            Lash, 15f, 150f, false,
            topLeft = Offset(c.x - w * 0.42f, c.y - h * 0.35f), size = Size(w * 0.84f, h * 0.6f),
            style = Stroke(width = size.width * 0.007f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSparkles(progress: Float, seed: Int) {
    if (progress >= 1f || seed == 0) return
    val rnd = Random(seed)
    val center = Offset(size.width / 2, size.height * 0.45f)
    repeat(14) {
        val angle = rnd.nextDouble(0.0, 2 * Math.PI)
        val dist = size.minDimension * (0.25f + 0.3f * rnd.nextFloat()) * progress
        val p = center + Offset((cos(angle) * dist).toFloat(), (sin(angle) * dist).toFloat())
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val r = size.minDimension * (0.012f + 0.012f * rnd.nextFloat())
        val color = if (rnd.nextBoolean()) Color(0xFFE9D5FF) else Color(0xFFFFD166)
        drawCircle(color.copy(alpha = alpha), radius = r, center = p)
        drawLine(color.copy(alpha = alpha * 0.8f), p - Offset(r * 2, 0f), p + Offset(r * 2, 0f), strokeWidth = r * 0.5f)
        drawLine(color.copy(alpha = alpha * 0.8f), p - Offset(0f, r * 2), p + Offset(0f, r * 2), strokeWidth = r * 0.5f)
    }
}

private fun DrawScope.drawListeningRing(pulse: Float) {
    drawCircle(
        Color(0x88C084FC), radius = size.minDimension * 0.48f * pulse,
        style = Stroke(width = size.minDimension * 0.015f),
    )
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
