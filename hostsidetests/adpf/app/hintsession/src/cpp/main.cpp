/*
 * Copyright 2023 The Android Open Source Project
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

#include <android/native_window.h>
#include <android/performance_hint.h>
#include <assert.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <functional>
#include <map>
#include <random>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "AndroidOut.h"
#include "JNIManager.h"
#include "Renderer.h"
#include "Utility.h"

using namespace std::chrono_literals;

const constexpr auto kDrawingTimeout = 3s;
const constexpr int kSamples = 300;
const constexpr int kCalibrationSamples = 100;
bool verboseLogging = true;

Renderer *getRenderer(android_app *pApp) {
    return (pApp->userData) ? reinterpret_cast<Renderer *>(pApp->userData) : nullptr;
}

// Converts lists of numbers into strings, so they can be
// passed up to the Java code the results map.
template <typename T>
std::string serializeValues(const std::vector<T> &values) {
    std::stringstream stream;
    for (auto &&value : values) {
        stream << value;
        stream << ",";
    }
    std::string out = stream.str();
    out.pop_back(); // remove the last comma
    return out;
}

// Generalizes the loop used to draw frames so that it can be easily started and stopped
// back to back with different parameters, or after adjustments such as target time adjustments.
FrameStats drawFrames(int count, android_app *pApp, int &events, android_poll_source *&pSource,
                      std::string testName = "") {
    bool namedTest = testName.size() > 0;
    std::vector<int64_t> durations{};
    std::vector<int64_t> intervals{};

    auto drawStart = std::chrono::steady_clock::now();
    // Iter is -1 so we have a buffer frame before it starts, to eat any delay from time spent
    // between tests
    for (int iter = -1; iter < count && !pApp->destroyRequested;) {
        if (std::chrono::steady_clock::now() - drawStart > kDrawingTimeout) {
            aout << "Stops drawing on " << kDrawingTimeout.count() << "s timeout for test "
                 << (namedTest ? testName : "unnamed") << std::endl;
            break;
        }
        int retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        while (retval == ALOOPER_POLL_CALLBACK) {
            retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        }
        if (retval >= 0 && pSource) {
            pSource->process(pApp, pSource);
        }
        if (pApp->userData) {
            // Don't add metrics for buffer frames
            if (iter > -1) {
                thread_local auto lastStart = std::chrono::steady_clock::now();
                auto start = std::chrono::steady_clock::now();

                // Render a frame
                auto spinTime = getRenderer(pApp)->render();
                // For now we only report the CPU duration
                getRenderer(pApp)->reportActualWorkDuration(spinTime);
                durations.push_back(spinTime);
                intervals.push_back((start - lastStart).count());
                lastStart = start;
            }
            ++iter;
        }
    }

    if (namedTest && verboseLogging) {
        getRenderer(pApp)->addResult(testName + "_durations", serializeValues(durations));
        getRenderer(pApp)->addResult(testName + "_intervals", serializeValues(intervals));
    }

    return getRenderer(pApp)->getFrameStats(durations, intervals, testName);
}

FrameStats drawFramesWithTarget(int64_t targetDuration, int &events, android_app *pApp,
                                android_poll_source *&pSource, std::string testName = "") {
    getRenderer(pApp)->updateTargetWorkDuration(targetDuration);
    return drawFrames(kSamples, pApp, events, pSource, testName);
}

double calibrate(int64_t interval, int &events, android_app *pApp, android_poll_source *&pSource) {
    const constexpr int initialHeads = 5;
    const constexpr int maxHeads = 40;
    int heads = initialHeads;
    auto renderer = getRenderer(pApp);
    renderer->setNumHeads(heads);
    int64_t duration = 0;
    int calibrateIter = 0;
    int physicsIter = 1;

    // adjust the head count so that it generates enough base duration while the interval doesn't
    // exceed the max
    const int64_t intervalMax = interval + static_cast<std::chrono::nanoseconds>(1ms).count();

    // fix the head count then and calibrate only CPU actual work duration by adjusting the physics
    // iteration count so that the actual duration is close to the goal duration
    bool fixedHeads = false;
    const int64_t goal = interval * 0.75;
    const int64_t duration_min = goal -
            std::max(static_cast<long long>(interval / 8),
                     static_cast<std::chrono::nanoseconds>(1000us).count());
    const int64_t duration_max = goal +
            std::max(static_cast<long long>(interval / 8),
                     static_cast<std::chrono::nanoseconds>(1000us).count());

    while (calibrateIter < 10 &&
           (((duration < duration_min || duration > duration_max) && physicsIter > 0) ||
            (interval > intervalMax && heads > 1 && !fixedHeads))) {
        auto stats = drawFrames(kCalibrationSamples, pApp, events, pSource);
        duration = stats.medianWorkDuration;
        interval = stats.medianFrameInterval;
        getRenderer(pApp)->addResult("calibration_" + std::to_string(calibrateIter) + "_duration",
                                     std::to_string(duration));
        getRenderer(pApp)->addResult("calibration_" + std::to_string(calibrateIter) + "_interval",
                                     std::to_string(interval));
        getRenderer(pApp)->addResult("calibration_" + std::to_string(calibrateIter) + "_heads",
                                     std::to_string(heads));
        getRenderer(pApp)->addResult("calibration_" + std::to_string(calibrateIter) +
                                             "_physicsIter",
                                     std::to_string(physicsIter));
        if (!fixedHeads) {
            // too many heads that the workload interval is exceeding frame interval
            if ((interval > intervalMax && heads > 1)) {
                heads = static_cast<double>(intervalMax) / interval * heads;
            }
            // too few heads to generate enough drawing workload
            // we will calibrate the head count so the rendering without physics iterations can run
            // at least (goal / 8) duration
            else if (duration < goal / 8 && heads < maxHeads) {
                // at most double the head count in each iteration
                heads = std::min(2.0, (static_cast<double>(goal) / 8 / duration)) * heads;
            } else {
                fixedHeads = true;
            }
            heads = std::min(maxHeads, heads);
            heads = std::max(heads, 1);
        }
        if (fixedHeads) {
            if (duration > duration_max) {
                physicsIter = std::min(physicsIter - 1,
                                       int(static_cast<double>(goal) / duration * physicsIter));
            } else if (duration < duration_min) {
                // increase 3 iteration at most at a time
                physicsIter = std::max(physicsIter + 3,
                                       int(std::sqrt(static_cast<double>(goal) / duration) *
                                           physicsIter));
            }
        }
        getRenderer(pApp)->setNumHeads(heads);
        getRenderer(pApp)->setPhysicsIterations(physicsIter);
        calibrateIter++;
    }
    getRenderer(pApp)->addResult("goal", std::to_string(goal));
    getRenderer(pApp)->addResult("duration", std::to_string(duration));
    getRenderer(pApp)->addResult("heads_count", std::to_string(heads));
    getRenderer(pApp)->addResult("interval", std::to_string(interval));
    getRenderer(pApp)->addResult("physics_iterations", std::to_string(physicsIter));
    return duration;
}

// /*!
//  * Handles commands sent to this Android application
//  * @param pApp the app the commands are coming from
//  * @param cmd the command to handle
//  */
void handle_cmd(android_app *pApp, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            pApp->userData = new Renderer(pApp);
            break;
        case APP_CMD_TERM_WINDOW:
            // The window is being destroyed. Use this to clean up your userData to avoid leaking
            // resources.
            //
            // We have to check if userData is assigned just in case this comes in really quickly
            if (pApp->userData) {
                auto *pRenderer = getRenderer(pApp);
                Utility::setFailure("App was closed while running!", pRenderer);
            }
            break;
        default:
            break;
    }
}

void setFrameRate(android_app *pApp, float frameRate) {
    auto t = ANativeWindow_setFrameRate(pApp->window, frameRate,
                                        ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_DEFAULT);
    if (t != 0) {
        Utility::setFailure("Can't set frame rate to " + std::to_string(frameRate),
                            getRenderer(pApp));
    }
}

void android_main(struct android_app *pApp) {
    app_dummy();

    // Register an event handler for Android events
    pApp->onAppCmd = handle_cmd;

    JNIManager &manager = JNIManager::getInstance();
    manager.setApp(pApp);

    int events;
    android_poll_source *pSource = nullptr;

    // adb shell setprop debug.adpf_cts_verbose_logging true
    const prop_info *pi_verbose = __system_property_find("debug.adpf_cts_verbose_logging");
    if (pi_verbose != nullptr) {
        char value[PROP_VALUE_MAX];
        __system_property_read(pi_verbose, nullptr, value);
        if (strcmp(value, "false") == 0) {
            verboseLogging = false;
        } else {
            verboseLogging = true;
        }
    }

    // Ensure renderer is initialized
    drawFrames(1, pApp, events, pSource);

    float frameRate = 60.0f;
    // adb shell setprop debug.adpf_cts_frame_rate 60.0
    const prop_info *pi_frame_rate = __system_property_find("debug.adpf_cts_frame_rate");
    if (pi_frame_rate != nullptr) {
        char value[PROP_VALUE_MAX];
        __system_property_read(pi_frame_rate, nullptr, value);
        char *endptr; // To check for valid conversion
        float floatValue = strtof(value, &endptr);
        if (*endptr == '\0') {
            frameRate = floatValue;
        }
    }
    setFrameRate(pApp, frameRate);

    std::this_thread::sleep_for(1s);
    std::vector<pid_t> tids;
    tids.push_back(gettid());
    const float lightTargetFrameRate = 30.0f;
    const int64_t lightTarget = 33333333; // 33.3ms or 30hz
    const float heavyTargetFrameRate = 120.0f;
    const int64_t heavyTarget = 8333333; // 8.3ms or 120hz
    bool supported = getRenderer(pApp)->startHintSession(tids, lightTarget);
    if (!supported) {
        JNIManager::sendResultsToJava(getRenderer(pApp)->getResults());
        return;
    }

    // Do an initial load with the session to let CPU settle and measure frame interval
    getRenderer(pApp)->setNumHeads(1);
    auto stats = drawFrames(kCalibrationSamples, pApp, events, pSource);
    const int64_t interval = stats.medianFrameInterval;
    getRenderer(pApp)->addResult("median_frame_interval", std::to_string(interval));
    // TODO(b/344696346): skip the test if the frame interval is significantly larger than
    // 16ms? Say the device has limited performance to run this test, or its FPS capped at 30
    // calibrate a workload that's 2ms over the current interval
    double calibratedTarget = calibrate(interval, events, pApp, pSource);

    auto testNames = JNIManager::getInstance().getTestNames();
    std::set<std::string> testSet{testNames.begin(), testNames.end()};
    std::vector<std::function<void()>> tests;

    FrameStats baselineStats = drawFrames(kSamples, pApp, events, pSource, "baseline");

    double calibrationAccuracy = 1.0 -
            (abs(static_cast<double>(baselineStats.medianWorkDuration) - calibratedTarget) /
             calibratedTarget);
    getRenderer(pApp)->addResult("calibration_accuracy", std::to_string(calibrationAccuracy));

    // Used to figure out efficiency score on actual runs
    getRenderer(pApp)->setBaselineMedian(baselineStats.medianWorkDuration);

    if (testSet.count("heavy_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(heavyTarget, events, pApp, pSource, "heavy_load"); });
    }

    if (testSet.count("light_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(lightTarget, events, pApp, pSource, "light_load"); });
    }

    if (testSet.count("transition_load") > 0) {
        tests.push_back([&]() {
            sleep(5);
            drawFramesWithTarget(lightTarget, events, pApp, pSource, "transition_load_1");
            sleep(5);
            drawFramesWithTarget(heavyTarget, events, pApp, pSource, "transition_load_2");
            sleep(5);
            drawFramesWithTarget(lightTarget, events, pApp, pSource, "transition_load_3");
        });
    }

    std::shuffle(tests.begin(), tests.end(), std::default_random_engine{});

    for (auto &test : tests) {
        test();
    }

    JNIManager::sendResultsToJava(getRenderer(pApp)->getResults());
}
