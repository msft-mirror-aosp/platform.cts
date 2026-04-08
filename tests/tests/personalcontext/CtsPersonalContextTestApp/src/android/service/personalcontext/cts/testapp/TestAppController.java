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

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.cts.testapp.components.Refiner1;
import android.service.personalcontext.cts.testapp.components.Refiner2;
import android.service.personalcontext.cts.testapp.components.Refiner3;
import android.service.personalcontext.cts.testapp.components.Renderer1;
import android.service.personalcontext.cts.testapp.components.Renderer2;
import android.service.personalcontext.cts.testapp.components.Renderer3;
import android.service.personalcontext.cts.testapp.components.Understander1;
import android.service.personalcontext.cts.testapp.components.Understander2;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Controller service for the Presonal Context CTS test app. */
public class TestAppController extends Service {
    private static final String TAG = "TestAppController";

    private static final ScheduledExecutorService sExecutor =
            Executors.newSingleThreadScheduledExecutor();

    public static Map<ComponentName, ITestAppComponentListener> sListeners = new HashMap<>();

    /** Gets the current listener for a component. */
    public static ITestAppComponentListener getListener(ComponentName componentName) {
        return Objects.requireNonNull(sListeners.get(componentName));
    }

    public static Executor getExecutor() {
        return sExecutor;
    }

    public TestAppController() {
        Log.w(TAG, "TestAppController()");
    }

    @Override
    public final IBinder onBind(Intent intent) {
        Log.w(TAG, "onBind()");
        return new Binder(this);
    }

    private static final class Binder extends ITestAppController.Stub {
        private final WeakReference<TestAppController> mService;

        private Binder(TestAppController service) {
            mService = new WeakReference<>(service);
        }

        private TestAppController getServiceOrThrow() throws RemoteException {
            final TestAppController service = mService.get();
            if (service == null) {
                throw new RemoteException("Service is no longer available");
            } else {
                return service;
            }
        }

        private List<ComponentName> getFlusherComponents() throws RemoteException {
            TestAppController service = getServiceOrThrow();
            return List.of(
                    new ComponentName(service, Flusher.Understander.class),
                    new ComponentName(service, Flusher.Renderer.class));
        }

        @Override
        public List<ComponentName> getRefiners() throws RemoteException {
            TestAppController service = getServiceOrThrow();
            return List.of(
                    new ComponentName(service, Refiner1.class),
                    new ComponentName(service, Refiner2.class),
                    new ComponentName(service, Refiner3.class));
        }

        @Override
        public List<ComponentName> getUnderstanders() throws RemoteException {
            TestAppController service = getServiceOrThrow();
            return List.of(
                    new ComponentName(service, Understander1.class),
                    new ComponentName(service, Understander2.class));
        }

        @Override
        public List<ComponentName> getRenderers() throws RemoteException {
            TestAppController service = getServiceOrThrow();
            return List.of(
                    new ComponentName(service, Renderer1.class),
                    new ComponentName(service, Renderer2.class),
                    new ComponentName(service, Renderer3.class));
        }

        @Override
        public void disableAllComponents() throws RemoteException {
            Log.w(TAG, "disableAllComponents()");
            updateComponentState(
                    false,
                    getRefiners(),
                    getUnderstanders(),
                    getRenderers(),
                    getFlusherComponents());
            sListeners.clear();
        }

        @Override
        public void registerComponent(
                ComponentName componentName, ITestAppComponentListener listener) {
            Log.w(TAG, "registerComponent(" + componentName + ")");
            sListeners.put(componentName, listener);
        }

        @Override
        public void enableRegisteredComponents() throws RemoteException {
            Log.w(TAG, "enableRegisteredComponents() -> " + sListeners.size() + " components");
            updateComponentState(true, sListeners.keySet(), getFlusherComponents());
        }

        @Override
        public void flush() throws RemoteException {
            Log.w(TAG, "flush()");
            try {
                Flusher.flush(getServiceOrThrow(), 5000);
                Log.w(TAG, "flush complete");
            } catch (Exception e) {
                Log.w(TAG, "Flush failed", e);
                throw new RemoteException(e);
            }
        }

        private void updateComponentState(boolean enabled, Collection<ComponentName>... components)
                throws RemoteException {
            Log.w(TAG, "updateComponentState()");
            PackageManager pm = getServiceOrThrow().getPackageManager();
            final List<PackageManager.ComponentEnabledSetting> newSettings = new ArrayList<>();

            for (Collection<ComponentName> componentList : components) {
                for (ComponentName componentName : componentList) {
                    newSettings.add(
                            new PackageManager.ComponentEnabledSetting(
                                    componentName,
                                    enabled
                                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP));
                }
            }

            pm.setComponentEnabledSettings(newSettings);
        }
    }
}
