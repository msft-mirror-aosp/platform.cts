/*
 * Copyright 2026 The Android Open Source Project
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

package android.service.personalcontext.cts.testapp.components;

import android.annotation.NonNull;
import android.content.ComponentName;
import android.os.RemoteException;
import android.service.personalcontext.cts.testapp.TestAppController;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.understander.ContextUnderstanderService;
import android.util.Log;

import java.util.List;

/** Base class for repurposable understander for CTS tests. */
public abstract class BaseContextUnderstanderService extends ContextUnderstanderService {
    private static final String TAG = "CtsUnderstander";

    public BaseContextUnderstanderService() {
        setExecutor(TestAppController.getExecutor());
    }

    private ComponentName getComponentName() {
        return new ComponentName(this, this.getClass());
    }

    @Override
    public void onConnected() {
        Log.w(TAG, getClass().getSimpleName() + ".onConnected() start");
        try {
            TestAppController.getListener(getComponentName()).onUnderstanderConnected();
        } catch (Exception e) {
            // Do nothing.
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onConnected() end");
        }
    }

    @Override
    public HintFilter onInitializeFilter() {
        Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() start");
        try {
            HintFilter filter =
                    TestAppController.getListener(getComponentName())
                            .onUnderstanderInitializeFilter();
            Log.w(TAG, "  Filter: " + filter);
            TestAppController.getExecutor().execute(this::onReady);
            return filter;
        } catch (Exception e) {
            Log.w(TAG, "  Filter Error: " + e, e);
            return new HintFilter.Builder()
                    .addBundleHintTypeName("[No hints]", HintFilter.FILTER_TYPE_REQUIRED)
                    .build();
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() end");
        }
    }

    @NonNull
    @Override
    public List<ContextInsight> onUnderstand(@NonNull List<PublishedContextHint> hints) {
        try {
            Log.w(TAG, getClass().getSimpleName() + ".onUnderstand() start");
            Log.w(TAG, "  Received " + hints.size() + " hints");
            for (PublishedContextHint hint : hints) {
                Log.w(TAG, "    - " + hint.getContextHint());
            }

            List<ContextInsight> outputInsights =
                    TestAppController.getListener(getComponentName())
                            .onUnderstanderUnderstand(
                                    hints.stream().map(PublishedContextHintWrapper::new).toList())
                            .stream()
                            .map(ContextInsight::createInsightFromBundle)
                            .toList();

            Log.w(TAG, "  Returned " + outputInsights.size() + " insights");
            for (ContextInsight insight : outputInsights) {
                Log.w(TAG, "    - " + insight);
            }

            return outputInsights;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onUnderstand() end");
        }
    }

    @Override
    public void onHandleEvent(String packageName, InsightEvent event) {
        try {
            Log.w(TAG, getClass().getSimpleName() + ".onHandleEvent()");
            Log.w(TAG, "  Received event from " + packageName);
            Log.w(TAG, "  Event: " + event);

            TestAppController.getListener(getComponentName())
                    .onUnderstanderEvent(packageName, event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onHandleEvent() end");
        }
    }

    private void onReady() {
        try {
            Log.w(TAG, getClass().getSimpleName() + ".onReady()");
            TestAppController.getListener(getComponentName()).onReady();
        } catch (RemoteException e) {
            Log.e(TAG, getClass().getSimpleName() + ".onReady() failed", e);
        }
    }
}
