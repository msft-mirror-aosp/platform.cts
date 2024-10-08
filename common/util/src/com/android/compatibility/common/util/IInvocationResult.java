/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.util;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for a the result of a single Compatibility invocation.
 */
public interface IInvocationResult {

    /**
     * @return the starting timestamp.
     */
    long getStartTime();

    /**
     * @param time the starting timestamp
     */
    void setStartTime(long time);

    /**
     * Count the number of results with given status.
     */
    int countResults(TestStatus result);

    /**
     * @param plan the plan associated with this result.
     */
    void setTestPlan(String plan);

    /**
     * @return the test plan associated with this result.
     */
    String getTestPlan();

    /**
     * Adds the given device serial to the result.
     */
    void addDeviceSerial(String serial);

    /**
     * @return the device serials associated with result.
     */
    Set<String> getDeviceSerials();

    /**
     * @return the {@link IModuleResult} for the given id, creating one if it doesn't exist
     */
    IModuleResult getOrCreateModule(String id);

    /**
     * @return the {@link IModuleResult}s sorted by id.
     */
    List<IModuleResult> getModules();

    /**
     * @return the directory containing this result.
     */
    File getResultDir();

    /**
     * Adds the given build info to the result.
     */
    void addBuildInfo(String key, String value);

    /**
     * Gets the {@link Map} of build info collected.
     */
    Map<String, String> getBuildInfo();
}
