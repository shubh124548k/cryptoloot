package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

fun Modifier.scrollGestureSafe(): Modifier = composed {
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            waitForUpOrCancellation()
        }
    }
}

fun Modifier.threeDTiltEffect(): Modifier = composed {
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var size = remember { IntSize.Zero }

    this
        .onGloballyPositioned { coordinates ->
            size = coordinates.size
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                
                if (width > 0 && height > 0) {
                    val updateTilt = { position: Offset ->
                        val pctX = ((position.x - (width / 2f)) / (width / 2f)).coerceIn(-1f, 1f)
                        val pctY = ((position.y - (height / 2f)) / (height / 2f)).coerceIn(-1f, 1f)
                        val maxRotation = 12f
                        scope.launch {
                            tiltX.animateTo(pctY * -maxRotation, spring())
                        }
                        scope.launch {
                            tiltY.animateTo(pctX * maxRotation, spring())
                        }
                    }
                    
                    updateTilt(down.position)
                    
                    drag(down.id) { change ->
                        updateTilt(change.position)
                    }
                }
                
                // On release
                scope.launch {
                    tiltX.animateTo(0f, spring())
                }
                scope.launch {
                    tiltY.animateTo(0f, spring())
                }
            }
        }
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = 16f * density
        }
}
