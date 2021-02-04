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

import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGES_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGE_INFO;
import static android.appenumeration.cts.Constants.ACTION_JUST_FINISH;
import static android.appenumeration.cts.Constants.ACTION_QUERY_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_QUERY_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_QUERY_SERVICES;
import static android.appenumeration.cts.Constants.ACTION_SEND_RESULT;
import static android.appenumeration.cts.Constants.ACTION_START_DIRECTLY;
import static android.appenumeration.cts.Constants.ACTION_START_FOR_RESULT;
import static android.appenumeration.cts.Constants.ACTION_START_SENDER_FOR_RESULT;
import static android.appenumeration.cts.Constants.EXTRA_ERROR;
import static android.appenumeration.cts.Constants.EXTRA_FLAGS;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_CALLBACK;
import static android.content.Intent.EXTRA_RETURN_RESULT;

import android.app.Activity;
import android.app.PendingIntent;
import android.appenumeration.cts.Constants;
import android.appenumeration.cts.MissingBroadcastException;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.RemoteCallback;
import android.util.SparseArray;

import java.util.ArrayList;

public class TestActivity extends Activity {

    private final static long TIMEOUT_MS = 3000;

    SparseArray<RemoteCallback> callbacks = new SparseArray<>();

    private Handler mainHandler;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mainHandler = new Handler(getMainLooper());
        backgroundThread = new HandlerThread("testBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        backgroundThread.quitSafely();
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        RemoteCallback remoteCallback = intent.getParcelableExtra(EXTRA_REMOTE_CALLBACK);
        try {
            final String action = intent.getAction();
            final Intent queryIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (ACTION_GET_PACKAGE_INFO.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                sendPackageInfo(remoteCallback, packageName);
            } else if (ACTION_GET_PACKAGES_FOR_UID.equals(action)) {
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                sendPackagesForUid(remoteCallback, uid);
            } else if (ACTION_START_FOR_RESULT.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                int requestCode = RESULT_FIRST_USER + callbacks.size();
                callbacks.put(requestCode, remoteCallback);
                startActivityForResult(
                        new Intent(ACTION_SEND_RESULT).setComponent(
                                new ComponentName(packageName, getClass().getCanonicalName())),
                        requestCode);
                // don't send anything... await result callback
            } else if (ACTION_SEND_RESULT.equals(action)) {
                try {
                    setResult(RESULT_OK,
                            getIntent().putExtra(
                                    Intent.EXTRA_RETURN_RESULT,
                                    getPackageManager().getPackageInfo(getCallingPackage(), 0)));
                } catch (PackageManager.NameNotFoundException e) {
                    setResult(RESULT_FIRST_USER, new Intent().putExtra("error", e));
                }
                finish();
            } else if (ACTION_QUERY_ACTIVITIES.equals(action)) {
                sendQueryIntentActivities(remoteCallback, queryIntent);
            } else if (ACTION_QUERY_SERVICES.equals(action)) {
                sendQueryIntentServices(remoteCallback, queryIntent);
            } else if (ACTION_QUERY_PROVIDERS.equals(action)) {
                sendQueryIntentProviders(remoteCallback, queryIntent);
            } else if (ACTION_START_DIRECTLY.equals(action)) {
                try {
                    startActivity(queryIntent);
                    remoteCallback.sendResult(new Bundle());
                } catch (ActivityNotFoundException e) {
                    sendError(remoteCallback, e);
                }
                finish();
            } else if (ACTION_JUST_FINISH.equals(action)) {
                finish();
            } else if (ACTION_GET_INSTALLED_PACKAGES.equals(action)) {
                sendGetInstalledPackages(remoteCallback, queryIntent.getIntExtra(EXTRA_FLAGS, 0));
            } else if (ACTION_START_SENDER_FOR_RESULT.equals(action)) {
                PendingIntent pendingIntent = intent.getParcelableExtra("pendingIntent");
                int requestCode = RESULT_FIRST_USER + callbacks.size();
                callbacks.put(requestCode, remoteCallback);
                try {
                    startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null,
                            0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    sendError(remoteCallback, e);
                }
            } else if (Constants.ACTION_AWAIT_PACKAGE_REMOVED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(
                        remoteCallback, packageName, Intent.ACTION_PACKAGE_REMOVED, TIMEOUT_MS);
            } else if (Constants.ACTION_AWAIT_PACKAGE_ADDED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(
                        remoteCallback, packageName, Intent.ACTION_PACKAGE_ADDED, TIMEOUT_MS);
            } else if (Constants.ACTION_QUERY_RESOLVER.equals(action)) {
                final String authority = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                queryResolverForVisiblePackages(remoteCallback, authority);
            } else if (Constants.ACTION_BIND_SERVICE.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                bindService(remoteCallback, packageName);
            } else {
                sendError(remoteCallback, new Exception("unknown action " + action));
            }
        } catch (Exception e) {
            sendError(remoteCallback, e);
        }
    }

    private void awaitPackageBroadcast(RemoteCallback remoteCallback, String packageName,
            String action, long timeoutMs) {
        final IntentFilter filter = new IntentFilter(action);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
        final Object token = new Object();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle result = new Bundle();
                result.putString(Constants.EXTRA_DATA, intent.getDataString());
                remoteCallback.sendResult(result);
                mainHandler.removeCallbacksAndMessages(token);
                finish();
            }
        }, filter);
        mainHandler.postDelayed(
                () -> sendError(remoteCallback,
                        new MissingBroadcastException(action, timeoutMs)),
                token, timeoutMs);
    }

    private void sendGetInstalledPackages(RemoteCallback remoteCallback, int flags) {
        String[] packages =
                getPackageManager().getInstalledPackages(flags)
                        .stream().map(p -> p.packageName).distinct().toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, packages);
        remoteCallback.sendResult(result);
        finish();
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

    private void queryResolverForVisiblePackages(RemoteCallback remoteCallback, String authority) {
        backgroundHandler.post(() -> {
            Uri queryUri = Uri.parse("content://" + authority + "/test");
            Cursor query = getContentResolver().query(queryUri, null, null, null, null);
            if (query == null || !query.moveToFirst()) {
                sendError(remoteCallback,
                        new IllegalStateException(
                                "Query of " + queryUri + " could not be completed"));
                return;
            }
            ArrayList<String> visiblePackages = new ArrayList<>();
            while (!query.isAfterLast()) {
                visiblePackages.add(query.getString(0));
                query.moveToNext();
            }
            query.close();

            mainHandler.post(() -> {
                Bundle result = new Bundle();
                result.putStringArray(EXTRA_RETURN_RESULT, visiblePackages.toArray(new String[]{}));
                remoteCallback.sendResult(result);
                finish();
            });

        });
    }

    private void sendError(RemoteCallback remoteCallback, Exception failure) {
        Bundle result = new Bundle();
        result.putSerializable(EXTRA_ERROR, failure);
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

    private void sendPackagesForUid(RemoteCallback remoteCallback, int uid) {
        final String[] packages = getPackageManager().getPackagesForUid(uid);
        final Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, packages);
        remoteCallback.sendResult(result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final RemoteCallback remoteCallback = callbacks.get(requestCode);
        if (resultCode != RESULT_OK) {
            Exception e = (Exception) data.getSerializableExtra(EXTRA_ERROR);
            sendError(remoteCallback, e == null ? new Exception("Result was " + resultCode) : e);
            return;
        }
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, data.getParcelableExtra(EXTRA_RETURN_RESULT));
        remoteCallback.sendResult(result);
        finish();
    }

    private void bindService(RemoteCallback remoteCallback, String packageName) {
        final String SERVICE_NAME = "android.appenumeration.testapp.DummyService";
        final Intent intent = new Intent();
        intent.setClassName(packageName, SERVICE_NAME);
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // No-op
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // No-op
            }

            @Override
            public void onBindingDied(ComponentName name) {
                // Remote service die
                finish();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                // Since the DummyService doesn't implement onBind, it returns null and
                // onNullBinding would be called. Use postDelayed to keep this service
                // connection alive for 3 seconds.
                mainHandler.postDelayed(() -> {
                    unbindService(this);
                    finish();
                }, TIMEOUT_MS);
            }
        };

        final boolean bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, bound);
        remoteCallback.sendResult(result);
        // Don't invoke finish() right here if service is bound successfully to keep the service
        // connection alive since the ServiceRecord would be remove from the ServiceMap once no
        // client is binding the service.
        if (!bound) finish();
    }
}