/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.service.dreams.cts;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.IntDef;
import android.app.dream.cts.app.IControlledDream;
import android.app.dream.cts.app.IDreamLifecycleListener;
import android.app.dream.cts.app.IDreamListener;
import android.app.dream.cts.app.IDreamProxy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.server.wm.DreamCoordinator;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link ControlledDreamSession} manages connecting and accessing a controlled dream instance that
 * is published through a dream proxy.
 */
public class ControlledDreamSession {
    private static final String TAG = "ControlledDreamSession";

    // Timeout that is used for waiting on various steps to complete, such as connecting to the
    // proxy service and starting the dream.
    private static final int TIMEOUT_SECONDS = 2;

    // The test app's proxy service component.
    private static final String DREAM_CONTROL_COMPONENT =
            "android.app.dream.cts.app/.DreamProxyService";


    public static final int DREAM_LIFECYCLE_UNKNOWN = 0;
    public static final int DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW = 1;
    public static final int DREAM_LIFECYCLE_ON_DREAMING_STARTED = 2;
    public static final int DREAM_LIFECYCLE_ON_DREAMING_STOPPED = 3;
    public static final int DREAM_LIFECYCLE_ON_DEATTACHED_FROM_WINDOW = 4;

    @IntDef(prefix = { "DREAM_LIFECYCLE_" }, value = {
            DREAM_LIFECYCLE_UNKNOWN,
            DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW,
            DREAM_LIFECYCLE_ON_DREAMING_STARTED,
            DREAM_LIFECYCLE_ON_DREAMING_STOPPED,
            DREAM_LIFECYCLE_ON_DEATTACHED_FROM_WINDOW
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Dreamlifecycle{}


    // Connection for accessing the dream proxy.
    private static final class ProxyServiceConnection implements ServiceConnection {
        final CountDownLatch mLatch;
        private IDreamProxy mProxy;

        ProxyServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        public IDreamProxy getProxy() {
            return mProxy;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mProxy = IDreamProxy.Stub.asInterface(service);
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mProxy = null;
        }
    }

    private final Context mContext;
    private final ComponentName mDreamComponent;
    private final DreamCoordinator mDreamCoordinator;

    private ProxyServiceConnection mServiceConnection;

    private IControlledDream mControlledDream;

    private @Dreamlifecycle int mLastLifecycle = DREAM_LIFECYCLE_UNKNOWN;

    public ControlledDreamSession(Context context, ComponentName dreamComponent,
            DreamCoordinator coordinator) {
        mContext = context;
        mDreamComponent = dreamComponent;
        mDreamCoordinator = coordinator;
    }

    private IDreamLifecycleListener mLifecycleListener = new IDreamLifecycleListener.Stub() {
        public void onAttachedToWindow(IControlledDream dream) {
            mLastLifecycle = DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW;
        }

        public void onDreamingStarted(IControlledDream dream) {
            mLastLifecycle = DREAM_LIFECYCLE_ON_DREAMING_STARTED;
        }

        public void onDreamingStopped(IControlledDream dream) {
            mLastLifecycle = DREAM_LIFECYCLE_ON_DREAMING_STOPPED;
        }

        public void onDetachedFromWindow(IControlledDream dream) {
            mLastLifecycle = DREAM_LIFECYCLE_ON_DEATTACHED_FROM_WINDOW;
        }
    };

    /**
     * Sets the dream component specified at construction as the active dream and subsequently
     * starts said dream.
     * @return An {@link IControlledDream} for accessing the currently running dream.
     */
    public IControlledDream start() throws InterruptedException, RemoteException {
        if (mServiceConnection != null) {
            Log.e(TAG, "session already started");
            return null;
        }

        // Connect to dream controller
        final ComponentName controllerService =
                ComponentName.unflattenFromString(DREAM_CONTROL_COMPONENT);
        final Intent intent = new Intent();
        intent.setComponent(controllerService);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mServiceConnection  = new ProxyServiceConnection(countDownLatch);
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        assertThat(countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final CountDownLatch dreamConnectLatch = new CountDownLatch(1);
        final IDreamListener dreamConnectListener = new IDreamListener.Stub() {
            @Override
            public void onDreamPublished(IControlledDream dream) {
                mControlledDream = dream;
                dreamConnectLatch.countDown();
            }
        };

        mServiceConnection.getProxy().registerListener(dreamConnectListener);

        // Start Dream
        mDreamCoordinator.setActiveDream(mDreamComponent);
        mDreamCoordinator.startDream();

        // Wait for dream to connect to the DreamController
        assertThat(dreamConnectLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        mControlledDream.registerLifecycleListener(mLifecycleListener);
        mServiceConnection.getProxy().unregisterListener(dreamConnectListener);

        return mControlledDream;
    }


    /**
     * Returns the dream published during start.
     */
    public IControlledDream getControlledDream() {
        return mControlledDream;
    }

    /**
     * Stops the current dream.
     */
    public void stop() throws RemoteException {
        if (mServiceConnection == null) {
            Log.e(TAG, "session not started");
            return;
        }

        mControlledDream.unregisterLifecycleListener(mLifecycleListener);
        mContext.unbindService(mServiceConnection);
    }

    /**
     * Returns the last lifecycle observed.
     */
    public @Dreamlifecycle int getLastLifecycle() {
        return mLastLifecycle;
    }
}
