/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.ACTIVITY_ID;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundActivityExtra.LAUNCH_INTENTS;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundEmbeddedActivityAction.ACTION_FINISH_ACTIVITY_SUFFIX;
import static android.server.wm.backgroundactivity.appa.Components.ForegroundEmbeddedActivityAction.ACTION_LAUNCH_EMBEDDED_ACTIVITY_SUFFIX;
import static android.server.wm.backgroundactivity.appa.Components.buildFullActionName;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;

import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitInfo;

import java.util.Collections;
import java.util.List;

public class ForegroundEmbeddingActivity extends Activity {
    private String mActionLaunchEmbeddedActivity;
    private String mActionFinishActivity;

    private int mActivityId = -1;

    private final BroadcastReceiver mReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    int activityId = intent.getIntExtra(ACTIVITY_ID, mActivityId);
                    if (activityId != mActivityId) {
                        return;
                    }
                    if (mActionLaunchEmbeddedActivity.equals(action)) {
                        // Need to copy as a new array instead of just casting to Intent[] since a
                        // new
                        // array of type Parcelable[] is created when deserializing.
                        Intent[] intents =
                                intent.getParcelableArrayExtra(LAUNCH_INTENTS, Intent.class);
                        startActivityInSplit(intents);
                    } else if (mActionFinishActivity.equals(action)) {
                        finish();
                    }
                }
            };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        final String appPackageName = getApplicationContext().getPackageName();
        mActionLaunchEmbeddedActivity =
                buildFullActionName(appPackageName, ACTION_LAUNCH_EMBEDDED_ACTIVITY_SUFFIX);
        mActionFinishActivity = buildFullActionName(appPackageName, ACTION_FINISH_ACTIVITY_SUFFIX);

        Intent intent = getIntent();
        mActivityId = intent.getIntExtra(ACTIVITY_ID, -1);

        IntentFilter filter = new IntentFilter();
        filter.addAction(mActionLaunchEmbeddedActivity);
        // TODO(hanikazmi) Move this (and others) to onStart/onStop
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    void startActivityInSplit(Intent[] intents) {
        ActivityEmbeddingComponent embeddingComponent =
                getWindowExtensions().getActivityEmbeddingComponent();

        TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer = new TestValueCountConsumer<>();
        embeddingComponent.setSplitInfoCallback(splitInfoConsumer);

        embeddingComponent.setEmbeddingRules(Collections.singleton(createWildcardSplitPairRule()));
        for (Intent intent: intents) {
            WindowManagerJetpackTestBase.startActivityFromActivity(this, intent.getComponent(),
                    EMBEDDED_ACTIVITY_ID, Bundle.EMPTY);
        }
    }
}
