/**
 * Copyright (c) 2026, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.cts.testapp;

import android.os.Bundle;
import android.os.ParcelUuid;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.InsightFilter;
import android.service.personalcontext.insight.interaction.InsightEvent;

/** Callback for ITestAppController. */
interface ITestAppComponentListener {

    void onReady();

    // Refiner callbacks.
    void onRefinerConnected();
    HintFilter onRefinerInitializeFilter();
    List<Bundle> onRefinerRefine(in List<Bundle> hints);

    // Understander callbacks.
    void onUnderstanderConnected();
    HintFilter onUnderstanderInitializeFilter();
    List<Bundle> onUnderstanderUnderstand(in List<PublishedContextHintWrapper> hints);
    void onUnderstanderEvent(String packageName, in InsightEvent event);

    // Renderer callbacks.
    void onRendererConnected(in RenderToken renderToken);
    InsightFilter onRendererInitializeFilter();
    void onRendererRender(in Bundle insight, in RenderToken renderToken);
}