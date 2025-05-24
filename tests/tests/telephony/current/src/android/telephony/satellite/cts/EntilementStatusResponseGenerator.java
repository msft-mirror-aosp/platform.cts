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

package android.telephony.satellite.cts;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class EntilementStatusResponseGenerator {
    private static final String TAG = "EntilementStatusResponseGenerator";

    public static final String MOCK_ENTITLEMENT_SERVER_URL = "http://127.0.0.1:8080";

    /** SatMode allowed, but not yet provisioned and activated on the network. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_DISABLED = 0;
    /** SatMode service allowed, provisioned and activated on the network. User can access the
     * satellite service. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_ENABLED = 1;
    /** SatMode cannot be offered for network or device. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_INCOMPATIBLE = 2;
    /** SatMode is being provisioned on the network. Not yet activated. */
    public static final int SATELLITE_ENTITLEMENT_STATUS_PROVISIONING = 3;

    private int mEntitlementStatus = SATELLITE_ENTITLEMENT_STATUS_DISABLED;
    @Nullable private List<SatelliteNetworkInfo> mSupportedPlmnsAndServices;
    @Nullable private List<String> mBarredPlmns;

    /**
     * Set the entitlement status.
     *
     * @param entitlementStatus The entitlement status.
     */
    public void setEntitlementStatus(int entitlementStatus) {
        mEntitlementStatus = entitlementStatus;
    }

    /**
     * Set the supported plmns and services.
     *
     * @param supportedPlmnsAndServices The supported plmns and services.
     */
    public void setSupportedPlmnsAndServices(
        @Nullable List<SatelliteNetworkInfo> supportedPlmnsAndServices) {
        mSupportedPlmnsAndServices = supportedPlmnsAndServices;
    }

    public List<String> getAllowedPlmns() {
        List<String> allowedPlmns = new ArrayList<>();
        if (mSupportedPlmnsAndServices == null) return allowedPlmns;
        for (SatelliteNetworkInfo satelliteNetworkInfo : mSupportedPlmnsAndServices) {
            allowedPlmns.add(satelliteNetworkInfo.plmn);
        }
        return allowedPlmns;
    }

    public List<String> getBarredPlmns() {
        if (mBarredPlmns == null) return new ArrayList<>();
        return mBarredPlmns;
    }

    /**
     * Set the barred plmns.
     *
     * @param barredPlmns The barred plmns.
     */
    public void setBarredPlmns(@Nullable List<String> barredPlmns) {
        mBarredPlmns = barredPlmns;
    }

    public static List<SatelliteNetworkInfo> createDefaultValidSatelliteNetworkInfoList() {
        List<SatelliteNetworkInfo> satelliteNetworkInfoList = new ArrayList<>();
        satelliteNetworkInfoList.add(new SatelliteNetworkInfo("40445",
                SatelliteNetworkInfo.DATA_PLAN_TYPE_METERED,
                new HashMap<>(ImmutableMap.of(SatelliteNetworkInfo.SERVICE_TYPE_DATA,
                        SatelliteNetworkInfo.SERVICE_POLICY_UNCONSTRAINED))));
        satelliteNetworkInfoList.add(new SatelliteNetworkInfo("40446",
                SatelliteNetworkInfo.DATA_PLAN_TYPE_UNMETERED,
                new HashMap<>(ImmutableMap.of(SatelliteNetworkInfo.SERVICE_TYPE_VOICE,
                        SatelliteNetworkInfo.SERVICE_POLICY_CONSTRAINED))));
        satelliteNetworkInfoList.add(new SatelliteNetworkInfo("40447",
                SatelliteNetworkInfo.DATA_PLAN_TYPE_METERED,
                new HashMap<>(
                    ImmutableMap.of(
                      SatelliteNetworkInfo.SERVICE_TYPE_DATA,
                      SatelliteNetworkInfo.SERVICE_POLICY_CONSTRAINED,
                      SatelliteNetworkInfo.SERVICE_TYPE_VOICE,
                      SatelliteNetworkInfo.SERVICE_POLICY_UNCONSTRAINED))));
        return satelliteNetworkInfoList;
    }

    public String createTS43Response() {
        String t43Response =
            "{\"Vers\":{\"version\":\"1\",\"validity\":\"1728000\"},"
            + "\"Token\":{\"token\":\"kZYfCEpSsMr88KZVmab5UsZVzl+nWSsX\"},"
            + "\"ap2016\":{\"EntitlementStatus\":\"" + mEntitlementStatus + "\""
            + createSupportedPlmnsAndServicesResponseStr()
            + createBarredPlmnsResponseStr() + "},"
            + "\"eap-relay-packet\":\"EapAkaChallengeRequest\""
            + "}";
        logd("t43Response: " + t43Response);
        return t43Response;
    }

    private String createSupportedPlmnsAndServicesResponseStr() {
        if (mEntitlementStatus != SATELLITE_ENTITLEMENT_STATUS_ENABLED) return "";
        if (mSupportedPlmnsAndServices == null) return "";

        StringBuilder supportedPlmnsAndServicesStrBuilder = new StringBuilder();
        supportedPlmnsAndServicesStrBuilder.append(",\"PLMNAllowed\":[");
        int i = 0;
        for (SatelliteNetworkInfo satelliteNetworkInfo : mSupportedPlmnsAndServices) {
            supportedPlmnsAndServicesStrBuilder.append("{\"PLMN\":\"");
            supportedPlmnsAndServicesStrBuilder.append(satelliteNetworkInfo.plmn);
            supportedPlmnsAndServicesStrBuilder.append("\",");
            supportedPlmnsAndServicesStrBuilder.append("\"DataPlanType\":\"");
            supportedPlmnsAndServicesStrBuilder.append(satelliteNetworkInfo.dataPlanType);
            if (satelliteNetworkInfo.allowedServicesInfo != null) {
                supportedPlmnsAndServicesStrBuilder.append("\",");
                supportedPlmnsAndServicesStrBuilder.append("\"AllowedServicesInfo\":[");
                int j = 0;
                for (String key : satelliteNetworkInfo.allowedServicesInfo.keySet()) {
                    supportedPlmnsAndServicesStrBuilder.append("{\"AllowedServices\":");
                    supportedPlmnsAndServicesStrBuilder.append("{\"ServiceType\":\"");
                    supportedPlmnsAndServicesStrBuilder.append(key);
                    supportedPlmnsAndServicesStrBuilder.append("\",");
                    supportedPlmnsAndServicesStrBuilder.append("\"ServicePolicy\":\"");
                    supportedPlmnsAndServicesStrBuilder.append(
                            satelliteNetworkInfo.allowedServicesInfo.get(key));
                    supportedPlmnsAndServicesStrBuilder.append("\"}}");
                    if (j < satelliteNetworkInfo.allowedServicesInfo.size() - 1) {
                        supportedPlmnsAndServicesStrBuilder.append(",");
                    }
                    j++;
                }
                supportedPlmnsAndServicesStrBuilder.append("]");
            }
            supportedPlmnsAndServicesStrBuilder.append("}");
            if (i < mSupportedPlmnsAndServices.size() - 1) {
                supportedPlmnsAndServicesStrBuilder.append(",");
            }
            i++;
        }
        supportedPlmnsAndServicesStrBuilder.append("]");
        logd("supportedPlmnsAndServicesStr: "
                + supportedPlmnsAndServicesStrBuilder.toString());
        return supportedPlmnsAndServicesStrBuilder.toString();
    }

    private String createBarredPlmnsResponseStr() {
        if (mEntitlementStatus != SATELLITE_ENTITLEMENT_STATUS_ENABLED) return "";
        if (mBarredPlmns == null) return "";

        StringBuilder barredPlmnsStrBuilder = new StringBuilder();
        barredPlmnsStrBuilder.append(",\"PLMNBarred\":[");
        int i = 0;
        for (String plmn : mBarredPlmns) {
            barredPlmnsStrBuilder.append("{\"PLMN\":\"");
            barredPlmnsStrBuilder.append(plmn);
            barredPlmnsStrBuilder.append("\"}");
            if (i < mBarredPlmns.size() - 1) {
                barredPlmnsStrBuilder.append(",");
            }
            i++;
        }
        barredPlmnsStrBuilder.append("]");
        logd("barredPlmnsStr: " + barredPlmnsStrBuilder.toString());
        return barredPlmnsStrBuilder.toString();
    }

    private static void logd(@NonNull String log) {
        Log.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Log.e(TAG, log);
    }

    /**
     * Data class of the satellite configuration received from the entitlement server.
     */
    public static class SatelliteNetworkInfo {
      public static final String DATA_PLAN_TYPE_UNMETERED = "unmetered";
      public static final String DATA_PLAN_TYPE_METERED = "metered";
      public static final String DATA_PLAN_TYPE_EMPTY = "";
      public static final String SERVICE_TYPE_DATA = "data";
      public static final String SERVICE_TYPE_VOICE = "voice";
      public static final String SERVICE_POLICY_UNCONSTRAINED = "unconstrained";
      public static final String SERVICE_POLICY_CONSTRAINED = "constrained";

      /** Stored the allowed plmn for using the satellite service. */
      public String plmn;
      /** Stored the DataPlanType. It is an optional value that can be one of the following three
       * values.
       * 1. "unmetered"
       * 2. "metered"
       * 3. empty string. */
      public String dataPlanType;
      /** Stored the Allowed Services Info. with key as service type and value as service
       *  policy for the plmn
       *  Possible Service Type values: "data" and "voice".
       *  Possible Service Policy values: "constrained" and "unconstrained".
       */
      public Map<String, String> allowedServicesInfo;

      public SatelliteNetworkInfo(String plmn, String dataPlanType,
              Map<String, String> allowedServicesInfo) {
          this.plmn = plmn;
          this.dataPlanType = dataPlanType;
          this.allowedServicesInfo = allowedServicesInfo != null
                  ? new HashMap<>(allowedServicesInfo) : null;
      }
    }
}