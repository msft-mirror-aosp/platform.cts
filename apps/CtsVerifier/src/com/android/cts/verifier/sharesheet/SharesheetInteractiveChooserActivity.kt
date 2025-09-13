/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.cts.verifier.sharesheet

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.chooser.ChooserManager
import android.service.chooser.ChooserSession
import android.service.chooser.ChooserSessionToken
import android.service.chooser.ChooserTarget
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R
import com.google.inject.util.Types.mapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PARAM_SESSION_TOKEN = "session-token"

class SharesheetInteractiveChooserActivity : PassFailButtons.Activity() {
    private val chooserSession = MutableStateFlow<ChooserSession?>(null)
    private val chooserBounds = MutableStateFlow<Rect?>(null)

    private lateinit var chooserManager: ChooserManager

    private lateinit var shareButton: Button
    private lateinit var closeButton: Button
    private lateinit var instructions: TextView
    private lateinit var passButton: View
    private lateinit var failButton: View

    private var startedScope: CoroutineScope? = null
    private val stateMachine = StateMachine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        val chooserManager = getSystemService(ChooserManager::class.java)
        if (chooserManager == null) {
            Toast.makeText(this, "ChooserManager is not available", Toast.LENGTH_LONG).show()
            setTestResultAndFinish(false)
            return
        }
        this.chooserManager = chooserManager

        setContentView(R.layout.sharesheet_interactive_chooser_activity)
        shareButton = requireViewById<Button>(R.id.share)
        closeButton = requireViewById<Button>(R.id.close)
        instructions = requireViewById<TextView>(R.id.instructions)
        passButton = requireViewById<View>(R.id.pass_button)
        failButton = requireViewById<View>(R.id.fail_button)

        shareButton.setOnClickListener { startChooser() }
        closeButton.setOnClickListener { closeChooser() }

        setPassFailButtonClickListeners()

        chooserSession.value =
            savedInstanceState
                ?.getParcelable(PARAM_SESSION_TOKEN, ChooserSessionToken::class.java)
                ?.let { token -> chooserManager.getSession(token) }
    }

    override fun onStart() {
        super.onStart()
        startedScope?.cancel()
        startedScope = CoroutineScope(Dispatchers.Main.immediate)
        val rootView = requireViewById<View>(R.id.root)
        val isLaidOut =
            callbackFlow<Unit> {
                val onLayoutChangedListener =
                    View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> trySend(Unit) }
                rootView.addOnLayoutChangeListener(onLayoutChangedListener)
                awaitClose { rootView.removeOnLayoutChangeListener(onLayoutChangedListener) }
            }
        startedScope?.launch {
            stateMachine.state.collect { state ->
                instructions.setText(state.descriptionRes)
                shareButton.visibility = state.shareButtonVisibility
                passButton.visibility = state.failPassButtonsVisibility
                failButton.visibility = state.failPassButtonsVisibility
            }
        }
        startedScope?.launch {
            val rootViewRect = Rect()
            isLaidOut
                .combine(chooserBounds) { _, bounds -> bounds }
                .filterNotNull()
                .collect { bounds ->
                    val showRect = rootView.getGlobalVisibleRect(rootViewRect)
                    if (showRect) {
                        (closeButton.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                            params.topMargin =
                                maxOf(0, bounds.top - rootViewRect.top - closeButton.height)
                            params.leftMargin = maxOf(0, bounds.left - rootViewRect.left)
                            closeButton.setLayoutParams(params)
                        }
                        if (chooserSession.value != null) {
                            closeButton.visibility = View.VISIBLE
                        }
                    }
                }
        }
        startedScope?.launch {
            chooserSession
                .scan<ChooserSession?, ChooserSession?>(null) { prev, curr ->
                    onChooserSessionChanged(prev, curr)
                    curr
                }
                .collect {}
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        chooserSession.value?.token?.let { token ->
            outState.putParcelable(PARAM_SESSION_TOKEN, token)
        }
    }

    override fun onStop() {
        super.onStop()
        startedScope?.cancel()
        startedScope = null
    }

    private fun startChooser() {
        val targetIntent =
            Intent(Intent.ACTION_SEND).apply {
                addCategory("com.android.cts.verifier.sharesheet.TEST_CATEGORY")
            }
        val chooserIntent =
            Intent.createChooser(targetIntent, "Test").apply {
                val target =
                    ChooserTarget(
                        "Test Target",
                        Icon.createWithResource(
                            this@SharesheetInteractiveChooserActivity,
                            R.drawable.icon,
                        ),
                        1f,
                        ComponentName.unflattenFromString(
                            "$packageName/.sharesheet.InteractiveTestTarget1"
                        ),
                        Bundle(),
                    )
                putExtra(Intent.EXTRA_CHOOSER_TARGETS, arrayOf(target))
                putExtra(Intent.EXTRA_INTENT, targetIntent)
                putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
            }
        val session = chooserManager.startSession(this, chooserIntent)
        updateChooserSession(session)
    }

    private fun closeChooser() {
        chooserSession.value?.endSession()
        updateChooserSession(null)
    }

    private fun onChooserSessionChanged(prev: ChooserSession?, curr: ChooserSession?) {
        prev?.endSession()
        if (curr == null) {
            onSessionEnded()
        } else {
            curr.addStateListener(
                mainExecutor,
                object : ChooserSession.StateListener {
                    override fun onStateChanged(state: Int) {
                        if (state == ChooserSession.STATE_CLOSED) {
                            updateChooserSession(null)
                        }
                    }

                    override fun onBoundsChanged(bounds: Rect) {
                        chooserBounds.value = bounds
                    }
                },
            )
            onSessionStarted()
        }
    }

    private fun updateChooserSession(session: ChooserSession?) {
        chooserSession.update { if (it?.token == session?.token) it else session }
    }

    private fun onSessionStarted() {
        stateMachine.onSessionStarted()
    }

    private fun onSessionEnded() {
        stateMachine.onSessionEnded()
        closeButton.visibility = View.INVISIBLE
    }
}

private data class State(
    val descriptionRes: Int,
    val shareButtonVisibility: Int,
    val failPassButtonsVisibility: Int,
)

private class StateMachine {
    private val initialState =
        State(
            descriptionRes = R.string.sharesheet_interactive_chooser_start_instruction,
            shareButtonVisibility = View.VISIBLE,
            failPassButtonsVisibility = View.GONE,
        )
    private val testingState =
        State(
            descriptionRes = R.string.sharesheet_interactive_chooser_test_instruction,
            shareButtonVisibility = View.GONE,
            failPassButtonsVisibility = View.GONE,
        )
    private val confirmationState =
        State(
            descriptionRes = R.string.sharesheet_interactive_chooser_confirmation_instruction,
            shareButtonVisibility = View.GONE,
            failPassButtonsVisibility = View.VISIBLE,
        )

    private val transitions =
        mapOf((initialState to true) to testingState, (testingState to false) to confirmationState)

    val state = MutableStateFlow<State>(initialState)

    fun onSessionStarted() {
        transitions[state.value to true]?.let { state.value = it }
    }

    fun onSessionEnded() {
        transitions[state.value to false]?.let { state.value = it }
    }
}
