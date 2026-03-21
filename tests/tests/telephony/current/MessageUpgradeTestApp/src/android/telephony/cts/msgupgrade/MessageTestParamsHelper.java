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
import android.telephony.cts.MessageUpgradeUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class MessageTestParamsHelper {
    private static final String TAG = MessageTestParamsHelper.class.getSimpleName();
    public static final String TEST_UPGRADE_PREFIX = "TEST_UPGRADE:";

    private static final String KEY_DELAY = "delay";
    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE_STATE = "messageState";

    private static final long DEFAULT_DELAY_MS = 1000;
    private static final int DEFAULT_STATUS = UPGRADE_STATUS_ACCEPTED;
    private static final MessageUpgradeUtils.MessageState DEFAULT_MESSAGE_STATE =
            MessageUpgradeUtils.MessageState.SENT_AND_DELIVERED;

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
     * @return An {@link UpgradeParams} instance with parsed values if the prefix is found and
     *     parameters are valid. Otherwise, returns null.
     */
    public UpgradeParams getUpgradeParamsIfAvailable(Uri contentUri) {
        MessageType type = getMessageType(contentUri);
        String controlString = getStringForControl(contentUri, type);

        if (controlString != null && controlString.startsWith(TEST_UPGRADE_PREFIX)) {
            return parseParameters(controlString.substring(TEST_UPGRADE_PREFIX.length()), type);
        }

        return null;
    }

    private MessageType getMessageType(Uri uri) {
        if (uri == null || uri.getAuthority() == null) return null;
        return switch (uri.getAuthority()) {
            case "sms" -> MessageType.SMS;
            case "mms" -> MessageType.MMS;
            default -> null;
        };
    }

    private UpgradeParams parseParameters(String paramsPart, MessageType type) {
        long delayMs = DEFAULT_DELAY_MS;
        int status = DEFAULT_STATUS;
        MessageUpgradeUtils.MessageState messageState = DEFAULT_MESSAGE_STATE;

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
                    case KEY_MESSAGE_STATE:
                        try {
                            messageState = MessageUpgradeUtils.MessageState.valueOf(value);
                        } catch (IllegalArgumentException e) {
                            Log.w(TAG, "Invalid format for '" + KEY_MESSAGE_STATE + "': " + value);
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

        return new UpgradeParams(delayMs, status, messageState, type);
    }

    /**
     * Retrieves the identifying string content from a message to be used for verification or
     * control flow during the upgrade process.
     *
     * <p>The string varies by message type:
     *
     * <ul>
     *   <li><b>SMS:</b> Returns the message body.
     *   <li><b>MMS:</b> Returns the message subject from the Telephony provider.
     *   <li><b>CTS_MMS:</b> Extracts the subject directly from the CTS-provided PDU.
     * </ul>
     *
     * @param contentUri The {@link Uri} of the message to query.
     * @param type The {@link MessageType} (SMS, MMS, or CTS_MMS).
     * @return The identifying string (Body or Subject), or {@code null} if the type is null or the
     *     column could not be found.
     */
    private String getStringForControl(Uri contentUri, MessageType type) {
        if (type == null) return null;

        return switch (type) {
            case SMS -> queryTelephonyStringColumn(contentUri, Telephony.Sms.BODY);
            case MMS -> queryTelephonyStringColumn(contentUri, Telephony.Mms.SUBJECT);
        };
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

    // Helper method to read InputStream to byte array
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384]; // Read in chunks
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Represents a scenario where upgrade parameters have been successfully parsed from the
     * message.
     *
     * @param delayMs The delay in milliseconds to wait before simulating the upgrade result.
     * @param status The status code to return for the simulated upgrade (e.g.,
     *     UPGRADE_STATUS_ACCEPTED, UPGRADE_STATUS_REJECTED).
     */
    public record UpgradeParams(
            long delayMs,
            int status,
            MessageUpgradeUtils.MessageState messageState,
            MessageType messageType) {}

    public enum MessageType {
        SMS,
        MMS
    }
}
