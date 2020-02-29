/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.appenumeration.cts.query;

import static android.content.Intent.EXTRA_RETURN_RESULT;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.SparseArray;

public class TestActivity extends Activity {

    SparseArray<RemoteCallback> callbacks = new SparseArray<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        RemoteCallback remoteCallback = intent.getParcelableExtra("remoteCallback");
        final String action = intent.getAction();
        final Intent queryIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if ("android.appenumeration.cts.action.GET_PACKAGE_INFO".equals(action)) {
            final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            sendPackageInfo(remoteCallback, packageName);
        } else if ("android.appenumeration.cts.action.START_FOR_RESULT".equals(action)) {
            final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            int requestCode = RESULT_FIRST_USER + callbacks.size();
            callbacks.put(requestCode, remoteCallback);
            startActivityForResult(
                    new Intent("android.appenumeration.cts.action.SEND_RESULT").setComponent(
                            new ComponentName(packageName, getClass().getCanonicalName())),
                    requestCode);
            // don't send anything... await result callback
        } else if ("android.appenumeration.cts.action.SEND_RESULT".equals(action)) {
            try {
                setResult(RESULT_OK,
                        getIntent().putExtra(
                                Intent.EXTRA_RETURN_RESULT,
                                getPackageManager().getPackageInfo(getCallingPackage(), 0)));
            } catch (PackageManager.NameNotFoundException e) {
                setResult(RESULT_FIRST_USER, new Intent().putExtra("error", e));
            }
            finish();
        } else if ("android.appenumeration.cts.action.QUERY_INTENT_ACTIVITIES".equals(action)) {
            sendQueryIntentActivities(remoteCallback, queryIntent);
        } else if ("android.appenumeration.cts.action.QUERY_INTENT_SERVICES".equals(action)) {
            sendQueryIntentServices(remoteCallback, queryIntent);
        } else if ("android.appenumeration.cts.action.QUERY_INTENT_PROVIDERS".equals(action)) {
            sendQueryIntentProviders(remoteCallback, queryIntent);
        } else {
            sendError(remoteCallback, new Exception("unknown action " + action));
        }
    }

    private void sendQueryIntentActivities(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentActivities(
                queryIntent, 0 /* flags */).stream()
                .map(ri -> ri.activityInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendQueryIntentServices(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentServices(
                queryIntent, 0 /* flags */).stream()
                .map(ri -> ri.serviceInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendQueryIntentProviders(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentContentProviders(
                queryIntent, 0 /* flags */).stream()
                .map(ri -> ri.providerInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendError(RemoteCallback remoteCallback, Exception failure) {
        Bundle result = new Bundle();
        result.putSerializable("error", failure);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendPackageInfo(RemoteCallback remoteCallback, String packageName) {
        final PackageInfo pi;
        try {
            pi = getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            sendError(remoteCallback, e);
            return;
        }
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, pi);
        remoteCallback.sendResult(result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final RemoteCallback remoteCallback = callbacks.get(requestCode);
        if (resultCode != RESULT_OK) {
            Exception e = (Exception) data.getSerializableExtra("error");
            sendError(remoteCallback, e == null ? new Exception("Result was " + resultCode) : e);
            return;
        }
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, data.getParcelableExtra(EXTRA_RETURN_RESULT));
        remoteCallback.sendResult(result);
        finish();
    }
}