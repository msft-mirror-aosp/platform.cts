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

import static android.service.personalcontext.cts.workflows.WorkflowUtils.createBasicHint;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.cts.EnablePersonalContextTestRule;
import android.service.personalcontext.cts.workflows.ComponentManager.RefinerCallback;
import android.service.personalcontext.cts.workflows.ComponentManager.UnderstanderCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// TODO(b/497020098): Re-enable.
@Ignore("b/496398871")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class HintWorkflowTest {
    private static final String PERSONAL_CONTEXT_SERVICE_NAME = "personal_context";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(PERSONAL_CONTEXT_SERVICE_NAME);

    @Rule public final WorkflowTestRule mWorkflow = new WorkflowTestRule();

    @Rule
    public final EnablePersonalContextTestRule mEnablePersonalContext =
            new EnablePersonalContextTestRule();

    private PersonalContextManager mPersonalContextManager;
    private boolean mWasEnabled;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPersonalContextManager = context.getSystemService(PersonalContextManager.class);
        mWasEnabled = mPersonalContextManager.isEnabled();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
            })
    @Test
    public void testNoRefinersHintWorkflowFinishes() {
        final ContextHint hint = createBasicHint(mWorkflow);
        mPersonalContextManager.publishTriggeringHint(List.of(hint), List.of(), List.of());
        mWorkflow.flush();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
                "android.service.personalcontext.refiner.HintRefinerService#onConnected",
                "android.service.personalcontext.refiner.HintRefinerService#onInitializeFilter",
                "android.service.personalcontext.refiner.HintRefinerService#onRefine",
            })
    @Test
    public void testOneHintToOneRefiner() {
        // Grab a refiner with a filter that accepts all hints and wait for it to be connected.
        final RefinerCallback a = mWorkflow.grabRefiner(new HintFilter.Builder().build());

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Build a hint, publish it, and wait for the workflow to finish.
        final ContextHint hint = createBasicHint(mWorkflow);
        mPersonalContextManager.publishTriggeringHint(List.of(hint), List.of(), List.of());

        // Make sure hint was delivered.
        verify(a, timeout(5000)).onRefine(List.of(hint));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
                "android.service.personalcontext.refiner.HintRefinerService#onConnected",
                "android.service.personalcontext.refiner.HintRefinerService#onInitializeFilter",
                "android.service.personalcontext.refiner.HintRefinerService#onRefine",
            })
    @Test
    public void testLargeHintBurst() throws InterruptedException {
        // Set up a latch for the number of hints we expect.
        int hintCount = 100;
        final CountDownLatch latch = new CountDownLatch(hintCount);

        // Grab a refiner with a filter that accepts all hints and wait for it to be connected.
        final RefinerCallback refiner = mWorkflow.grabRefiner(new HintFilter.Builder().build());

        // When we get a hint in the refiner, count down the latch.
        when(refiner.onRefine(any()))
                .thenAnswer(
                        invocation -> {
                            latch.countDown();
                            return Collections.emptyList();
                        });

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Build a hint, publish it, and wait for the workflow to finish.
        for (int i = 0; i < hintCount; i++) {
            mPersonalContextManager.publishTriggeringHint(
                    List.of(createBasicHint(mWorkflow)), List.of(), List.of());
        }

        // Make sure all hints were delivered.
        assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
                "android.service.personalcontext.refiner.HintRefinerService#onConnected",
                "android.service.personalcontext.refiner.HintRefinerService#onInitializeFilter",
                "android.service.personalcontext.refiner.HintRefinerService#onRefine",
            })
    @Test
    public void testMultipleHintsToOneRefiner() {
        // Grab a refiner with a filter that accepts all hints and wait for it to be connected.
        final RefinerCallback a = mWorkflow.grabRefiner(new HintFilter.Builder().build());

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Build hints, publish them, and wait for the workflow to finish.
        final ContextHint hint1 = createBasicHint(mWorkflow);
        final ContextHint hint2 = createBasicHint(mWorkflow);
        final ContextHint hint3 = createBasicHint(mWorkflow);

        mPersonalContextManager.publishTriggeringHint(
                List.of(hint1, hint2, hint3), List.of(), List.of());

        // Make sure all hints sere delivered.
        verify(a, timeout(5000)).onRefine(listContainsExactly(hint1, hint2, hint3));
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.hint.HintFilter.Builder#build",
                "android.service.personalcontext.refiner.HintRefinerService#onConnected",
                "android.service.personalcontext.refiner.HintRefinerService#onInitializeFilter",
                "android.service.personalcontext.refiner.HintRefinerService#onRefine",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onConnected",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onUnderstand",
            })
    @Test
    public void testHintsToComponentsByFilter() {
        final BundleHint hint = createBasicHint(mWorkflow);
        final HintFilter filter =
                new HintFilter.Builder()
                        .addBundleHintTypeName(
                                hint.getHintTypeName(), HintFilter.FILTER_TYPE_REQUIRED)
                        .build();

        // Get a refiner with a null HintFilter (no hints).
        final RefinerCallback a = mWorkflow.grabRefiner(null);

        // Get a refiner and an understander with the same hint filter specified.
        final RefinerCallback b = mWorkflow.grabRefiner(filter);
        final UnderstanderCallback c = mWorkflow.grabUnderstander(filter);

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Publish the hint and wait for the workflow to finish.
        mPersonalContextManager.publishTriggeringHint(List.of(hint), List.of(), List.of());

        // Make sure the hint was delivered to the components it was intended for.
        verify(b, timeout(5000)).onRefine(eq(List.of(hint)));
        verify(c, timeout(5000))
                .onUnderstand(listContainsExactly(PublishedContextHint::getContextHint, hint));

        // Make sure that prior messages have been flushed from the system.
        mWorkflow.flush();

        // Make sure the hint wasn't delivered to the components it wasn't intended for.
        verify(a, never()).onRefine(any());
    }

    /** Mockito matcher for a list where we don't care about the order. */
    protected static <T> List<T> listContainsExactly(T... values) {
        return listContainsExactly(v -> v, values);
    }

    /** Mockito matcher for a list where we don't care about the order and need to convert them. */
    protected static <T1, T2> List<T1> listContainsExactly(
            Function<T1, T2> converter, T2... values) {
        return argThat(
                (List<T1> argument) -> {
                    List<T2> converted = argument.stream().map(converter).toList();
                    return converted.size() == values.length
                            && converted.containsAll(Arrays.asList(values));
                });
    }
}
