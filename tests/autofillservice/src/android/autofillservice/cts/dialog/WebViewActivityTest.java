/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.autofillservice.cts.dialog;


import static android.autofillservice.cts.testcore.Helper.setFillDialogHints;

import android.autofillservice.cts.activities.MyWebView;
import android.autofillservice.cts.activities.WebViewActivity;
import android.autofillservice.cts.commontests.AbstractWebViewTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.Helper;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import org.junit.Test;

public class WebViewActivityTest extends AbstractWebViewTestCase<WebViewActivity> {

    private static final String TAG = "WebViewActivityTest";

    private WebViewActivity mActivity;

    @Override
    protected AutofillActivityTestRule<WebViewActivity> getActivityRule() {
        return new AutofillActivityTestRule<WebViewActivity>(WebViewActivity.class) {

            // TODO(b/111838239): latest WebView implementation calls AutofillManager.isEnabled() to
            // disable autofill for optimization when it returns false, and unfortunately the value
            // returned by that method does not change when the service is enabled / disabled, so we
            // need to start enable the service before launching the activity.
            // Once that's fixed, remove this overridden method.
            @Override
            protected void beforeActivityLaunched() {
                super.beforeActivityLaunched();
                Log.i(TAG, "Setting service before launching the activity");
                enableService();
                setFillDialogHints(sContext, "email:postalAddress:postalCode");
            }

            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    @AppModeFull(reason = "LoginActivityTest is enough")
    public void testVirtualViewsReady_doNothing() throws Exception {
        enableService();
        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        mActivity.notifyViewReady(new String[]{"email"});

        sReplier.assertNoUnhandledFillRequests();
    }
}
