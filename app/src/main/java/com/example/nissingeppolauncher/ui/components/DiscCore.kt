package com.example.nissingeppolauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.nissingeppolauncher.data.AppPreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * アプリ円盤（RotatableAppDisc）と同エンジンの、中身だけ差し替えられる円盤。
 * 回転・ドラッグ・スナップ・フェード・ハプティクス・輪郭描画はアプリ円盤からそのまま移植した。
 * アプリ円盤本体には一切手を入れていないので、アプリ一覧のデザイン・操作感は変わらない。
 * 項目の中身（アイコンやラベルの描画）は itemContent で呼び出し側が差し替える。
 */
class DiscCarouselState internal constructor(
    private val scope: CoroutineScope,
    initialIndex: Int,
    stepAngle: Float
) {
    val rotationAnim = Animatable(-initialIndex * stepAngle)
    var rawRotation by mutableFloatStateOf(-initialIndex * stepAngle)
    var isScrolling by mutableStateOf(false)
    var itemCount: Int = 0
    var stepAngle: Float = stepAngle

    /** タップなどで指定の項目まで回す（アプリ円盤のドラッグ終了時と同じバネ） */
    fun animateToIndex(index: Int) {
        if (itemCount <= 0) return
        val minRotation = -((itemCount - 1) * stepAngle).coerceAtLeast(0f)
        val target = (-index * stepAngle).coerceIn(minRotation, 0f)
        rawRotation = target
        scope.launch {
            rotationAnim.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = 220f, dampingRatio = 0.65f)
            )
            isScrolling = false
        }
    }
}

@Composable
fun rememberDiscCarouselState(initialIndex: Int = 0, stepAngle: Float = 20f): DiscCarouselState {
    val scope = rememberCoroutineScope()
    return remember { DiscCarouselState(scope, initialIndex, stepAngle) }
}

@Composable
fun DiscCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    stepAngle: Float = 20f,
    isRightDisc: Boolean = true,
    isLoopable: Boolean = false,
    state: DiscCarouselState = rememberDiscCarouselState(0, stepAngle),
    onSelectedIndexChange: (Int) -> Unit = {},
    centerContent: (@Composable BoxScope.() -> Unit)? = null,
    itemContent: @Composable (index: Int, rad: Float, alpha: Float, isSelected: Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val isReverseScroll = prefManager.isReverseScroll()

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    state.itemCount = itemCount
    state.stepAngle = stepAngle

    val baseCenterAngle = if (isRightDisc) 180f else 0f

    val minRotation = if (itemCount > 0) -((itemCount - 1) * stepAngle).coerceAtLeast(0f) else 0f
    val maxRotation = 0f

    val scrollAlpha by animateFloatAsState(
        targetValue = if (state.isScrolling) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "DiscCarouselScrollAlpha"
    )

    // 項目数が減ったら範囲内に安全クランプ（アプリ円盤と同じ）
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && !isLoopable) {
            val minRot = -((itemCount - 1) * stepAngle).coerceAtLeast(0f)
            if (state.rawRotation < minRot) {
                state.rawRotation = minRot
                coroutineScope.launch { state.rotationAnim.snapTo(minRot) }
            }
        }
    }

    val currentRotation = state.rotationAnim.value

    val selectedIndex = remember(currentRotation, itemCount, isLoopable) {
        if (itemCount <= 0) -1
        else {
            val rawIdx = (-currentRotation / stepAngle).roundToInt()
            if (isLoopable) ((rawIdx % itemCount) + itemCount) % itemCount
            else rawIdx.coerceIn(0, itemCount - 1)
        }
    }

    var lastHapticIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1 && selectedIndex != lastHapticIndex && lastHapticIndex != -1) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = selectedIndex
        if (selectedIndex != -1) onSelectedIndexChange(selectedIndex)
    }

    val centerPx = with(density) { Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx()) }
    val radiusPx = with(density) { discRadiusDp.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(itemCount, discCenterXDp, discCenterYDp, discRadiusDp, isRightDisc, isLoopable) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pos = down.position

                        val isTouchInTargetHalf = if (isRightDisc) {
                            pos.x >= size.width / 2f
                        } else {
                            pos.x < size.width / 2f
                        }
                        if (!isTouchInTargetHalf) continue

                        val distFromCenter = sqrt((pos.x - centerPx.x).pow(2) + (pos.y - centerPx.y).pow(2))
                        val isInsideCircle = distFromCenter <= radiusPx

                        var currentPos = pos
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragStarted = false
                        var lastDeltaAngle = 0f
                        var lastTime = System.currentTimeMillis()

                        drag(down.id) { change: PointerInputChange ->
                            val prevPos = currentPos
                            currentPos = change.position
                            val currentTime = System.currentTimeMillis()

                            val dx = currentPos.x - prevPos.x
                            val dy = currentPos.y - prevPos.y
                            totalDx += dx
                            totalDy += dy

                            if (!isDragStarted) {
                                if (isInsideCircle && abs(totalDx) > abs(totalDy) && abs(totalDx) > 10f) {
                                    return@drag
                                }
                                if (abs(totalDy) > 6f || abs(totalDx) > 6f || !isInsideCircle) {
                                    isDragStarted = true
                                    state.isScrolling = true
                                }
                            }

                            if (isDragStarted) {
                                change.consume()

                                val dyPx = currentPos.y - prevPos.y
                                val directionFactor = if (isReverseScroll) -1f else 1f
                                val deltaAngle = -(dyPx / radiusPx) * (180f / PI.toFloat()) * (if (isRightDisc) 1.2f else -1.2f) * directionFactor

                                val dt = (currentTime - lastTime).coerceAtLeast(1)
                                lastDeltaAngle = deltaAngle / dt * 16f
                                lastTime = currentTime

                                val targetAngle = state.rawRotation + deltaAngle
                                val constrainedAngle = if (isLoopable) {
                                    targetAngle
                                } else {
                                    targetAngle.coerceIn(minRotation - 20f, maxRotation + 20f)
                                }
                                state.rawRotation = constrainedAngle
                                coroutineScope.launch {
                                    state.rotationAnim.snapTo(constrainedAngle)
                                }
                            }
                        }

                        if (isDragStarted && itemCount > 0) {
                            if (isLoopable) {
                                val inertiaTarget = state.rawRotation + lastDeltaAngle * 10f
                                val nearestIndex = (-inertiaTarget / stepAngle).roundToInt()
                                val finalSnapAngle = -nearestIndex * stepAngle

                                state.rawRotation = finalSnapAngle
                                coroutineScope.launch {
                                    state.rotationAnim.animateTo(
                                        targetValue = finalSnapAngle,
                                        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                                    )
                                    state.isScrolling = false
                                }
                            } else {
                                val inertiaTarget = state.rawRotation + lastDeltaAngle * 6f
                                val nearestIndex = (-inertiaTarget / stepAngle).roundToInt()
                                val finalSnapAngle = (-nearestIndex * stepAngle).coerceIn(minRotation, maxRotation)

                                state.rawRotation = finalSnapAngle
                                coroutineScope.launch {
                                    state.rotationAnim.animateTo(
                                        targetValue = finalSnapAngle,
                                        animationSpec = spring(stiffness = 220f, dampingRatio = 0.65f)
                                    )
                                    state.isScrolling = false
                                }
                            }
                        } else {
                            state.isScrolling = false
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = discRadiusDp.dp.toPx(),
                    center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
            }

            if (itemCount <= 0) return@Box

            val textAlpha = 1.0f

            if (isLoopable) {
                val minK = floor((-80f - currentRotation) / stepAngle).toInt() - 1
                val maxK = ceil((80f - currentRotation) / stepAngle).toInt() + 1

                for (k in minK..maxK) {
                    val itemAngle = baseCenterAngle + (k * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val itemIndex = ((k % itemCount) + itemCount) % itemCount
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val finalAlpha = textAlpha * scrollAlpha * edgeFade

                        itemContent(itemIndex, rad, finalAlpha, itemIndex == selectedIndex)
                    }
                }
            } else {
                for (index in 0 until itemCount) {
                    val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val finalAlpha = textAlpha * scrollAlpha * edgeFade

                        itemContent(index, rad, finalAlpha, index == selectedIndex)
                    }
                }
            }

            if (centerContent != null) {
                val previewCx = if (isRightDisc) discCenterXDp - 128f else discCenterXDp
                Box(
                    modifier = Modifier
                        .offset(x = (previewCx - 52f).dp, y = (discCenterYDp - 52f).dp)
                        .size(104.dp),
                    contentAlignment = Alignment.Center,
                    content = centerContent
                )
            }
        }
    }
}
