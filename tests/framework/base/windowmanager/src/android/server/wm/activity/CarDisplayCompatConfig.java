/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.server.wm.activity;

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Display;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;

/**
 * Class for accessing car display compat package overrides.
 *
 * The methods of class are not thread safe.
 */
final class CarDisplayCompatConfig {

    private static final String TAG = CarDisplayCompatConfig.class.getSimpleName();
    private static final String ENCODING = "UTF-8";
    // Config file doesn't expect any namespace before xml elements.
    private static final String NAMESPACE = null;
    private static final String CONFIG = "config";
    private static final String SCALE = "scale";
    private static final String DISPLAY = "display";
    private static final String USER = "userId";
    private static final String PACKAGE = "packageName";
    private static final String ANY_PACKAGE = "*";
    private static final float DEFAULT_SCALE = 1f;

    /** see {@code PackageManager#FEATURE_CAR_DISPLAY_COMPATIBILITY} */
    static final String FEATURE_CAR_DISPLAY_COMPATIBILITY =
            "android.software.car.display_compatibility";

    /**
     * Maps a combination of package name, user id, display id to a scale factor.
     * display id is required.
     * * means all packages
     * -1 (UserHandle.ALL) means all the users
     *
     * ex: com.android@10@0
     * ex: *@10@0
     * ex: com.android@-1@0
     * ex: *@-1@0
     */
    private final ArrayMap<Key, Float> mPackageUserDisplayScaleFactorMap = new ArrayMap<>();

    /**
     * Returns the display compatibility dpi scaling factor for the given context.
     *
     * @param context The context to retrieve the scaling factor for
     * @return The dpi scaling factor
     */
    static float getAutomotiveDisplayCompatScalingFactor(Context context) {
        String secureKey = FEATURE_CAR_DISPLAY_COMPATIBILITY + ":settings:secure";
        String configString = Settings.Secure.getString(
                context.getContentResolver(), secureKey);
        if (configString == null) {
            return DEFAULT_SCALE;
        }
        try (InputStream in = new ByteArrayInputStream(configString.getBytes())) {
            CarDisplayCompatConfig displayCompatConfig = new CarDisplayCompatConfig();
            displayCompatConfig.populate(in);

            CarDisplayCompatConfig.Key key = new CarDisplayCompatConfig.Key(
                    Display.DEFAULT_DISPLAY,
                    ANY_PACKAGE,
                    UserHandle.ALL
            );
            return displayCompatConfig.getScaleFactor(key, DEFAULT_SCALE);
        } catch (XmlPullParserException | IOException | SecurityException e) {
            Log.e(TAG, "Error reading automotive display compat config " + e);
        }

        return DEFAULT_SCALE;
    }

    /**
     * Set a new scaling rule.
     */
    private void setScaleFactor(Key key, float value) {
        mPackageUserDisplayScaleFactorMap.put(key, value);
    }

    /**
     * {@link ScaleFactor} when all values are set.
     *
     * Because this class is not thread safe, we're accepting the key as a parameter so that
     * the class that's calling this method can make sure they key is created in a
     * thread safe manner.
     */
    private float getScaleFactor(Key key, float defaultValue) {
        return mPackageUserDisplayScaleFactorMap.getOrDefault(key, defaultValue);
    }

    /**
     * Populate the internal data from the given {@link InputStream}
     */
    private void populate(InputStream inputStream) throws XmlPullParserException,
            IOException {
        mPackageUserDisplayScaleFactorMap.clear();
        XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = parserFactory.newPullParser();
        // Config file doesn't expect any namespace before xml elements.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(inputStream, ENCODING);
        parser.nextTag();
        readConfig(parser);
    }

    private void readConfig(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, CONFIG);
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (SCALE.equals(name)) {
                readScale(parser);
            } else {
                skipTag(parser);
            }
        }
    }

    private void readScale(XmlPullParser parser) throws XmlPullParserException,
            IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, SCALE);

        int display = DEFAULT_DISPLAY;
        try {
            display = Integer.parseInt(parser.getAttributeValue(NAMESPACE, DISPLAY));
        } catch (NullPointerException | NumberFormatException e) {
            Log.e(TAG, "parse failed: " + DISPLAY + " = " +
                    parser.getAttributeValue(NAMESPACE, DISPLAY));
        }

        String packageName = parser.getAttributeValue(NAMESPACE, PACKAGE);
        packageName = (packageName == null) ? ANY_PACKAGE : packageName;

        int userId = UserHandle.ALL.getIdentifier();
        try {
            userId = Integer.parseInt(parser.getAttributeValue(NAMESPACE, USER));
        } catch (NullPointerException | NumberFormatException e) {
            Log.e(TAG, "parse failed: " + USER + " = " + parser.getAttributeValue(NAMESPACE, USER));
        }

        float value = DEFAULT_SCALE;
        if (parser.next() == XmlPullParser.TEXT) {
            try {
                value = Float.parseFloat(parser.getText());
            } catch (NullPointerException | NumberFormatException e) {
                Log.e(TAG, "parse failed: TEXT = " + parser.getText());
            }
            parser.nextTag();
        }
        parser.require(XmlPullParser.END_TAG, NAMESPACE, SCALE);

        setScaleFactor(new Key(display, packageName, UserHandle.of(userId)), value);
    }

    /**
     * Skips to the next tag.
     */
    private void skipTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    Log.i(TAG, "skipped TAG " + parser.getName());
                    depth++;
                    break;
            }
        }
    }

    private static class Key {
        int displayId;
        String packageName;
        int userId;

        Key(int displayId, String packageName, UserHandle user) {
            this.displayId = displayId;
            this.packageName = packageName;
            this.userId = user.getIdentifier();
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "%d@%s@%d", displayId, packageName, userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(displayId, packageName, userId);
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Key)) return false;
            if (((Key) other).displayId != displayId) return false;
            if (!((Key) other).packageName.equals(packageName)) return false;
            if (((Key) other).userId != userId) return false;
            return true;
        }
    }
}
