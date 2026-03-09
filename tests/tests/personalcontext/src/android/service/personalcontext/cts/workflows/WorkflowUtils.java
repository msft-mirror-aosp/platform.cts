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

package android.service.personalcontext.cts.workflows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.service.personalcontext.cts.workflows.ComponentManager.UnderstanderCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.BundleInsight;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WorkflowUtils {
    /** Creates a BundleHint with the current test name as the type name. */
    public static BundleHint createBasicHint(WorkflowTestRule rule) {
        return createBasicHint(rule.getTestName());
    }

    /** Creates a BundleHint with the given type name. */
    public static BundleHint createBasicHint(String typeName) {
        return new BundleHint.Builder()
                .setHintTypeName(typeName)
                .setDataBundle(new Bundle())
                .build();
    }

    /**
     * Gets an understander and configures it to generate a BundleInsight when it gets the provided
     * hint.
     */
    public static CompletableFuture<BundleInsight> grabBasicUnderstander(
            WorkflowTestRule rule, BundleHint hint) {
        final HintFilter filter =
                new HintFilter.Builder()
                        .addBundleHintTypeName(
                                hint.getHintTypeName(), HintFilter.FILTER_TYPE_REQUIRED)
                        .build();

        final UnderstanderCallback understander = rule.grabUnderstander(filter);
        final CompletableFuture<BundleInsight> future = new CompletableFuture<>();

        when(understander.onUnderstand(any()))
                .thenAnswer(
                        invocation -> {
                            List<PublishedContextHint> hints = invocation.getArgument(0);
                            if (hints.getFirst().getContextHint().equals(hint)
                                    && !future.isDone()) {
                                future.complete(
                                        new BundleInsight.Builder()
                                                .setInsightTypeName(hint.getHintTypeName())
                                                .addOriginHint(hints.getFirst())
                                                .build());
                                return List.of(future.get());
                            }

                            return Collections.emptyList();
                        });

        return future;
    }
}
