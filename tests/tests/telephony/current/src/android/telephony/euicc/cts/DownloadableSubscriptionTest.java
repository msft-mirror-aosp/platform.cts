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

package android.telephony.euicc.cts;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.carrier.CarrierIdentifier;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.DownloadableSubscription;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DownloadableSubscriptionTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String ACTIVATION_CODE =
            "1$SMDP.GSMA.COM$04386-AGYFT-A74Y8-3F815$1.3.6.1.4.1.31746";

    private static final CarrierIdentifier CARRIER_IDENTIFIER =
            new CarrierIdentifier(
                    "123" /*MCC*/, "456" /*MNC*/,
                    "Android" /*SPN*/, "8675309" /*IMSI*/,
                    "111" /*GID1*/, "222" /*GID2*/);

    private static final String CONFIRMATION_CODE = "fake confirmation code";

    private DownloadableSubscription mDownloadableSubscription;

    private static final String CARRIER_NAME = "Test carrier Name";

    // Note: a null list is converted to an empty list during parceling.
    // Accordingly, an empty list is used here for cleanliness.
    private static final List<UiccAccessRule> EMPTY_ACCESS_RULES = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        DownloadableSubscription.Builder dsb =
                new DownloadableSubscription.Builder(ACTIVATION_CODE);

        dsb.setConfirmationCode(CONFIRMATION_CODE);
        dsb.setAccessRules(EMPTY_ACCESS_RULES);
        dsb.setCarrierName(CARRIER_NAME);

        if (Flags.downloadableSubscriptionIncludeCarrierIdentifierInternal()) {
            dsb.setCarrierIdentifier(CARRIER_IDENTIFIER);
        }
        mDownloadableSubscription = dsb.build();
    }

    @Test
    public void testDownloadableSubscriptionBuilder() {
        assertEqualsDefaults(mDownloadableSubscription);
    }

    @Test
    public void testDescribeContents() {
        int bitmask = mDownloadableSubscription.describeContents();
        assertTrue(bitmask == 0 || bitmask == Parcelable.CONTENTS_FILE_DESCRIPTOR);
    }

    @Test
    public void testWriteEmptyParcel() {
        DownloadableSubscription emptySubscription = new DownloadableSubscription.Builder().build();

        assertNull(emptySubscription.getEncodedActivationCode());
        assertNull(emptySubscription.getConfirmationCode());
        assertNull(emptySubscription.getCarrierName());

        if (Flags.downloadableSubscriptionIncludeCarrierIdentifierInternal()) {
            assertNull(emptySubscription.getCarrierIdentifier());
        }

        Parcel parcel = Parcel.obtain();
        emptySubscription.writeToParcel(parcel, emptySubscription.describeContents());

        // extract object from parcel
        parcel.setDataPosition(0 /* pos */);
        DownloadableSubscription downloadableSubscriptionFromParcel =
                DownloadableSubscription.CREATOR.createFromParcel(parcel);

        assertNull(downloadableSubscriptionFromParcel.getEncodedActivationCode());
        assertNull(downloadableSubscriptionFromParcel.getConfirmationCode());
        assertNull(downloadableSubscriptionFromParcel.getCarrierName());

        // Yes this is unfortunate that the behavior could be null or empty. It's not
        // specified, but because the values are nullable, it can return either.
        assertTrue(
                downloadableSubscriptionFromParcel.getAccessRules() == null
                        || downloadableSubscriptionFromParcel.getAccessRules().isEmpty());

        if (Flags.downloadableSubscriptionIncludeCarrierIdentifierInternal()) {
            assertNull(downloadableSubscriptionFromParcel.getCarrierIdentifier());
        }
    }

    @Test
    public void testWriteToParcel() {
        // write object to parcel
        Parcel parcel = Parcel.obtain();
        mDownloadableSubscription.writeToParcel(
                parcel, mDownloadableSubscription.describeContents());

        // extract object from parcel
        parcel.setDataPosition(0 /* pos */);
        DownloadableSubscription downloadableSubscriptionFromParcel =
                DownloadableSubscription.CREATOR.createFromParcel(parcel);

        assertEqualsDefaults(downloadableSubscriptionFromParcel);
    }

    @Test
    public void testWriteToParcelManually() {
        assumeTrue(Flags.downloadableSubscriptionIncludeCarrierIdentifierInternal());
        // write object to parcel
        Parcel parcel = Parcel.obtain();
        parcel.writeString(ACTIVATION_CODE);
        parcel.writeString(CONFIRMATION_CODE);
        parcel.writeString(CARRIER_NAME);
        parcel.writeTypedList(EMPTY_ACCESS_RULES);
        parcel.writeParcelable(CARRIER_IDENTIFIER, CARRIER_IDENTIFIER.describeContents());

        // extract object from parcel
        parcel.setDataPosition(0 /* pos */);
        DownloadableSubscription downloadableSubscriptionFromParcel =
                DownloadableSubscription.CREATOR.createFromParcel(parcel);
        assertEqualsDefaults(downloadableSubscriptionFromParcel);
    }

    void assertEqualsDefaults(DownloadableSubscription ds) {
        assertEquals(ACTIVATION_CODE, ds.getEncodedActivationCode());
        assertEquals(CONFIRMATION_CODE, ds.getConfirmationCode());
        assertEquals(CARRIER_NAME, ds.getCarrierName());
        assertEquals(EMPTY_ACCESS_RULES, ds.getAccessRules());

        if (Flags.downloadableSubscriptionIncludeCarrierIdentifierInternal()) {
            assertEquals(CARRIER_IDENTIFIER, ds.getCarrierIdentifier());
        }
    }
}
