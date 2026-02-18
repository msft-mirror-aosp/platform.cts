/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.ondeviceintelligence.cts.coveragetests;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ON_DEVICE_INTELLIGENCE_26Q2;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ondeviceintelligence.Content;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.IDownloadCallback;
import android.app.ondeviceintelligence.IOnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.ITokenInfoCallback;
import android.app.ondeviceintelligence.ModelDownloadCallback;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.Part;
import android.app.ondeviceintelligence.TokenInfo;
import android.app.ondeviceintelligence.imagedescription.ImageDescriptionModel;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ImageDescriptionModelTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testImageDescriptionModelMethods() {
        Feature feature = new Feature.Builder(1).build();
        android.os.LocaleList locales = android.os.LocaleList.forLanguageTags("en-US");
        ImageDescriptionModel model = new ImageDescriptionModel(feature, "signature", 100, locales);

        assertThat(model.getMaxTokenLimit()).isEqualTo(100);
        assertThat(model.getModelSignature()).isEqualTo("signature");
        assertThat(model.getSupportedLocales()).isEqualTo(locales);
        assertThat(model.getName()).isEqualTo("signature");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testCountTokens() throws Exception {
        Feature feature = new Feature.Builder(1).build();
        ImageDescriptionModel model =
                new ImageDescriptionModel(
                        feature, "signature", 100, android.os.LocaleList.getEmptyLocaleList());
        IOnDeviceIntelligenceManager mockService = mock(IOnDeviceIntelligenceManager.class);
        OnDeviceIntelligenceManager manager = createManager(mockService);
        injectManager(model, manager);

        Content content = new Content(List.of(Part.createText("test")));
        Executor executor = Runnable::run;
        OutcomeReceiver<Long, OnDeviceIntelligenceException> callback = mock(OutcomeReceiver.class);

        model.countTokens(content, executor, callback);

        ArgumentCaptor<ITokenInfoCallback> captor = ArgumentCaptor.forClass(ITokenInfoCallback.class);
        verify(mockService)
                .requestTokenInfoWithContent(eq(feature), eq(content), any(), captor.capture());

        TokenInfo tokenInfo = new TokenInfo(10L);
        captor.getValue().onSuccess(tokenInfo);
        verify(callback).onResult(10L);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ON_DEVICE_INTELLIGENCE_26Q2)
    public void testDownload() throws Exception {
        Feature feature = new Feature.Builder(1).build();
        ImageDescriptionModel model =
                new ImageDescriptionModel(
                        feature, "signature", 100, android.os.LocaleList.getEmptyLocaleList());
        IOnDeviceIntelligenceManager mockService = mock(IOnDeviceIntelligenceManager.class);
        OnDeviceIntelligenceManager manager = createManager(mockService);
        injectManager(model, manager);

        CancellationSignal cancellationSignal = new CancellationSignal();
        Executor executor = Runnable::run;
        ModelDownloadCallback callback = mock(ModelDownloadCallback.class);

        model.download(cancellationSignal, executor, callback);

        ArgumentCaptor<IDownloadCallback> captor = ArgumentCaptor.forClass(IDownloadCallback.class);
        verify(mockService)
                .requestFeatureDownload(
                        eq(feature), any(), captor.capture());

        captor.getValue().onDownloadCompleted(android.os.PersistableBundle.EMPTY);
        verify(callback).onDownloadCompleted();
    }

    private void injectManager(ImageDescriptionModel model, OnDeviceIntelligenceManager manager)
            throws Exception {
        java.lang.reflect.Method method =
                ImageDescriptionModel.class.getDeclaredMethod(
                        "setOnDeviceIntelligenceManager", OnDeviceIntelligenceManager.class);
        method.setAccessible(true);
        method.invoke(model, manager);
    }

    private OnDeviceIntelligenceManager createManager(IOnDeviceIntelligenceManager service) throws Exception {
        Context mockContext = mock(Context.class);
        java.lang.reflect.Constructor<OnDeviceIntelligenceManager> constructor =
                OnDeviceIntelligenceManager.class.getDeclaredConstructor(Context.class, IOnDeviceIntelligenceManager.class);
        // It's public but hidden
        return constructor.newInstance(mockContext, service);
    }
}
