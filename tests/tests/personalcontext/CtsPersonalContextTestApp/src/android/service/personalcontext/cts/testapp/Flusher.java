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

package android.service.personalcontext.cts.testapp;

import android.annotation.NonNull;
import android.content.Context;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.renderer.InsightRendererService;
import android.service.personalcontext.understander.ContextUnderstanderService;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Class that holds all the apparatus to send a flush hint / insight through PCE. */
public abstract class Flusher {
    private static final String TAG = "Flusher";

    private static final String TYPE_FLUSH = "android.service.personalcontext.cts.testapp.Flush";

    private static final Map<UUID, CountDownLatch> sLatches = new HashMap<>();
    private static RenderToken sRenderToken;

    /** Sends a new message and blocks until it has been rendered. */
    public static void flush(Context context, int timeoutMs) throws InterruptedException {
        BundleHint hint = new BundleHint.Builder().setHintTypeName(TYPE_FLUSH).build();

        Log.w(TAG, "Flushing with hint " + hint.getHintId());

        try {
            CountDownLatch latch = new CountDownLatch(1);
            sLatches.put(hint.getHintId(), latch);

            Log.w(TAG, "Publishing hint");
            context.getSystemService(PersonalContextManager.class)
                    .publishTriggeringHint(List.of(hint), List.of(sRenderToken), List.of());

            Log.w(TAG, "Waiting for flush hint");
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            Log.w(TAG, "Clearing up flush latch");
            sLatches.remove(hint.getHintId());
        }
    }

    public static class Understander extends ContextUnderstanderService {
        @Override
        public HintFilter onInitializeFilter() {
            return new HintFilter.Builder()
                    .addBundleHintTypeName(TYPE_FLUSH, HintFilter.FILTER_TYPE_REQUIRED)
                    .build();
        }

        @NonNull
        @Override
        public List<ContextInsight> onUnderstand(@NonNull List<PublishedContextHint> hints) {
            BundleInsight insight =
                    new BundleInsight.Builder()
                            .setInsightTypeName(TYPE_FLUSH)
                            .addOriginHint(hints.getFirst())
                            .build();

            Log.w(TAG, "onUnderstand() -> " + insight.getInsightId());
            return List.of(insight);
        }
    }

    public static class Renderer extends InsightRendererService {
        @Override
        public void onConnected() {
            sRenderToken = mintRenderToken();
        }

        @Override
        public InsightFilter onInitializeFilter() {
            return InsightFilter.REQUIRE_RENDER_TOKEN;
        }

        @Override
        public void onRender(PublishedContextInsight insight, RenderToken renderToken) {
            Log.w(TAG, "Render(" + insight.getInsight().getInsightId() + ")");
            if (insight.getInsight() instanceof BundleInsight bundleInsight) {
                if (TYPE_FLUSH.equals(bundleInsight.getInsightTypeName())) {
                    PublishedContextHint hint =
                            new ArrayList<>(insight.getInsight().getOriginHints()).getFirst();

                    Log.w(TAG, "Flush complete: " + hint.getContextHint().getHintId());

                    sLatches.get(hint.getContextHint().getHintId()).countDown();
                }
            }
        }
    }
}
