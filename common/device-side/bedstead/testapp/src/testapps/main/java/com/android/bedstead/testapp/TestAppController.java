/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.testapp;

import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import com.google.android.enterprise.connectedapps.annotations.CrossUser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to issue overall control over a remote test app.
 */
public final class TestAppController {
    private static final String TAG = "TestAppController";

    private static final Map<Long, BaseTestAppBroadcastReceiver> sBroadcastReceivers =
            new HashMap<>();

    /**
     * Register a broadcast receiver.
     *
     * <p>Received broadcasts can be queried using EventLib.
     */
    @CrossUser
    public void registerReceiver(Context context, long receiverId, IntentFilter intentFilter) {
        registerReceiver(context, receiverId, intentFilter, 0);
    }

    /** See {@link #registerReceiver(Context, long, IntentFilter)}. */
    @CrossUser
    public void registerReceiver(
            Context context, long receiverId, IntentFilter intentFilter, int flags) {
        BaseTestAppBroadcastReceiver broadcastReceiver = new BaseTestAppBroadcastReceiver();
        sBroadcastReceivers.put(receiverId, broadcastReceiver);

        context.registerReceiver(broadcastReceiver, intentFilter, flags);
    }

    /**
     * Unregister a previously registered broadcast receiver.
     */
    @CrossUser
    public void unregisterReceiver(Context context, long receiverId) {
        BaseTestAppBroadcastReceiver receiver = sBroadcastReceivers.remove(receiverId);
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
    }

    /**
     * Makes an HTTP request at the given address. Ignores network and DNS errors.
     *
     * @param urlString a HTTP[S] url valid according to {@link android.webkit.URLUtil#isNetworkUrl}
     * @return {@code true} iff the request was made successfully.
     * @throws IllegalArgumentException when {@code urlString} is not valid.
     */
    @CrossUser
    public boolean makeHttpRequest(String urlString) {
        HttpURLConnection urlConnection = null;
        final URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid url", e);
        }
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
            urlConnection.getResponseCode();
            return true;
        } catch (IOException e) {
            Log.i(TAG, "Failed to make HTTP request", e);
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
