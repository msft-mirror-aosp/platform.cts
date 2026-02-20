/*
 * Copyright 2024 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfos;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.view.cts.util.ASurfaceControlInputReceiverTestUtils.nCreateInputReceiver;
import static android.view.cts.util.ASurfaceControlInputReceiverTestUtils.nDeleteInputReceiver;
import static android.view.cts.util.ASurfaceControlInputReceiverTestUtils.nGetInputTransferToken;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_create;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_fromJava;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceControl_release;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_apply;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_create;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_releaseBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_reparent;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setOnCommitCallback;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setSolidBuffer;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setVisibility;

import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withCoords;
import static com.android.cts.input.inputeventmatchers.InputEventMatchersKt.withMotionAction;

import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.cts.util.ASurfaceControlInputReceiverTestUtils.InputReceiver;
import android.view.cts.util.EmbeddedSCVHService;
import android.view.cts.util.aidl.IAttachEmbeddedWindow;
import android.view.cts.util.aidl.IMotionEventReceiver;
import android.window.InputTransferToken;
import android.window.WindowInfosListenerForTest;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.backportedfixes.BackportedFixRule;
import com.android.cts.backportedfixes.BackportedFixTest;
import com.android.cts.input.BlockingQueueEventVerifier;
import com.android.cts.input.FailOnTestThreadRule;
import com.android.cts.input.UinputTouchDevice;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.input.inputeventmatchers.InputEventMatchersKt;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Presubmit
@RunWith(TestParameterInjector.class)
public class ASurfaceControlInputReceiverTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "ASurfaceControlInputReceiverTest";
    private TestActivity mActivity;
    private static final Rect sBounds = new Rect(0, 0, 100, 100);
    private static final long WAIT_TIME_S = 5L * HW_TIMEOUT_MULTIPLIER;

    private static final String sEmbeddedName = "SurfaceControl_create";

    private WindowManager mWm;
    private UinputTouchDevice mTouchScreen;

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule
    public FailOnTestThreadRule mFailOnTestThreadRule = new FailOnTestThreadRule();

    @Rule public BackportedFixRule mBackportedFixRule = new BackportedFixRule();

    @Before
    public void setUp() throws InterruptedException, RemoteException {
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
        mWm = mActivity.getWindowManager();
        mTouchScreen =
                new UinputTouchScreen(
                        InstrumentationRegistry.getInstrumentation(), mActivity.getDisplay());
        waitForWindowOnTop(mActivity.getWindow());
    }

    @After
    public void tearDown() {
        if (mTouchScreen != null) {
            mTouchScreen.close();
        }
    }

    private void testLocalASurfaceControlReceivesInput(boolean batched)
            throws InterruptedException {
        LocalSurfaceControlInputReceiverHelper helper = new LocalSurfaceControlInputReceiverHelper(
                mActivity, true /* zOrderOnTop */, batched);

        final LinkedBlockingQueue<InputEvent> events = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier verifier = new BlockingQueueEventVerifier(events);
        helper.setup(
                null,
                new InputReceiver() {
                    @Override
                    public boolean onMotionEvent(MotionEvent motionEvent) {
                        events.add(MotionEvent.obtain(motionEvent));
                        return false;
                    }

                    @Override
                    public boolean onKeyEvent(KeyEvent keyEvent) {
                        return false;
                    }
                });
        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);
        final Point tapCoord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        mTouchScreen.touchDown(tapCoord).lift();

        assertMotionEventOnWindowCenter(verifier, bounds);
    }

    private void assertMotionEventOnWindowCenter(
            BlockingQueueEventVerifier verifier, Rect windowBounds) {
        // As the surface view is being attached to the contentView, it will always start from
        // (0, 0) within the activity window. But there is no guarantee that Activity window itself
        // is at (0, 0) even in immersive mode. To correctly check the value, center of the activity
        // bounds should be obtained instead of off-setting which is needed to tap at right place.
        final Point centerCoordRelativeToWindow = new Point(windowBounds.width() / 2,
                windowBounds.height() / 2);
        verifier.assertReceivedMotion(
                allOf(
                        withMotionAction(MotionEvent.ACTION_DOWN),
                        withCoords(centerCoordRelativeToWindow, InputEventMatchersKt.EPSILON)),
                "Failed to receive touch");
    }

    @Test
    public void testLocalASurfaceControlReceivesInput() throws InterruptedException {
        testLocalASurfaceControlReceivesInput(true /* batched */);
    }

    @Test
    public void testNonBatchedASurfaceControlReceivesInput() throws InterruptedException {
        testLocalASurfaceControlReceivesInput(false /* batched */);
    }

    @Test
    public void testRemoteASurfaceControlReceivesInput()
            throws InterruptedException {
        RemoteSurfaceControlInputReceiverHelper helper =
                new RemoteSurfaceControlInputReceiverHelper(
                        mActivity,
                        true /* zOrderOnTop */,
                        true /* batched */,
                        false /* transferTouchToHost */);

        final LinkedBlockingQueue<InputEvent> events = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier verifier = new BlockingQueueEventVerifier(events);
        helper.setup(
                null,
                new IMotionEventReceiver.Stub() {
                    @Override
                    public void onMotionEventReceived(MotionEvent motionEvent) {
                        events.add(MotionEvent.obtain(motionEvent));
                    }
                });

        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);

        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        mTouchScreen.touchDown(coord).lift();

        assertMotionEventOnWindowCenter(verifier, bounds);
    }

    @Test
    public void testTransferGestureFromHostToEmbedded() throws InterruptedException {
        LocalSurfaceControlInputReceiverHelper helper = new LocalSurfaceControlInputReceiverHelper(
                mActivity, false /* zOrderOnTop */, true /* batched */);

        final LinkedBlockingQueue<InputEvent> embeddedEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier embeddedVerifier =
                new BlockingQueueEventVerifier(embeddedEvents);

        final LinkedBlockingQueue<InputEvent> hostEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier hostVerifier = new BlockingQueueEventVerifier(hostEvents);
        helper.setup(
                (v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mWm.transferTouchGesture(
                                mActivity
                                        .getWindow()
                                        .getRootSurfaceControl()
                                        .getInputTransferToken(),
                                helper.mEmbeddedTransferToken);
                    }
                    hostEvents.add(MotionEvent.obtain(event));
                    return true;
                },
                new InputReceiver() {
                    @Override
                    public boolean onMotionEvent(MotionEvent motionEvent) {
                        embeddedEvents.add(MotionEvent.obtain(motionEvent));
                        return false;
                    }

                    @Override
                    public boolean onKeyEvent(KeyEvent keyEvent) {
                        return false;
                    }
                });
        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);
        final Point coord =
                new Point(bounds.left + bounds.width() / 2, bounds.top + bounds.height() / 2);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(coord);

        hostVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_DOWN), "Failed to receive DOWN event on host");
        hostVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_CANCEL),
                "Failed to receive CANCEL event on host");

        pointer.lift();
        assertMotionEventOnWindowCenter(embeddedVerifier, bounds);
    }

    @Test
    @TestParameters({"{batched: true}", "{batched: false}"})
    public void testTransferGestureFromHostToEmbeddedRemote(boolean batched)
            throws InterruptedException, RemoteException {
        RemoteSurfaceControlInputReceiverHelper helper =
                new RemoteSurfaceControlInputReceiverHelper(
                        mActivity,
                        false /* zOrderOnTop */,
                        batched,
                        false /* transferTouchToHost */);

        final LinkedBlockingQueue<InputEvent> embeddedEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier embeddedVerifier =
                new BlockingQueueEventVerifier(embeddedEvents);
        final LinkedBlockingQueue<InputEvent> hostEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier hostVerifier = new BlockingQueueEventVerifier(hostEvents);
        helper.setup(
                (v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mWm.transferTouchGesture(
                                mActivity
                                        .getWindow()
                                        .getRootSurfaceControl()
                                        .getInputTransferToken(),
                                helper.mEmbeddedTransferToken);
                    }
                    hostEvents.add(MotionEvent.obtain(event));
                    return true;
                },
                new IMotionEventReceiver.Stub() {
                    @Override
                    public void onMotionEventReceived(MotionEvent motionEvent) {
                        embeddedEvents.add(MotionEvent.obtain(motionEvent));
                    }
                });
        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);
        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(coord);

        hostVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_DOWN), "Failed to receive DOWN event on host");
        hostVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_CANCEL),
                "Failed to receive CANCEL event on host");

        pointer.lift();
        assertMotionEventOnWindowCenter(embeddedVerifier, bounds);
    }

    @Test
    public void testTransferGestureFromEmbeddedToHost() throws InterruptedException {
        LocalSurfaceControlInputReceiverHelper helper = new LocalSurfaceControlInputReceiverHelper(
                mActivity, true /* zOrderOnTop */, false /* batched */);
        final LinkedBlockingQueue<InputEvent> embeddedEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier embeddedVerifier =
                new BlockingQueueEventVerifier(embeddedEvents);
        final LinkedBlockingQueue<InputEvent> hostEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier hostVerifier = new BlockingQueueEventVerifier(hostEvents);
        helper.setup(
                (v, event) -> {
                    hostEvents.add(MotionEvent.obtain(event));
                    return false;
                },
                new InputReceiver() {
                    @Override
                    public boolean onMotionEvent(MotionEvent motionEvent) {
                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                            mWm.transferTouchGesture(
                                    helper.mEmbeddedTransferToken,
                                    mActivity
                                            .getWindow()
                                            .getRootSurfaceControl()
                                            .getInputTransferToken());
                        }
                        embeddedEvents.add(MotionEvent.obtain(motionEvent));
                        return false;
                    }

                    @Override
                    public boolean onKeyEvent(KeyEvent keyEvent) {
                        return false;
                    }
                });
        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);
        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(coord);

        embeddedVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_DOWN),
                "Failed to receive DOWN event on embedded");
        embeddedVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_CANCEL),
                "Failed to receive CANCEL event on embedded");

        pointer.lift();
        assertMotionEventOnWindowCenter(hostVerifier, bounds);
    }

    @Test
    public void testTransferGestureFromEmbeddedToHostRemote()
            throws InterruptedException, RemoteException {
        RemoteSurfaceControlInputReceiverHelper helper =
                new RemoteSurfaceControlInputReceiverHelper(
                        mActivity,
                        true /* zOrderOnTop */,
                        true /* batched */,
                        true /* transferTouchToHost */);

        final LinkedBlockingQueue<InputEvent> embeddedEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier embeddedVerifier =
                new BlockingQueueEventVerifier(embeddedEvents);
        final LinkedBlockingQueue<InputEvent> hostEvents = new LinkedBlockingQueue<>();
        final BlockingQueueEventVerifier hostVerifier = new BlockingQueueEventVerifier(hostEvents);
        helper.setup(
                (v, event) -> {
                    hostEvents.add(MotionEvent.obtain(event));
                    return false;
                },
                new IMotionEventReceiver.Stub() {
                    @Override
                    public void onMotionEventReceived(MotionEvent motionEvent) {
                        embeddedEvents.add(MotionEvent.obtain(motionEvent));
                    }
                });
        Rect bounds = new Rect();
        assertWindowAndGetBounds(mActivity.getDisplayId(), bounds);
        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        UinputTouchDevice.Pointer pointer = mTouchScreen.touchDown(coord);

        embeddedVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_DOWN),
                "Failed to receive DOWN event on embedded");
        embeddedVerifier.assertReceivedMotion(
                withMotionAction(MotionEvent.ACTION_CANCEL),
                "Failed to receive CANCEL event on embedded");

        pointer.lift();
        assertMotionEventOnWindowCenter(hostVerifier, bounds);
    }

    private static void assertWindowAndGetBounds(int displayId, Rect outBounds)
            throws InterruptedException {
        boolean success = waitForWindowInfos(
                windowInfos -> {
                    for (var windowInfo : windowInfos) {
                        if (getBoundsIfWindowIsVisible(windowInfo, displayId,
                                sEmbeddedName, outBounds)) {
                            return true;
                        }
                    }
                    return false;
                }, Duration.ofSeconds(WAIT_TIME_S));
        assertAndDumpWindowState(TAG, "Failed to find embedded SC on top", success);
    }

    private static boolean getBoundsIfWindowIsVisible(
            WindowInfosListenerForTest.WindowInfo windowInfo, int displayId, String name,
            Rect outBounds) {
        if (!windowInfo.isVisible || windowInfo.displayId != displayId) {
            return false;
        }
        if (!windowInfo.name.contains(name)) {
            return false;
        }

        if (!windowInfo.bounds.isEmpty()) {
            outBounds.set(windowInfo.bounds);
            return true;
        }
        return false;
    }

    private static class LocalSurfaceControlInputReceiverHelper {
        private final Activity mActivity;
        private final boolean mZOrderOnTop;
        private final boolean mBatched;

        private long mEmbeddedSc;
        private long mBuffer;
        private long mNativeBatchedInputReceiver;

        private InputTransferToken mEmbeddedTransferToken;

        LocalSurfaceControlInputReceiverHelper(Activity activity, boolean zOrderOnTop,
                boolean batched) {
            mActivity = activity;
            mZOrderOnTop = zOrderOnTop;
            mBatched = batched;
        }

        public void setup(View.OnTouchListener hostTouchListener,
                InputReceiver inputReceiver) throws InterruptedException {
            final CountDownLatch drawCompleteLatch = new CountDownLatch(1);

            // Place the child z order on top so it gets touch first and can transfer to host
            SurfaceView surfaceView = new SurfaceView(mActivity.getApplicationContext());
            surfaceView.setZOrderOnTop(mZOrderOnTop);
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    mEmbeddedSc = nSurfaceControl_create(
                            nSurfaceControl_fromJava(surfaceView.getSurfaceControl()));
                    long surfaceTransaction = nSurfaceTransaction_create();
                    nSurfaceTransaction_setVisibility(mEmbeddedSc, surfaceTransaction, true);
                    mBuffer = nSurfaceTransaction_setSolidBuffer(mEmbeddedSc, surfaceTransaction,
                            sBounds.width(), sBounds.height(), Color.RED);
                    nSurfaceTransaction_setOnCommitCallback(surfaceTransaction,
                            (latchTime, presentTime) -> drawCompleteLatch.countDown());
                    nSurfaceTransaction_apply(surfaceTransaction);

                    mNativeBatchedInputReceiver = nCreateInputReceiver(mBatched,
                            surfaceView.getRootSurfaceControl().getInputTransferToken(),
                            mEmbeddedSc, inputReceiver);

                    mEmbeddedTransferToken = nGetInputTransferToken(mNativeBatchedInputReceiver);
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                        int height) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    long surfaceTransaction = nSurfaceTransaction_create();
                    nSurfaceTransaction_reparent(mEmbeddedSc, 0, surfaceTransaction);
                    nSurfaceTransaction_apply(surfaceTransaction);
                    nSurfaceControl_release(mEmbeddedSc);

                    nSurfaceTransaction_releaseBuffer(mBuffer);
                    nDeleteInputReceiver(mNativeBatchedInputReceiver);
                }
            });

            mActivity.runOnUiThread(() -> mActivity.setContentView(surfaceView));

            assertTrue("Failed to wait for child SC to draw",
                    drawCompleteLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            surfaceView.setOnTouchListener(hostTouchListener);
            waitForStableWindowGeometry(Duration.ofSeconds(WAIT_TIME_S));
        }
    }

    private class RemoteSurfaceControlInputReceiverHelper {
        private final Activity mActivity;
        private final boolean mZOrderOnTop;
        private final boolean mBatched;
        private final boolean mTransferTouchToHost;
        private IAttachEmbeddedWindow mIAttachEmbeddedWindow;

        private InputTransferToken mEmbeddedTransferToken;

        RemoteSurfaceControlInputReceiverHelper(
                Activity activity,
                boolean zOrderOnTop,
                boolean batched,
                boolean transferTouchToHost) {
            mActivity = activity;
            mZOrderOnTop = zOrderOnTop;
            mBatched = batched;
            mTransferTouchToHost = transferTouchToHost;
        }

        public void setup(View.OnTouchListener hostTouchListener,
                IMotionEventReceiver.Stub motionEventReceiver)
                throws InterruptedException {
            SurfaceView surfaceView = new SurfaceView(mActivity.getApplicationContext());
            surfaceView.setZOrderOnTop(mZOrderOnTop);

            CountDownLatch embeddedServiceReady = new CountDownLatch(1);
            mActivity.runOnUiThread(() -> {
                ServiceConnection mConnection = new ServiceConnection() {
                    // Called when the connection with the service is established
                    public void onServiceConnected(ComponentName className, IBinder service) {
                        mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service);
                        embeddedServiceReady.countDown();
                    }

                    public void onServiceDisconnected(ComponentName className) {
                        mIAttachEmbeddedWindow = null;
                    }
                };

                Intent intent = new Intent(mActivity, EmbeddedSCVHService.class);
                intent.setAction(IAttachEmbeddedWindow.class.getName());
                mActivity.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            });
            assertTrue("Failed to wait for embedded service to bind",
                    embeddedServiceReady.await(WAIT_TIME_S, TimeUnit.SECONDS));

            final CountDownLatch surfaceViewCreatedLatch = new CountDownLatch(1);
            surfaceView
                    .getHolder()
                    .addCallback(
                            new SurfaceHolder.Callback() {
                                @Override
                                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                                    try {
                                        boolean success =
                                                mIAttachEmbeddedWindow
                                                        .attachEmbeddedASurfaceControl(
                                                                surfaceView.getSurfaceControl(),
                                                                surfaceView
                                                                        .getRootSurfaceControl()
                                                                        .getInputTransferToken(),
                                                                sBounds.width(),
                                                                sBounds.height(),
                                                                mBatched,
                                                                mTransferTouchToHost,
                                                                motionEventReceiver);
                                        mEmbeddedTransferToken =
                                                mIAttachEmbeddedWindow
                                                        .getEmbeddedInputTransferToken();
                                        if (!success) {
                                            mFailOnTestThreadRule.addFailure(
                                                    new Exception(
                                                            "attachEmbeddedASurfaceControl"
                                                                    + " failed"));
                                        }
                                        surfaceViewCreatedLatch.countDown();
                                    } catch (RemoteException e) {
                                        mFailOnTestThreadRule.addFailure(e);
                                    }
                                }

                                @Override
                                public void surfaceChanged(
                                        @NonNull SurfaceHolder holder,
                                        int format,
                                        int width,
                                        int height) {}

                                @Override
                                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                                    try {
                                        mIAttachEmbeddedWindow.tearDownEmbeddedASurfaceControl();
                                    } catch (RemoteException e) {
                                        mFailOnTestThreadRule.addFailure(e);
                                    }
                                }
                            });

            mActivity.runOnUiThread(() -> mActivity.setContentView(surfaceView));

            assertTrue("Failed to attach ASurfaceControl",
                    surfaceViewCreatedLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
            surfaceView.setOnTouchListener(hostTouchListener);
            waitForStableWindowGeometry(Duration.ofSeconds(WAIT_TIME_S));
        }
    }

    @Test
    @BackportedFixTest(385124056)
    public void debuggable() {
        // Setting the test application as debuggable to enable checkjni which will identify
        // JNI calls with incorrect signatures.
        // See https://developer.android.com/training/articles/perf-jni#extended-checking
        assertTrue("android:debuggable of the <application> tag",
                (mActivity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    }
}
