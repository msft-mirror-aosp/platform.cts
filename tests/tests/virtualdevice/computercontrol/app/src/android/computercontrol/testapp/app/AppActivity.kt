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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue

class AppActivity : ComponentActivity() {

    private val interactionReceiverBinder = InteractionReceiverBinder()

    private var activityReadySignaled = false

    private val focusRequesters =
        mapOf(
            Constants.TEXT_FIELD_1 to FocusRequester(),
            Constants.TEXT_FIELD_2 to FocusRequester(),
        )

    private val focusReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Constants.ACTION_REQUEST_FOCUS) {
                    val textFieldId = intent.getStringExtra(Constants.EXTRA_FOCUS_TEXT_FIELD_ID)
                    Log.d(Constants.TAG, "Requesting focus for $textFieldId")
                    focusRequesters[textFieldId]?.requestFocus()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(Constants.TAG, "AppActivity.onCreate")
        interactionReceiverBinder.register(this)

        val filter = IntentFilter(Constants.ACTION_REQUEST_FOCUS)
        registerReceiver(focusReceiver, filter, RECEIVER_EXPORTED)

        setContent { AppView(focusRequesters, this::sendInteraction) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(
            Constants.TAG,
            "onWindowFocusChanged: hasFocus=$hasFocus, " +
                "signaled=$activityReadySignaled"
        )
        if (hasFocus && !activityReadySignaled) {
            activityReadySignaled = true
            sendInteraction(Interaction(Action.ActivityReady))
            Log.d(Constants.TAG, "Sent ActivityReady signal")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interactionReceiverBinder.unregister(this)
        unregisterReceiver(focusReceiver)
    }

    private fun sendInteraction(interaction: Interaction) {
        InteractionSender.sendInteraction(interaction)
    }
}

@Composable
fun AppView(
    focusRequesters: Map<String, FocusRequester>,
    sendInteractionResult: (Interaction) -> Unit,
) {
    var text1 by remember { mutableStateOf(TextFieldValue()) }
    var text2 by remember { mutableStateOf(TextFieldValue()) }

    Column {
        TextField(
            value = text1,
            onValueChange = {
                text1 = it
                sendInteractionResult(
                    Interaction(
                        Action.TextFieldValueChange(
                            Constants.TEXT_FIELD_1,
                            it.text,
                            getUncommittedText(it),
                        )
                    )
                )
            },
            label = { Text("Text Field 1") },
            modifier = Modifier.focusRequester(focusRequesters.getValue(Constants.TEXT_FIELD_1)),
        )
        TextField(
            value = text2,
            onValueChange = {
                text2 = it
                sendInteractionResult(
                    Interaction(
                        Action.TextFieldValueChange(
                            Constants.TEXT_FIELD_2,
                            it.text,
                            getUncommittedText(it),
                        )
                    )
                )
            },
            label = { Text("Text Field 2") },
            modifier = Modifier.focusRequester(focusRequesters.getValue(Constants.TEXT_FIELD_2)),
        )
    }
}

private fun getUncommittedText(textFieldValue: TextFieldValue): String? {
    return textFieldValue.composition?.let { composition ->
        textFieldValue.text.substring(composition.start, composition.end)
    }
}
