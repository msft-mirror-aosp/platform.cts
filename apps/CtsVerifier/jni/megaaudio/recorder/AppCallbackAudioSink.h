/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef SMOKEPLAYER_APPCALLBACKAUDIOSINK_H
#define SMOKEPLAYER_APPCALLBACKAUDIOSINK_H

#include <jni.h>
#include <JavaNativeFloatFifo.h>

#include "AudioSink.h"

class AppCallbackAudioSink: public AudioSink {
public:
    /**
     *
     * @param floatFifoPtr The pointer is managed by Java code.
     */
    explicit AppCallbackAudioSink(JavaNativeFloatFifo* floatFifoPtr);

    virtual void init(int numFrames, int numChannels) override;
    virtual void start() override;
    virtual void stop() override;

    virtual void push(float* audioData, int numFrames, int numChannels) override;

private:
    JavaNativeFloatFifo *mFloatFifoPtr = nullptr;
};

#endif //SMOKEPLAYER_APPCALLBACKAUDIOSINK_H
