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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.cts.testapp.ITestAppController;
import android.service.personalcontext.cts.workflows.ComponentManager.RefinerCallback;
import android.service.personalcontext.cts.workflows.ComponentManager.RendererCallback;
import android.service.personalcontext.cts.workflows.ComponentManager.UnderstanderCallback;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.insight.InsightFilter;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class WorkflowTestRule implements TestRule {
    private static final String SERVICE_PACKAGE_NAME = "android.personalcontext.cts.testapp";
    private static final String SERVICE_CLASS_NAME =
            "android.service.personalcontext.cts.testapp.TestAppController";

    private final Map<ComponentName, ComponentManager> mComponents = new LinkedHashMap<>();

    private ITestAppController mServiceController;
    private String mTestName;

    public String getTestName() {
        return mTestName;
    }

    /** Gets a refiner and waits for it to be configured, returns an empty mock RefinerCallback. */
    public RefinerCallback grabRefiner(HintFilter hintFilter) {
        final RefinerCallback callback = mock(RefinerCallback.class);
        for (Map.Entry<ComponentName, ComponentManager> component : mComponents.entrySet()) {
            final ComponentName componentName = component.getKey();
            final ComponentManager manager = component.getValue();

            if (manager.isAvailable() && manager instanceof ComponentManager.RefinerManager) {
                ((ComponentManager.RefinerManager) manager).grab(hintFilter, callback);

                try {
                    mServiceController.registerComponent(componentName, manager.getAppListener());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return callback;
            }
        }

        throw new RuntimeException("No refiners available");
    }

    /**
     * Gets an understander and waits for it to be configured, returns an empty mock
     * UnderstanderCallback.
     */
    public UnderstanderCallback grabUnderstander(HintFilter hintFilter) {
        final UnderstanderCallback callback = mock(UnderstanderCallback.class);
        for (Map.Entry<ComponentName, ComponentManager> component : mComponents.entrySet()) {
            final ComponentName componentName = component.getKey();
            final ComponentManager manager = component.getValue();

            if (manager.isAvailable() && manager instanceof ComponentManager.UnderstanderManager) {
                ((ComponentManager.UnderstanderManager) manager).grab(hintFilter, callback);

                try {
                    mServiceController.registerComponent(componentName, manager.getAppListener());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return callback;
            }
        }

        throw new RuntimeException("No understanders available");
    }

    /**
     * Gets a renderer and waits for it to be configured, returns an empty mock RendererCallback.
     */
    public RendererCallback grabRenderer() {
        return grabRenderer(InsightFilter.REQUIRE_RENDER_TOKEN);
    }

    /**
     * Gets a renderer and waits for it to be configured, returns an empty mock RendererCallback.
     */
    public RendererCallback grabRenderer(InsightFilter insightFilter) {
        final RendererCallback callback = mock(RendererCallback.class);
        for (Map.Entry<ComponentName, ComponentManager> component : mComponents.entrySet()) {
            final ComponentName componentName = component.getKey();
            final ComponentManager manager = component.getValue();

            if (manager.isAvailable() && manager instanceof ComponentManager.RendererManager) {
                ((ComponentManager.RendererManager) manager).grab(insightFilter, callback);

                when(callback.getRenderToken())
                        .thenAnswer(
                                invocation ->
                                        ((ComponentManager.RendererManager) manager)
                                                .getRenderToken());

                try {
                    mServiceController.registerComponent(componentName, manager.getAppListener());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return callback;
            }
        }

        throw new RuntimeException("No renderers available");
    }

    /** Blocks until all components have been registered and configured. */
    public void waitForComponentsReady() {
        try {
            mServiceController.enableRegisteredComponents();
            for (ComponentManager component : mComponents.values()) {
                if (!component.isAvailable()) {
                    component.waitForComponent();
                }
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Blocks until a new hint is delivered to a renderer, indicating that prior work is done. */
    public void flush() {
        try {
            mServiceController.flush();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private void setup() throws InterruptedException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final ComponentName serviceComponent =
                new ComponentName(SERVICE_PACKAGE_NAME, SERVICE_CLASS_NAME);

        final CountDownLatch connectedLatch = new CountDownLatch(1);

        context.bindService(
                new Intent().setComponent(serviceComponent),
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        try {
                            // Get the service controller.
                            mServiceController = ITestAppController.Stub.asInterface(binder);

                            // Turn off all components in case the last test left things messy.
                            mServiceController.disableAllComponents();

                            // Get all of the available components and add them to mComponents.
                            for (ComponentName componentName : mServiceController.getRefiners()) {
                                mComponents.put(
                                        componentName,
                                        new ComponentManager.RefinerManager(componentName));
                            }

                            for (ComponentName componentName :
                                    mServiceController.getUnderstanders()) {
                                mComponents.put(
                                        componentName,
                                        new ComponentManager.UnderstanderManager(componentName));
                            }

                            for (ComponentName componentName : mServiceController.getRenderers()) {
                                mComponents.put(
                                        componentName,
                                        new ComponentManager.RendererManager(componentName));
                            }

                            // Report that the service is ready.
                            connectedLatch.countDown();
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        throw new RuntimeException("Service disconnected unexpectedly");
                    }
                },
                Context.BIND_AUTO_CREATE);

        // Wait until the service is all configured and ready.
        if (!connectedLatch.await(5000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Service did not connect");
        }
    }

    private void teardown() throws RemoteException {
        if (mServiceController != null) {
            mServiceController.disableAllComponents();
        }

        mComponents.clear();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        mTestName = description.getClassName() + "#" + description.getMethodName();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    setup();
                    base.evaluate();
                } finally {
                    teardown();
                }
            }
        };
    }
}
