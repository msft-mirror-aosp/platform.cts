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

package android.service.personalcontext.cts.testapp.components;

import android.content.ComponentName;
import android.os.RemoteException;
import android.service.personalcontext.cts.testapp.TestAppController;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.refiner.HintRefinerService;
import android.util.Log;

import java.util.List;

/** Base class for repurposable refiner for CTS tests. */
public abstract class BaseHintRefinerService extends HintRefinerService {
    private static final String TAG = "CtsRefiner";

    public BaseHintRefinerService() {
        setExecutor(TestAppController.getExecutor());
    }

    private ComponentName getComponentName() {
        return new ComponentName(this, this.getClass());
    }

    @Override
    public void onConnected() {
        Log.w(TAG, getClass().getSimpleName() + ".onConnected() start");
        try {
            TestAppController.getListener(getComponentName()).onRefinerConnected();
        } catch (Exception e) {
            // Do nothing.
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onConnected() end");
        }
    }

    @Override
    public HintFilter onInitializeFilter() {
        Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() start");
        try {
            HintFilter filter =
                    TestAppController.getListener(getComponentName()).onRefinerInitializeFilter();
            Log.w(TAG, "  Filter: " + filter);
            TestAppController.getExecutor().execute(this::onReady);
            return filter;
        } catch (Exception e) {
            Log.w(TAG, "  Filter Error: " + e, e);
            return new HintFilter.Builder()
                    .addBundleHintTypeName("[No hints]", HintFilter.FILTER_TYPE_REQUIRED)
                    .build();
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onInitializeFilter() end");
        }
    }

    @Override
    public List<ContextHint> onRefine(List<ContextHint> inputHints) {
        Log.w(TAG, getClass().getSimpleName() + ".onRefine() start");
        try {
            Log.w(TAG, "  Received " + inputHints.size() + " hints");
            for (ContextHint hint : inputHints) {
                Log.w(TAG, "    - " + hint);
            }

            List<ContextHint> outputHints =
                    TestAppController.getListener(getComponentName())
                            .onRefinerRefine(
                                    inputHints.stream().map(ContextHint::toBundle).toList())
                            .stream()
                            .map(ContextHint::createHintFromBundle)
                            .toList();

            Log.w(TAG, "  Returned " + outputHints.size() + " hints");
            for (ContextHint hint : outputHints) {
                Log.w(TAG, "    - " + hint);
            }

            return outputHints;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Log.w(TAG, getClass().getSimpleName() + ".onRefine() end");
        }
    }

    private void onReady() {
        try {
            Log.w(TAG, getClass().getSimpleName() + ".onReady() start");
            TestAppController.getListener(getComponentName()).onReady();
        } catch (RemoteException e) {
            Log.e(TAG, getClass().getSimpleName() + ".onReady() failed", e);
        }
    }
}
