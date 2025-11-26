/*
 * Copyright 2025 The Android Open Source Project
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

#include <android/native_window_jni.h>
#include <android/trace.h>
#include <android_native_app_glue.h>
#include <unistd.h>

#include <chrono>

#include "vulkan_renderer.h"

struct AppState {
    VulkanRenderer renderer;
    bool canRender = false;
};

enum class Mode {
    LOW_GPU_USAGE = 0,
    HIGH_GPU_USAGE,
    N_MODES,
};

constexpr Mode &operator++(Mode &s) {
    s = (s == Mode::HIGH_GPU_USAGE) ? Mode::LOW_GPU_USAGE : Mode::HIGH_GPU_USAGE;
    return s;
}

static void onAppCmd(struct android_app *app, int32_t cmd) {
    auto *appState = (AppState *)app->userData;
    switch (cmd) {
        case APP_CMD_START:
            if (app->window != nullptr) {
                appState->renderer.reset(app->window, app->activity->assetManager);
                appState->renderer.init();
                appState->canRender = true;
            }
        case APP_CMD_INIT_WINDOW:
            if (app->window != nullptr) {
                appState->renderer.reset(app->window, app->activity->assetManager);
                if (!appState->renderer.initialized) {
                    appState->renderer.init();
                }
                appState->canRender = true;
            }
            break;
        case APP_CMD_TERM_WINDOW:
            appState->canRender = false;
            break;
        case APP_CMD_DESTROY:
            appState->renderer.cleanup();
        default:
            break;
    }
}

void android_main(struct android_app *app) {
    AppState appState{};

    app->userData = &appState;
    app->onAppCmd = onAppCmd;

    int trianglesCount = 1;
    Mode mode = Mode::LOW_GPU_USAGE;
    auto periodStartTime = std::chrono::high_resolution_clock::now();

    while (true) {
        auto frameStartTime = std::chrono::high_resolution_clock::now();
        int ident;
        int events;
        android_poll_source *source;
        while ((ident = ALooper_pollOnce(appState.canRender ? 0 : -1, nullptr, &events,
                                         (void **)&source)) >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }
        }

        appState.renderer.render(static_cast<int>(trianglesCount));
        double lastFrameDurationMs = appState.renderer.lastFrameDurationMs;

        std::chrono::duration<double, std::milli> elapsedMs = frameStartTime - periodStartTime;
        if (elapsedMs.count() > 2000) {
            ++mode;
            periodStartTime = std::chrono::high_resolution_clock::now();
            // Since we don't know when the trace capture starts, report
            // the raytracing support status every once in a while.
            ATrace_setCounter("CtsTestDeviceRayTracingSupport",
                              appState.renderer.supportsRaytracing);
        }
        if (lastFrameDurationMs > 0) {
            double desiredFrameDuration = 10.5;
            // Adjust the number of triangles based on the previous frame time.
            switch (mode) {
                case Mode::LOW_GPU_USAGE:
                    usleep(20000);
                    break;
                case Mode::HIGH_GPU_USAGE:
                    desiredFrameDuration = 60.0;
                    break;
                case Mode::N_MODES:
                    // Should not happen
                    break;
            }
            // Shift triangles count at most 20% at a time towards the desired frame duration.
            trianglesCount *= 0.8 + 0.2 * desiredFrameDuration / lastFrameDurationMs;

            if (trianglesCount < 1) trianglesCount = 1;
        }
        ATrace_setCounter("CtsTestLastFrameTime", lastFrameDurationMs);
        ATrace_setCounter("CtsTestTriangleCount", trianglesCount);
        ATrace_setCounter("CtsTestGpuUsageMode", (int)mode);
    }
}