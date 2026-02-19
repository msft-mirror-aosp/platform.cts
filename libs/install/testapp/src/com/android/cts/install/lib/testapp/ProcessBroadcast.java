/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.install.lib.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * A broadcast receiver to handle intents sent by tests.
 *
 * PROCESS_USER_DATA: check for and update user app data version and user handle compatibility.
 * GET_USER_DATA_VERSION: return user app data version
 * REQUEST_AUDIO_FOCUS: request audio focus
 * ABANDON_AUDIO_FOCUS: abandon audio focus
 */
public class ProcessBroadcast extends BroadcastReceiver {

    private static final String USERDATA_TXT = "userdata.txt";
    private static final String USERDATA_LINK = "userdata.link";
    private static final String XATTR_NAME = "user.test";

    /**
     * Exception thrown in case of issue with user data.
     */
    public static class UserDataException extends Exception {
        public UserDataException(String message) {
            super(message);
        }

        public UserDataException(String message, Throwable cause) {
           super(message, cause);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if ("PROCESS_USER_DATA".equals(action)) {
            try {
                processUserData(context);
                setResultCode(1);
            } catch (UserDataException e) {
                setResultCode(0);
                setResultData(e.getMessage());
            }
        } else if ("GET_USER_DATA_VERSION".equals(action)) {
            try {
                setResultCode(getUserDataVersion(context));
            } catch (FileNotFoundException e) {
                setResultCode(-1); // Matches the host-side InstallUtils#getUserDataVersion()
            } catch (UserDataException e) {
                // Inconsistency detected.
                setResultCode(-3); // Matches the host-side InstallUtils#getUserDataVersion()
                setResultData(e.getMessage());
            }
        } else if ("REQUEST_AUDIO_FOCUS".equals(action)) {
            requestAudioFocus(context);
        } else if ("ABANDON_AUDIO_FOCUS".equals(action)) {
            abandonAudioFocus(context);
        }
    }

    private void requestAudioFocus(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        final AudioFocusRequest afr =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
        audioManager.requestAudioFocus(afr);
        setResultCode(0);
    }

    private void abandonAudioFocus(Context context) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        final AudioFocusRequest afr =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build();
        audioManager.abandonAudioFocusRequest(afr);
        setResultCode(0);
    }

    /**
     * Update the app's user data version to match the app version, and confirm
     * the user data is for the correct user.
     *
     * @param context The application context.
     * @throws UserDataException in case of problems with app user data.
     */
    public void processUserData(Context context) throws UserDataException {
        Resources res = context.getResources();
        String packageName = context.getPackageName();

        String userHandle = Process.myUserHandle().toString();

        int appVersionId = res.getIdentifier("app_version", "integer", packageName);
        int appVersion = res.getInteger(appVersionId);

        int splitVersionId = res.getIdentifier("split_version", "integer", packageName);
        int splitVersion = res.getInteger(splitVersionId);

        // Make sure the app version and split versions are compatible.
        if (appVersion != splitVersion) {
            throw new UserDataException("Split version " + splitVersion
                    + " does not match app version " + appVersion);
        }

        // Read the version of the app's user data and ensure it is compatible
        // with our version of the application. Also ensure that the user data is
        // for the correct user.
        File userDataTxtFile = new File(context.getFilesDir(), USERDATA_TXT);
        int userDataVersion = -1;
        try (Scanner s = new Scanner(userDataTxtFile)) {
            userDataVersion = s.nextInt();
            s.nextLine();

            if (userDataVersion > appVersion) {
                throw new UserDataException("User data is from version " + userDataVersion
                        + ", which is not compatible with this version " + appVersion
                        + " of the RollbackTestApp");
            }

            String readUserHandle = s.nextLine();

            if (!readUserHandle.equals(userHandle)) {
                throw new UserDataException("User handle expected to be: " + userHandle
                        + ", but was actually " + readUserHandle);
            }
        } catch (FileNotFoundException e) {
            // No problem. This is a fresh install of the app or the user data has been wiped.
        }

        if (userDataVersion != -1) {
            verifyAdditionalCopiesOfVersion(context, userDataVersion);
        }

        // Record the current version of the app in a regular file.
        try (PrintWriter pw = new PrintWriter(userDataTxtFile)) {
            pw.println(appVersion);
            pw.println(userHandle);
        } catch (IOException e) {
            throw new UserDataException("Unable to write user data.", e);
        }
        // Also store a copy in an xattr, so that it's tested that xattrs are preserved.
        try {
            Os.setxattr(
                    userDataTxtFile.getAbsolutePath(),
                    XATTR_NAME,
                    Integer.toString(appVersion).getBytes(StandardCharsets.UTF_8),
                    0);
        } catch (ErrnoException e) {
            throw new UserDataException("Unable to set xattr.", e);
        }
        // Also store a copy in a symlink, so that it's tested that symlinks are preserved.
        File userDataLinkFile = new File(context.getFilesDir(), USERDATA_LINK);
        try {
            userDataLinkFile.delete();
            Os.symlink(Integer.toString(appVersion), userDataLinkFile.getPath());
        } catch (ErrnoException e) {
            throw new UserDataException("Unable to create symlink.", e);
        }
    }

    /**
     * Gets the app's user data version.
     *
     * @param context The application context.
     * @return the user data version
     * @throws FileNotFoundException if userdata.txt doesn't exist
     * @throws UserDataException if a data inconsistency is detected
     */
    private int getUserDataVersion(Context context)
            throws FileNotFoundException, UserDataException {
        File file = new File(context.getFilesDir(), USERDATA_TXT);
        final int userDataVersion;
        try (Scanner s = new Scanner(file)) {
            userDataVersion = s.nextInt();
        }
        verifyAdditionalCopiesOfVersion(context, userDataVersion);
        return userDataVersion;
    }

    private void verifyAdditionalCopiesOfVersion(Context context, int userDataVersion)
            throws UserDataException {
        verifyXattrVersion(context, userDataVersion);
        verifySymlinkVersion(context, userDataVersion);
    }

    private void verifyXattrVersion(Context context, int userDataVersion) throws UserDataException {
        File file = new File(context.getFilesDir(), USERDATA_TXT);
        final int xattrVersion;
        try {
            xattrVersion =
                    Integer.valueOf(new String(Os.getxattr(file.getAbsolutePath(), XATTR_NAME)));
        } catch (ErrnoException e) {
            throw new UserDataException("Error while retrieving xattr.", e);
        }
        if (xattrVersion != userDataVersion) {
            throw new UserDataException(
                    "xattr version is "
                            + xattrVersion
                            + " which does not match version "
                            + userDataVersion
                            + " from contents of "
                            + USERDATA_TXT);
        }
    }

    private void verifySymlinkVersion(Context context, int userDataVersion)
            throws UserDataException {
        File file = new File(context.getFilesDir(), USERDATA_LINK);
        final int symlinkVersion;
        try {
            symlinkVersion = Integer.valueOf(Os.readlink(file.getPath()));
        } catch (ErrnoException e) {
            throw new UserDataException("Error while reading symlink.", e);
        }
        if (symlinkVersion != userDataVersion) {
            throw new UserDataException(
                    "symlink version is "
                            + symlinkVersion
                            + " which does not match version "
                            + userDataVersion
                            + " from contents of "
                            + USERDATA_TXT);
        }
    }
}
