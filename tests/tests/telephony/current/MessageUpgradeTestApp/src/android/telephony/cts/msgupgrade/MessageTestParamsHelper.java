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

package android.telephony.cts.msgupgrade;

import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_ACCEPTED;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

public final class MessageTestParamsHelper {
    private static final String TAG = MessageTestParamsHelper.class.getSimpleName();
    public static final String TEST_UPGRADE_PREFIX = "TEST_UPGRADE:";

    private static final String KEY_DELAY = "delay";
    private static final String KEY_STATUS = "status";

    private static final long DEFAULT_DELAY_MS = 1000;
    private static final int DEFAULT_STATUS = UPGRADE_STATUS_ACCEPTED;

    private final ContentResolver mContentResolver;

    public MessageTestParamsHelper(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    /**
     * Parses the message content from the given URI to extract upgrade parameters if they are
     * encoded in the SMS body or MMS subject.
     *
     * <p>Looks for a string starting with {@code TEST_UPGRADE_PREFIX}. If found, it parses
     * key-value pairs for parameters like 'delay' and 'status'.
     *
     * @param contentUri The content URI of the SMS or MMS message.
     * @return An {@link UpgradeReady} instance with parsed values if the prefix is found and
     *     parameters are valid. Otherwise, returns a {@link NoUpgrade} instance with default
     *     values.
     */
    public UpgradeParams getUpgradeParamsIfAvailable(Uri contentUri) {
        String controlString = getStringForControl(contentUri);
        if (controlString != null && controlString.startsWith(TEST_UPGRADE_PREFIX)) {
            return parseParameters(controlString.substring(TEST_UPGRADE_PREFIX.length()));
        } else {
            return new NoUpgrade(DEFAULT_DELAY_MS);
        }
    }

    private UpgradeParams parseParameters(String paramsPart) {
        long delayMs = DEFAULT_DELAY_MS;
        int status = DEFAULT_STATUS;

        String[] params = paramsPart.split(";");
        for (String param : params) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                switch (key) {
                    case KEY_DELAY:
                        try {
                            delayMs = Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid format for '" + KEY_DELAY + "': " + value);
                        }
                        break;
                    case KEY_STATUS:
                        try {
                            status = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid format for '" + KEY_STATUS + "': " + value);
                        }
                        break;
                    default:
                        Log.d(TAG, "Unknown parameter key: " + key);
                        break;
                }
            } else {
                Log.w(TAG, "Malformed parameter: " + param);
            }
        }

        return new UpgradeReady(delayMs, status);
    }

    private String getStringForControl(Uri contentUri) {
        if (contentUri == null) return null;
        String authority = contentUri.getAuthority();
        if (authority == null) return null;

        return switch (authority) {
            case "sms" -> getSmsBody(contentUri);
            case "mms" -> getMmsSubject(contentUri);
            default -> {
                Log.w(TAG, "getStringForRejectionCheck: Unknown URI authority for " + contentUri);
                yield null;
            }
        };
    }

    private String getSmsBody(Uri smsUri) {
        if (smsUri == null || !"sms".equals(smsUri.getAuthority())) {
            Log.w(TAG, "getSmsBody: Invalid SMS URI: " + smsUri);
            return null;
        }
        return queryTelephonyStringColumn(smsUri, Telephony.Sms.BODY);
    }

    private String getMmsSubject(Uri mmsUri) {
        if (mmsUri == null || !"mms".equals(mmsUri.getAuthority())) {
            Log.w(TAG, "getMmsSubject: Invalid MMS URI: " + mmsUri);
            return null;
        }
        return queryTelephonyStringColumn(mmsUri, Telephony.Mms.SUBJECT);
    }

    private String queryTelephonyStringColumn(Uri uri, String columnName) {
        String[] projection = new String[] {columnName};
        String value = null;

        if (mContentResolver == null) {
            Log.w(TAG, "queryTelephonyStringColumn: ContentResolver is null");
            return null;
        }
        if (uri == null) {
            Log.w(TAG, "queryTelephonyStringColumn: URI is null for column: " + columnName);
            return null;
        }

        try (Cursor cursor = mContentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(columnName);
                value = cursor.getString(columnIndex);
            } else {
                Log.w(
                        TAG,
                        "queryTelephonyStringColumn: Cursor empty or null for URI: "
                                + uri
                                + ", column: "
                                + columnName);
            }
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "queryTelephonyStringColumn: Failed to read column "
                            + columnName
                            + " for URI: "
                            + uri,
                    e);
        }

        Log.d(TAG, "queryTelephonyStringColumn: " + value);
        return value;
    }

    /**
     * Represents the parameters for a simulated message upgrade process. This is a sealed
     * interface, permitting only {@link NoUpgrade} and {@link UpgradeReady} as direct
     * implementations.
     */
    public sealed interface UpgradeParams permits NoUpgrade, UpgradeReady {
        /**
         * The delay in milliseconds to wait before completing the simulated upgrade action.
         *
         * @return the delay in milliseconds.
         */
        long delayMs();
    }

    /**
     * Represents a scenario where no specific upgrade parameters are found in the message, or the
     * message does not trigger the special upgrade path.
     *
     * @param delayMs A default delay value.
     */
    public record NoUpgrade(long delayMs) implements UpgradeParams {}

    /**
     * Represents a scenario where upgrade parameters have been successfully parsed from the
     * message.
     *
     * @param delayMs The delay in milliseconds to wait before simulating the upgrade result.
     * @param status The status code to return for the simulated upgrade (e.g.,
     *     UPGRADE_STATUS_ACCEPTED, UPGRADE_STATUS_REJECTED).
     */
    public record UpgradeReady(long delayMs, int status) implements UpgradeParams {}
}
