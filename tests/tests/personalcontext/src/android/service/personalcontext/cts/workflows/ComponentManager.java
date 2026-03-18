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

import static com.google.common.truth.Truth.assertThat;

import android.annotation.CallSuper;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.cts.testapp.ITestAppComponentListener;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Base class for managing an ACE component. */
public abstract class ComponentManager {
    protected final ComponentName mComponentName;
    private CountDownLatch mReadyLatch = new CountDownLatch(1);
    private boolean mAvailable = true;

    ComponentManager(ComponentName componentName) {
        mComponentName = componentName;
    }

    /** Determines whether the component is available. */
    public final boolean isAvailable() {
        return mAvailable;
    }

    protected final void onReady() {
        mReadyLatch.countDown();
    }

    protected void grab() {
        assertThat(mAvailable).isTrue();
        mAvailable = false;
    }

    /** Waits for the component to be configured and ready to receive hints or insights. */
    public void waitForComponent() {
        try {
            // Wait for component to be enabled.
            assertThat(mReadyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Gets a listener for component events. */
    public abstract ITestAppComponentListener getAppListener();

    protected abstract class TestAppListener extends ITestAppComponentListener.Stub {
        @Override
        public final void onReady() {
            ComponentManager.this.onReady();
        }

        public void onRefinerConnected() {
            // Do nothing.
        }

        @CallSuper
        @Override
        public HintFilter onRefinerInitializeFilter() {
            throw new IllegalStateException();
        }

        @Override
        public List<Bundle> onRefinerRefine(List<Bundle> hints) {
            throw new IllegalStateException();
        }

        @CallSuper
        @Override
        public void onUnderstanderConnected() {
            // Do nothing.
        }

        @Override
        public HintFilter onUnderstanderInitializeFilter() {
            throw new IllegalStateException();
        }

        @Override
        public List<Bundle> onUnderstanderUnderstand(List<PublishedContextHintWrapper> hints) {
            throw new IllegalStateException();
        }

        @Override
        public void onUnderstanderEvent(String packageName, InsightEvent event) {
            throw new IllegalStateException();
        }

        @CallSuper
        @Override
        public void onRendererConnected(RenderToken renderToken) {
            // Do nothing.
        }

        @Override
        public InsightFilter onRendererInitializeFilter() {
            throw new IllegalStateException();
        }

        @Override
        public void onRendererRender(Bundle insight, RenderToken renderToken) {
            throw new IllegalStateException();
        }
    }
    ;

    /** Callbacks for refiner events. */
    public interface RefinerCallback {
        /** Called when the refiner receives a call to onRefine. */
        List<ContextHint> onRefine(List<ContextHint> hints);
    }

    /** Manager for a refiner. */
    public static final class RefinerManager extends ComponentManager {
        private HintFilter mFilter;
        private RefinerCallback mCallback;

        RefinerManager(ComponentName componentName) {
            super(componentName);
        }

        /** Grabs this manager and configures it. */
        public void grab(HintFilter hintFilter, RefinerCallback callback) {
            mFilter = hintFilter;
            mCallback = callback;
            grab();
        }

        @Override
        public ITestAppComponentListener getAppListener() {
            return new TestAppListener() {
                @Override
                public HintFilter onRefinerInitializeFilter() {
                    return mFilter;
                }

                @Override
                public List<Bundle> onRefinerRefine(List<Bundle> hintBundles) {
                    List<ContextHint> hints =
                            hintBundles.stream().map(ContextHint::createHintFromBundle).toList();
                    return mCallback.onRefine(hints).stream().map(ContextHint::toBundle).toList();
                }
            };
        }
    }

    /** Callbacks for understander events. */
    public interface UnderstanderCallback {
        /** Called when the understander receives a call to onUnderstand. */
        List<ContextInsight> onUnderstand(List<PublishedContextHint> hints);

        /** Called when the understander receives a call to onHandleEvent. */
        void onHandleEvent(String packageName, InsightEvent event);
    }

    /** Manager for an understander. */
    public static final class UnderstanderManager extends ComponentManager {
        private HintFilter mFilter;
        private UnderstanderCallback mCallback;

        UnderstanderManager(ComponentName componentName) {
            super(componentName);
        }

        /** Grabs this manager and configures it. */
        public void grab(HintFilter hintFilter, UnderstanderCallback callback) {
            mFilter = hintFilter;
            mCallback = callback;
            grab();
        }

        @Override
        public ITestAppComponentListener getAppListener() {
            return new TestAppListener() {
                @Override
                public HintFilter onUnderstanderInitializeFilter() {
                    return mFilter;
                }

                @Override
                public List<Bundle> onUnderstanderUnderstand(
                        List<PublishedContextHintWrapper> hintWrappers) {
                    List<PublishedContextHint> hints =
                            hintWrappers.stream()
                                    .map(PublishedContextHintWrapper::getPublishedContextHint)
                                    .toList();
                    return mCallback.onUnderstand(hints).stream()
                            .map(ContextInsight::toBundle)
                            .toList();
                }

                @Override
                public void onUnderstanderEvent(String packageName, InsightEvent event) {
                    mCallback.onHandleEvent(packageName, event);
                }
            };
        }
    }

    /** Callbacks for renderer events. */
    public interface RendererCallback {
        /** Called when the renderer receives a call to onRender. */
        void onRender(PublishedContextInsight insight, RenderToken renderToken);

        /** Helper method for getting a render token. */
        RenderToken getRenderToken();
    }

    /** Manager for a renderer. */
    public static final class RendererManager extends ComponentManager {
        private InsightFilter mFilter;
        private RendererCallback mCallback;
        private RenderToken mRenderToken;

        RendererManager(ComponentName componentName) {
            super(componentName);
        }

        /** Grabs this manager and configures it. */
        public void grab(InsightFilter insightFilter, RendererCallback callback) {
            mFilter = insightFilter;
            mCallback = callback;
            grab();
        }

        /** Gets the render token for this renderer. */
        public RenderToken getRenderToken() {
            return mRenderToken;
        }

        @Override
        public ITestAppComponentListener getAppListener() {
            return new TestAppListener() {
                @Override
                public void onRendererConnected(RenderToken renderToken) {
                    mRenderToken = renderToken;
                    super.onRendererConnected(renderToken);
                }

                @Override
                public InsightFilter onRendererInitializeFilter() {
                    return mFilter;
                }

                @Override
                public void onRendererRender(Bundle insightBundle, RenderToken renderToken) {
                    PublishedContextInsight insight =
                            PublishedContextInsight.createPublishedInsightFromBundle(insightBundle);
                    mCallback.onRender(insight, renderToken);
                }
            };
        }
    }
}
