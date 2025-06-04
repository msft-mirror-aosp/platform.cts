/*
 * Copyright 2025 The Android Open Source Project
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

package android.media.projection.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.Instrumentation;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.cts.MediaProjectionRule;
import android.media.projection.AppContentProjectionService;
import android.media.projection.IAppContentProjectionCallback;
import android.media.projection.IAppContentProjectionSession;
import android.media.projection.MediaProjectionAppContent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallback;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Size;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.permissions.PermissionContextImpl;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.internal.util.FunctionalUtils;
import com.android.media.projection.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testng.Assert;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test {@link MediaProjectionAppContent}.
 *
 * <p>Run with: atest CtsMediaProjectionTestCases:MediaProjectionAppContentTest
 */
@FrameworkSpecificTest
@RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
public class MediaProjectionAppContentTest {

    private static final int THUMBNAIL_SIZE = 5;
    private static final int TIMEOUT_MILLIS = 5000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private Context mContext;

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getTargetContext();
        MediaProjectionAppContentService.reset();
    }

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        int width = 10;
        int height = 20;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        String title = "title";
        int id = 1234;
        MediaProjectionAppContent appContent = new MediaProjectionAppContent(bitmap, title, id);
        assertThat(appContent.getId()).isEqualTo(id);
        assertThat(appContent.getTitle()).isEqualTo(title);
        assertThat(appContent.getThumbnail().getWidth()).isEqualTo(width);
        assertThat(appContent.getThumbnail().getHeight()).isEqualTo(height);
        Parcel parcel = Parcel.obtain();
        appContent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MediaProjectionAppContent unparcel =
                MediaProjectionAppContent.CREATOR.createFromParcel(parcel);
        assertThat(unparcel.getId()).isEqualTo(id);
        assertThat(unparcel.getTitle()).isEqualTo(title);
        assertThat(unparcel.getThumbnail().getWidth()).isEqualTo(width);
        assertThat(unparcel.getThumbnail().getHeight()).isEqualTo(height);
    }

    @Test
    public void onContentRequest_returnsContent() throws Exception {
        AsyncRef<MediaProjectionAppContent[]> appContentsRef = new AsyncRef<>();
        RemoteCallback contentConsumer =
                new RemoteCallback(
                        result -> {
                            Objects.requireNonNull(result);
                            MediaProjectionAppContent[] parcelableArray =
                                    result.getParcelableArray(
                                            MediaProjectionAppContentService.EXTRA_APP_CONTENT,
                                            MediaProjectionAppContent.class);
                            appContentsRef.set(parcelableArray);
                        });
        withServiceConnected(
                true /* grantPermission */,
                contentProjectionCallback -> {
                    contentProjectionCallback.onContentRequest(
                            contentConsumer, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                });
        MediaProjectionAppContent[] appContents = appContentsRef.waitAndGet();
        assertThat(appContents).isNotNull();
        assertThat(appContents)
                .asList()
                .containsExactlyElementsIn(MediaProjectionAppContentService.TEST_APP_CONTENT_LIST);
        assertThat(MediaProjectionAppContentService.sState)
                .isEqualTo(MediaProjectionAppContentService.State.CONTENT_REQUESTED);
    }

    @Test
    public void onContentRequest_appContentRequest_validateData() throws Exception {
        AsyncRef<MediaProjectionAppContent[]> appContentsRef = new AsyncRef<>();
        RemoteCallback contentConsumer =
                new RemoteCallback(
                        result -> {
                            Objects.requireNonNull(result);
                            MediaProjectionAppContent[] parcelableArray =
                                    result.getParcelableArray(
                                            MediaProjectionAppContentService.EXTRA_APP_CONTENT,
                                            MediaProjectionAppContent.class);
                            appContentsRef.set(parcelableArray);
                        });
        withServiceConnected(
                true /* grantPermission */,
                contentProjectionCallback -> {
                    contentProjectionCallback.onContentRequest(
                            contentConsumer, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
                });
        appContentsRef.waitAndGet();
        assertThat(MediaProjectionAppContentService.sLastAppContentRequest).isNotNull();
        assertThat(MediaProjectionAppContentService.sLastAppContentRequest.getThumbnailSize())
                .isEqualTo(new Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE));
    }

    @Test
    public void onLoopbackProjectionStarted_called() throws Exception {
        withServiceConnected(
                true /* grantPermission */,
                contentProjectionCallback ->
                        contentProjectionCallback.onLoopbackProjectionStarted(
                                new IAppContentProjectionSession.Default(), 0));
        assertThat(MediaProjectionAppContentService.sState)
                .isEqualTo(MediaProjectionAppContentService.State.SESSION_STARTED);
    }

    @Test
    public void onSessionStopped_called() throws Exception {
        withServiceConnected(
                true /* grantPermission */, IAppContentProjectionCallback::onSessionStopped);
        assertThat(MediaProjectionAppContentService.sState)
                .isEqualTo(MediaProjectionAppContentService.State.SESSION_STOPPED);
    }

    @Test
    public void startedAndStoppedSession_areSame() throws Exception {
        withServiceConnected(
                true /* grantPermission */,
                contentProjectionCallback -> {
                    contentProjectionCallback.onLoopbackProjectionStarted(createStubSession(), 0);
                    contentProjectionCallback.onSessionStopped();
                });

        assertThat(MediaProjectionAppContentService.sStartedSession).isNotNull();
        assertThat(MediaProjectionAppContentService.sStoppedSession)
                .isEqualTo(MediaProjectionAppContentService.sStartedSession);
    }

    @Test
    public void serviceSideSessionStop_sessionStopped() throws Exception {
        IAppContentProjectionSession projectionSession = mock(IAppContentProjectionSession.class);
        withServiceConnected(
                true /* grantPermission */,
                contentProjectionCallback -> {
                    contentProjectionCallback.onLoopbackProjectionStarted(projectionSession, 0);
                    MediaProjectionAppContentService.stopSession();
                });

        verify(projectionSession, times(1)).notifySessionStop();
    }

    @Test
    public void missingPermission_throwsSecurityException() throws Exception {
        withServiceConnected(
                false /* grantPermission */,
                contentProjectionCallback -> {
                    Assert.expectThrows(
                            SecurityException.class,
                            () ->
                                    contentProjectionCallback.onContentRequest(
                                            new RemoteCallback(result -> {}), 0, 0));
                    Assert.expectThrows(
                            SecurityException.class,
                            () ->
                                    contentProjectionCallback.onLoopbackProjectionStarted(
                                            createStubSession(), 0));
                    Assert.expectThrows(
                            SecurityException.class,
                            contentProjectionCallback::onContentRequestCanceled);
                    Assert.expectThrows(
                            SecurityException.class, contentProjectionCallback::onSessionStopped);
                });
        assertThat(MediaProjectionAppContentService.sStartedSession).isNull();
        assertThat(MediaProjectionAppContentService.sStoppedSession).isNull();
    }

    private static IAppContentProjectionSession createStubSession() {
        return new IAppContentProjectionSession.Default();
    }

    private void withServiceConnected(
            boolean grantPermission,
            FunctionalUtils.ThrowingConsumer<IAppContentProjectionCallback> callback)
            throws Exception {
        AsyncRef<IBinder> serviceBinderRef = new AsyncRef<>();
        ServiceConnection serviceConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        serviceBinderRef.set(service);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                };
        try {
            Intent service =
                    new Intent(AppContentProjectionService.SERVICE_INTERFACE)
                            .setPackage(mMediaProjectionRule.getTargetPackage());

            boolean bound =
                    mContext.bindService(service, serviceConnection, Service.BIND_AUTO_CREATE);
            if (!bound) {
                throw new RuntimeException(
                        "Failed to bind to %s service"
                                .formatted(MediaProjectionAppContentService.class.getName()));
            }

            IBinder serviceBinder = serviceBinderRef.waitAndGet();
            IAppContentProjectionCallback contentProjectionCallback =
                    IAppContentProjectionCallback.Stub.asInterface(serviceBinder);

            if (grantPermission) {
                try (PermissionContextImpl ignored =
                        TestApis.permissions()
                                .withPermission(Manifest.permission.MANAGE_MEDIA_PROJECTION)) {
                    callback.accept(contentProjectionCallback);
                }
            } else {
                callback.accept(contentProjectionCallback);
            }
        } finally {
            mContext.unbindService(serviceConnection);
        }
    }

    private static final class AsyncRef<T> {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final AtomicReference<T> mReference = new AtomicReference<>();

        public T waitAndGet() {
            try {
                if (!mLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException(
                            new TimeoutException("Reference was not set after timeout."));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return mReference.get();
        }

        public void set(T value) {
            if (mLatch.getCount() < 1) {
                throw new IllegalStateException("Reference was already set once");
            }
            mReference.set(value);
            mLatch.countDown();
        }
    }
}
