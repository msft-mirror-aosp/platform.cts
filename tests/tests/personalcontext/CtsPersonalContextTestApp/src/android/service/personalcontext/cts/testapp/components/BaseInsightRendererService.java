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

import android.content.ComponentName;
import android.os.RemoteException;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.cts.testapp.TestAppController;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.renderer.InsightRendererService;
import android.util.Log;

/** Repurposable refiner for CTS tests. */
public abstract class BaseInsightRendererService extends InsightRendererService {
    private static final String TAG = "CtsRenderer";

    public BaseInsightRendererService() {
        setExecutor(TestAppController.getExecutor());
    }

    private ComponentName getComponentName() {
        return new ComponentName(this, this.getClass());
    }

    @Override
    public void onConnected() {
        Log.w(TAG, getClass().getSimpleName() + ".onConnected()");
        try {
            RenderToken renderToken = mintRenderToken();
            Log.w(TAG, "  Render Token: " + renderToken);
            TestAppController.getListener(getComponentName()).onRendererConnected(renderToken);
        } catch (Exception e) {
            Log.w(TAG, getClass().getSimpleName() + ".onConnected() error", e);
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onConnected() end");
        }
    }

    @Override
    public InsightFilter onInitializeFilter() {
        Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter()");
        try {
            InsightFilter filter =
                    TestAppController.getListener(getComponentName()).onRendererInitializeFilter();
            Log.w(TAG, "  Filter: " + filter);
            TestAppController.getExecutor().execute(this::onReady);
            return filter;
        } catch (Exception e) {
            Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() error", e);
            return InsightFilter.REQUIRE_RENDER_TOKEN;
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() end");
        }
    }

    @Override
    public void onRender(PublishedContextInsight insight, RenderToken renderToken) {
        Log.w(TAG, getClass().getSimpleName() + ".onRender()");
        try {
            Log.w(TAG, "  Insight: " + insight.getInsight());
            Log.w(TAG, "  Render Token: " + renderToken);
            TestAppController.getListener(getComponentName())
                    .onRendererRender(insight.toBundle(), renderToken);
        } catch (Exception e) {
            Log.w(TAG, getClass().getSimpleName() + ".onRender() error", e);
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onRender() end");
        }
    }

    private void onReady() {
        try {
            Log.w(TAG, getClass().getSimpleName() + ".onReady()");
            TestAppController.getListener(getComponentName()).onReady();
        } catch (RemoteException e) {
            Log.w(TAG, getClass().getSimpleName() + ".onReady() error", e);
        }
    }
}
