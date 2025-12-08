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

package android.voiceinteraction.nopermission;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

public class NoPermissionSession extends VoiceInteractionSession {
    static final String TAG = "NoPermissionSession";
    // Must match MainInteractionSession.ACTION_ON_SHOW_RECEIVED
    public static final String ACTION_ON_SHOW_RECEIVED = "on_show_received";
    public static final String ACTION_ASSIST_DATA_RECEIVED = "assist_data_received";
    public static final String ACTION_SCREENSHOT_RECEIVED = "screenshot_received";
    // Must match MainInteractionSession.EXTRA_SHOW_FLAGS
    public static final String EXTRA_SHOW_FLAGS = "show_flags";
    public static final String EXTRA_RECEIVED = "received";
    public static final String EXTRA_HAS_STRUCTURE = "has_structure";
    public static final String EXTRA_HAS_WINDOW_DATA = "has_window_data";
    public static final String EXTRA_HAS_VIEW_DATA = "has_view_data";

    public NoPermissionSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        if (args == null) {
            args = new Bundle();
        }
        args.putInt(EXTRA_SHOW_FLAGS, showFlags);
        notifyTestReceiver(ACTION_ON_SHOW_RECEIVED, args);

        Intent intent = args.getParcelable("intent");
        if (intent != null) {
            startVoiceActivity(intent);
        }
    }

    @Override
    public void onHandleAssist(AssistState state) {
        super.onHandleAssist(state);
        Bundle extras = new Bundle();
        boolean hasStructure = state.getAssistStructure() != null;
        extras.putBoolean(EXTRA_RECEIVED, hasStructure);
        extras.putBoolean(EXTRA_HAS_STRUCTURE, hasStructure);

        boolean hasWindowData = false;
        boolean hasViewData = false;

        if (hasStructure) {
            android.app.assist.AssistStructure structure = state.getAssistStructure();
            int windowCount = structure.getWindowNodeCount();
            if (windowCount > 0) {
                hasWindowData = true;
                for (int i = 0; i < windowCount; i++) {
                    android.app.assist.AssistStructure.WindowNode window =
                            structure.getWindowNodeAt(i);
                    if (window.getRootViewNode() != null
                            && window.getRootViewNode().getChildCount() > 0) {
                        hasViewData = true;
                        break;
                    }
                }
            }
        }

        extras.putBoolean(EXTRA_HAS_WINDOW_DATA, hasWindowData);
        extras.putBoolean(EXTRA_HAS_VIEW_DATA, hasViewData);

        notifyTestReceiver(ACTION_ASSIST_DATA_RECEIVED, extras);
    }

    @Override
    public void onHandleScreenshot(android.graphics.Bitmap screenshot) {
        super.onHandleScreenshot(screenshot);
        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_RECEIVED, screenshot != null);
        notifyTestReceiver(ACTION_SCREENSHOT_RECEIVED, extras);
    }

    private void notifyTestReceiver(String action, Bundle extras) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtras(extras);
        intent.setClassName(
                "android.voiceinteraction.cts",
                "android.voiceinteraction.cts.VoiceInteractionTestReceiver");
        Log.i(TAG, "notifyTestReceiver: broadcast intent=" + intent);
        getContext().sendBroadcast(intent);
    }
}
