/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.computercontrol.testapp.app

import android.computercontrol.testapp.common.Action
import android.computercontrol.testapp.common.Constants
import android.computercontrol.testapp.common.Interaction
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

suspend fun PointerInputScope.detectDragGesturesImmediate(
    onDragStart: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        // 1. Wait for the first finger to touch (ACTION_DOWN)
        val down = awaitFirstDown(requireUnconsumed = false)

        // 2. IMMEDIATE START: We skip the "awaitTouchSlop" step entirely.
        // We register the start at the exact pixel where the finger landed.
        onDragStart(down.position)

        var pointer = down.id

        // 3. Loop to track movements
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer }

            // If finger is lifted or gesture canceled
            if (change == null || !change.pressed) {
                onDragEnd()
                break
            }

            // If the event was canceled by a parent (e.g. valid scroll)
            if (change.isConsumed) {
                onDragCancel()
                break
            }

            // 4. Report every pixel change
            if (change.positionChanged()) {
                val dragAmount = change.position - change.previousPosition
                onDrag(change, dragAmount)

                // Consuming the change prevents parents (like Pager/List) from stealing it
                change.consume()
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val interactionReceiverBinder = InteractionReceiverBinder()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Constants.TAG, "MainActivity.onCreate")
        super.onCreate(savedInstanceState)
        interactionReceiverBinder.register(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    InteractionSender.sendInteraction(Interaction(Action.GoBack))
                }
            },
        )

        setContent { TestView(InteractionSender::sendInteraction) }
    }

    override fun onDestroy() {
        super.onDestroy()
        interactionReceiverBinder.unregister(this)
    }
}

@Composable
fun TestView(sendInteractionResult: (Interaction) -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            sendInteractionResult(
                                Interaction(Action.Tap(it.x.toInt(), it.y.toInt()))
                            )
                        },
                        onLongPress = {
                            sendInteractionResult(
                                Interaction(Action.LongPress(it.x.toInt(), it.y.toInt()))
                            )
                        },
                    )
                }
                .pointerInput(Unit) {
                    var dragStartOffset = Offset.Zero
                    var dragEndOffset = Offset.Zero
                    detectDragGesturesImmediate(
                        onDragStart = {
                            dragStartOffset = it
                            dragEndOffset = it
                        },
                        onDrag = { change, _ ->
                            dragEndOffset = change.position
                            change.consume()
                        },
                        onDragEnd = {
                            val swipe =
                                Action.Swipe(
                                    dragStartOffset.x.toInt(),
                                    dragStartOffset.y.toInt(),
                                    dragEndOffset.x.toInt(),
                                    dragEndOffset.y.toInt(),
                                )
                            // If it has movement then report it as a swipe
                            if (swipe.x1 != swipe.x2 || swipe.y1 != swipe.y2) {
                                val interaction = Interaction(swipe)
                                sendInteractionResult(interaction)
                            }
                        },
                    )
                }
    )
}
