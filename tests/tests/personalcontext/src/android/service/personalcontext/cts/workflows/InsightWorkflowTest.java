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

import static android.service.personalcontext.cts.workflows.WorkflowUtils.configureBasicUnderstander;
import static android.service.personalcontext.cts.workflows.WorkflowUtils.createBasicHint;
import static android.service.personalcontext.cts.workflows.WorkflowUtils.grabBasicUnderstander;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.cts.workflows.ComponentManager.RendererCallback;
import android.service.personalcontext.cts.workflows.ComponentManager.UnderstanderCallback;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.RequiredServiceRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// TODO(b/497020098): Re-enable.
@Ignore("b/496398871")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE)
@RunWith(AndroidJUnit4.class)
public class InsightWorkflowTest {
    private static final String PERSONAL_CONTEXT_SERVICE_NAME = "personal_context";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final RequiredServiceRule mRequiredServiceRule =
            new RequiredServiceRule(PERSONAL_CONTEXT_SERVICE_NAME);

    @Rule public final WorkflowTestRule mWorkflow = new WorkflowTestRule();

    private PersonalContextManager mPersonalContextManager;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPersonalContextManager = context.getSystemService(PersonalContextManager.class);
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.insight.BundleInsight.Builder#addOriginHint",
                "android.service.personalcontext.insight.BundleInsight.Builder#build",
                "android.service.personalcontext.renderer.InsightRendererService#onConnected",
                "android.service.personalcontext.renderer.InsightRendererService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.renderer.InsightRendererService#onRender",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onConnected",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onUnderstand",
            })
    @Test
    public void testInsightWithRenderToken()
            throws InterruptedException, ExecutionException, TimeoutException {
        // Create a basic hint for the workflow.
        final BundleHint hint = createBasicHint(mWorkflow);

        // Set up a basic understander and three renderers.
        final CompletableFuture<BundleInsight> insightFuture =
                grabBasicUnderstander(mWorkflow, hint);

        final RendererCallback renderer1 = mWorkflow.grabRenderer();
        final RendererCallback renderer2 = mWorkflow.grabRenderer();
        final RendererCallback renderer3 = mWorkflow.grabRenderer();

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Get a RenderToken after all components are ready.
        final RenderToken renderToken1 = renderer1.getRenderToken();
        final RenderToken renderToken2 = renderer2.getRenderToken();

        // Publish a hint with the render tokens.
        mPersonalContextManager.publishTriggeringHint(
                List.of(hint), List.of(renderToken1, renderToken2), List.of());

        // Wait for the understander to be invoked and get the insight that it returned.
        BundleInsight insight = insightFuture.get(5000, TimeUnit.MILLISECONDS);
        assertThat(insight).isNotNull();

        // Confirm that renderer1 and 2 were each called once.
        verify(renderer1, timeout(5000)).onRender(insightEq(insight), eq(renderToken1));
        verify(renderer2, timeout(5000)).onRender(insightEq(insight), eq(renderToken2));

        // Make sure that prior messages have been flushed from the system.
        mWorkflow.flush();

        // Confirm that renderer3 was never called.
        verify(renderer3, never()).onRender(any(), any());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.insight.BundleInsight.Builder#addOriginHint",
                "android.service.personalcontext.insight.BundleInsight.Builder#build",
                "android.service.personalcontext.insight.BundleInsight.Builder"
                        + "#setInsightTypeName",
                "android.service.personalcontext.insight.InsightFilter.Builder#addInsightType",
                "android.service.personalcontext.insight.InsightFilter.Builder#build",
                "android.service.personalcontext.renderer.InsightRendererService#onConnected",
                "android.service.personalcontext.renderer.InsightRendererService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.renderer.InsightRendererService#onRender",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onConnected",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onUnderstand",
            })
    @Test
    public void testInsightWithoutRenderToken()
            throws InterruptedException, ExecutionException, TimeoutException {
        // Create a basic hint for the workflow.
        final BundleHint hint = createBasicHint(mWorkflow);

        // Set up a basic understander and two renderers.
        final CompletableFuture<BundleInsight> insightFuture =
                grabBasicUnderstander(mWorkflow, hint);
        final RendererCallback renderer1 =
                mWorkflow.grabRenderer(
                        new InsightFilter.Builder().addInsightType(hint.getHintTypeName()).build());
        final RendererCallback renderer2 =
                mWorkflow.grabRenderer(
                        new InsightFilter.Builder().addInsightType(hint.getHintTypeName()).build());
        final RendererCallback renderer3 = mWorkflow.grabRenderer();

        // Wait for all components to be connected.
        mWorkflow.waitForComponentsReady();

        // Publish the hint without a render token.
        mPersonalContextManager.publishTriggeringHint(List.of(hint), List.of(), List.of());

        // Wait for the understander to be invoked and get the insight that it returned.
        BundleInsight insight = insightFuture.get(5000, TimeUnit.MILLISECONDS);
        assertThat(insight).isNotNull();

        // Confirm that renderer1 and renderer2 were each called once.
        verify(renderer1, timeout(5000)).onRender(insightEq(insight), any());
        verify(renderer2, timeout(5000)).onRender(insightEq(insight), any());

        // Make sure that prior messages have been flushed from the system.
        mWorkflow.flush();

        // Confirm that renderer2 was never called.
        verify(renderer3, never()).onRender(any(), any());
    }

    @ApiTest(
            apis = {
                "android.service.personalcontext.PersonalContextManager#publishTriggeringHint",
                "android.service.personalcontext.PersonalContextManager#reportInsightEvent",
                "android.service.personalcontext.hint.BundleHint.Builder#build",
                "android.service.personalcontext.insight.BundleInsight.Builder#addOriginHint",
                "android.service.personalcontext.insight.BundleInsight.Builder#build",
                "android.service.personalcontext.insight.BundleInsight.Builder"
                        + "#setInsightTypeName",
                "android.service.personalcontext.insight.InsightFilter.Builder#addInsightType",
                "android.service.personalcontext.insight.InsightFilter.Builder#build",
                "android.service.personalcontext.insight.interaction.InsightEvent#getEventType",
                "android.service.personalcontext.renderer.InsightRendererService#onConnected",
                "android.service.personalcontext.renderer.InsightRendererService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.renderer.InsightRendererService#onRender",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onConnected",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onInitializeFilter",
                "android.service.personalcontext.understander.ContextUnderstanderService"
                        + "#onUnderstand",
            })
    @Test
    public void testReportEvent() throws InterruptedException {
        // Create a basic hint for the workflow.
        final BundleHint hint = createBasicHint(mWorkflow);

        // Set up a basic understander and a renderer.
        UnderstanderCallback understander =
                mWorkflow.grabUnderstander(WorkflowUtils.filterForHint(hint));
        RendererCallback renderer = mWorkflow.grabRenderer();

        // Configure the understander to create an insight when it gets the hint.
        configureBasicUnderstander(understander, hint);

        // When the renderer gets the insight, report an event based on it.
        doAnswer(
                        invocation -> {
                            mPersonalContextManager.reportInsightEvent(
                                    /* insight= */ invocation.getArgument(0),
                                    InsightEvent.EVENT_USER_FEEDBACK_POSITIVE,
                                    /* renderToken= */ invocation.getArgument(1));
                            return null;
                        })
                .when(renderer)
                .onRender(any(), any());

        // Wait for all components to be ready.
        mWorkflow.waitForComponentsReady();

        // Publish the hint with a render token.
        mPersonalContextManager.publishTriggeringHint(
                List.of(hint), List.of(renderer.getRenderToken()), List.of());

        // Wait for the event to be reported and confirmed.
        ArgumentCaptor<InsightEvent> eventCaptor = ArgumentCaptor.forClass(InsightEvent.class);
        verify(understander, timeout(5000))
                .onHandleEvent(eq("android.personalcontext.cts"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(InsightEvent.EVENT_USER_FEEDBACK_POSITIVE);
    }

    /** Mockito matcher for an object that needs to be converted before testing for equality. */
    protected static PublishedContextInsight insightEq(ContextInsight insight) {
        return argThat(v -> insight.equals(v.getInsight()));
    }
}
