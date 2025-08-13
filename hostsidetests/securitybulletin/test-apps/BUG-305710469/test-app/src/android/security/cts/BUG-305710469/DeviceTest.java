/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts.bug_305710469_test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final String TAG = DeviceTest.class.getSimpleName();

    private static final long TIMEOUT_MS = 20000;

    private static final String PROVIDER_AUTHORITY = "android.security.cts.bug_305710469_provider";
    private static final Uri PROVIDER_AUTHORITY_URI = Uri.parse("content://" + PROVIDER_AUTHORITY);

    private Uri mTargetAuthorityUri;
    public static Uri mTargetFileUri;

    @Before
    public void setUp() {
        // Get the id of a test user created by host side test
        Bundle args = InstrumentationRegistry.getArguments();
        int targetUser = Integer.parseInt(args.getString("target_user", "-1"));
        assumeTrue("Could not find target user", targetUser != -1);

        mTargetAuthorityUri = withUserId(PROVIDER_AUTHORITY_URI, targetUser);
        mTargetFileUri = withPath(mTargetAuthorityUri, "crossuser.txt");
    }


    /** Device test */
    @Test
    public void testDeviceSideMethod() throws Exception {


        Instrumentation instrumentation = getInstrumentation();
        UiDevice device = UiDevice.getInstance(instrumentation);
        Context context = instrumentation.getTargetContext();

        AtomicReference<String> actual = new AtomicReference<>();
        actual.set("secure");

        final Semaphore resultReturn = new Semaphore(0);

        new Thread(()-> {
            int port = 1337;
            try (ServerSocket serverSocket = new ServerSocket(1337)) {
                Log.d(TAG, "HTTP Server listening on port " + port);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    try (InputStream inputStream = clientSocket.getInputStream();
                            InputStreamReader inputStreamReader =
                                new InputStreamReader(inputStream);
                            BufferedReader in = new BufferedReader(inputStreamReader)) {

                        // code to read headers
                        String line;
                        while ((line = in.readLine()) != null) {
                            if (line.isEmpty()) {
                                break;
                            }
                        }
                        // code to read the post payload data
                        StringBuilder requestBody = new StringBuilder();
                        while (in.ready()) {
                            requestBody.append((char) in.read());
                        }

                        String body = requestBody.toString();
                        // Respond to the client
                        String response = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/html\r\n"
                                + "\r\n"
                                + "<html><body><h1>Hello, World!</h1></body></html>";
                        clientSocket.getOutputStream().write(response.getBytes());

                        if (body.contains("cross_user")) {
                            actual.set("vulnerable");
                        }
                        resultReturn.release();

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        SmsManager smsManager = context.getSystemService(SmsManager.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 123,
                new Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_MUTABLE);

        smsManager.sendMultimediaMessage(context, mTargetFileUri, "http://127.0.0.1:1337", null, pendingIntent);
        Log.d(TAG, mTargetFileUri.toString());

        resultReturn.tryAcquire(/* TIMEOUT_MS */ TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertEquals("Failed. Cross user URI reads detected", "secure", actual.get());



    }



    private static Uri withUserId(Uri uri, int userId) {
        Uri.Builder builder = uri.buildUpon();
        builder.encodedAuthority("" + userId + "@" + uri.getEncodedAuthority());
        return builder.build();
    }

    private static Uri withPath(Uri uri, String path) {
        return uri.buildUpon().appendPath(path).build();
    }

}
