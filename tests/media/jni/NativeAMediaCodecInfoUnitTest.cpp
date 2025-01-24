/*
 * Copyright (C) 2024 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeAMediaCodecInfoUnitTest"

#include <jni.h>
#include <media/NdkMediaCodecInfo.h>
#include <media/NdkMediaCodecStore.h>
#include <media/NdkMediaExtractor.h>
#include <sys/stat.h>

#include "NativeMediaCommon.h"

template <typename T>
bool equals(const T& op1, const T& op2) {
    return op1 == op2;
}

template <>
bool equals(const char* const& op1, const char* const& op2) {
    if (op1 != nullptr && op2 != nullptr)
        return strcmp(op1, op2) == 0;
    else
        return op1 == op2;
}

template <>
bool equals(const AIntRange& op1, const AIntRange& op2) {
    return op1.mLower == op2.mLower && op1.mUpper == op2.mUpper;
}

template <>
bool equals(const AMediaCodecKind& kind1, const AMediaCodecKind& kind2) {
    return kind1 == kind2;
}

template <typename T>
std::string toString(const T& val) {
    return std::to_string(val);
}

template <>
std::string toString(const char* const& val) {
    if (val == nullptr) return "null";
    return std::string(val);
}

template <>
std::string toString(const AIntRange& val) {
    return StringFormat("range lower %d, range upper %d", val.mLower, val.mUpper);
}

template <>
std::string toString(const AMediaFormat* const& val) {
    return std::string(AMediaFormat_toString((AMediaFormat*)val));
}

#define CLEANUP_IF_FALSE(cond) \
    if (!(isPass = (cond))) {  \
        goto CleanUp;          \
    }

class NativeAMediaCodecInfoUnitTest {
private:
    const char* mCodecName;
    const AMediaCodecInfo* mCodecInfo;
    const ACodecVideoCapabilities* mVideoCaps;
    const ACodecAudioCapabilities* mAudioCaps;
    const ACodecEncoderCapabilities* mEncoderCaps;
    std::string mErrorLogs;

    template <typename T, typename U>
    bool validateGetCodecMetadata(const T* obj, U (*func)(const T* obj), U expResultForInvalidArgs,
                                  U expResult, const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataArgs(const T* obj, int32_t (*func)(const T* obj, const U arg),
                                      int32_t expResultForInvalidArgs, U arg, int32_t expResult,
                                      const char* funcName);

    template <typename T>
    bool validateGetCapabilities(const T* obj,
                                 media_status_t (*func)(const AMediaCodecInfo* info, const T** obj),
                                 media_status_t expResult, const char* funcName);

    template <typename T>
    bool validateGetCodecMetadataIntRange(const T* obj,
                                          media_status_t (*func)(const T* obj, AIntRange* outRange),
                                          AIntRange& expRange, const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataArgsArray(const T* obj,
                                           media_status_t (*func)(const T* obj, const U** outArray,
                                                                  size_t* outCount),
                                           const U* expArray, size_t expCount,
                                           const char* funcName);

public:
    NativeAMediaCodecInfoUnitTest(const char* codecName);
    NativeAMediaCodecInfoUnitTest(const char* codecName, bool testAudio, bool testVideo);
    ~NativeAMediaCodecInfoUnitTest() = default;

    bool validateCodecKind(int codecKind);
    bool validateIsVendor(bool isVendor);
    bool validateCanonicalName(const char* name);
    bool validateMaxSupportedInstances(int maxSupportedInstances);
    bool validateMediaCodecInfoType(int expectedCodecType);
    bool validateMediaType(const char* expectedMediaType);
    bool validateIsFeatureSupported(const char* feature, int hasSupport);
    bool validateIsFeatureRequired(const char* feature, int isRequired);
    bool validateIsFormatSupported(const char* file, const char* mediaType, bool isSupported);
    bool validateGetAudioCaps(bool isAudio);
    bool validateGetVideoCaps(bool isVideo);
    bool validateGetEncoderCaps(bool isEncoder);

    bool validateVideoCodecBitRateRange(int lower, int higher);
    bool validateVideoCodecWidthRange(int lower, int higher);
    bool validateVideoCodecHeightRange(int lower, int higher);
    bool validateVideoCodecFrameRatesRange(int lower, int higher);
    bool validateVideoCodecWidthAlignment(int alignment);
    bool validateVideoCodecHeightAlignment(int alignment);

    bool validateAudioCodecBitRateRange(int mLower, int mUpper);
    bool validateAudioCodecMaxInputChannelCount(int maxInputChannelCount);
    bool validateAudioCodecMinInputChannelCount(int minInputChannelCount);
    bool validateAudioCodecSupportedSampleRates(int* sampleRates, int count);
    bool validateAudioCodecSupportedSampleRateRanges(int* sampleRateRanges, int count);
    bool validateAudioCodecInputChannelCountRanges(int* channelCountRanges, int count);
    bool validateAudioCodecIsSampleRateSupported(int sampleRate, int isSupported);

    bool validateEncoderComplexityRange(int lower, int higher);
    bool validateEncoderQualityRange(int lower, int higher);
    bool validateEncoderIsBitrateModeSupported(int bitrateMode, int isSupported);

    std::string getErrorMsg() { return mErrorLogs; };
};

NativeAMediaCodecInfoUnitTest::NativeAMediaCodecInfoUnitTest(const char* codecName) {
    mCodecName = codecName;
    mCodecInfo = nullptr;
    mVideoCaps = nullptr;
    mAudioCaps = nullptr;
    mEncoderCaps = nullptr;
    if (__builtin_available(android 36, *)) {
        media_status_t val = AMediaCodecStore_getCodecInfo(codecName, &mCodecInfo);
        if (AMEDIA_OK != val) {
            mErrorLogs.append(
                    StringFormat("AMediaCodecStore_getCodecInfo returned with error %d \n", val));
            return;
        }
        if (equals(AMediaCodecInfo_getKind(mCodecInfo), AMediaCodecKind_ENCODER)) {
            val = AMediaCodecInfo_getEncoderCapabilities(mCodecInfo, &mEncoderCaps);
            if (AMEDIA_OK != val) {
                mErrorLogs.append(StringFormat("AMediaCodecInfo_getEncoderCapabilities "
                                               "returned with error %d \n",
                                               val));
            }
        }
    }
}

NativeAMediaCodecInfoUnitTest::NativeAMediaCodecInfoUnitTest(const char* codecName, bool testAudio,
                                                             bool testVideo)
      : NativeAMediaCodecInfoUnitTest(codecName) {
    if (__builtin_available(android 36, *)) {
        if (mCodecInfo != nullptr) {
            if (testVideo) {
                media_status_t val = AMediaCodecInfo_getVideoCapabilities(mCodecInfo, &mVideoCaps);
                if (AMEDIA_OK != val) {
                    mErrorLogs.append(StringFormat("AMediaCodecInfo_getVideoCapabilities returned "
                                                   "with error %d \n",
                                                   val));
                }
            }
            if (testAudio) {
                media_status_t val = AMediaCodecInfo_getAudioCapabilities(mCodecInfo, &mAudioCaps);
                if (AMEDIA_OK != val) {
                    mErrorLogs.append(StringFormat("AMediaCodecInfo_getAudioCapabilities returned "
                                                   "with error %d \n",
                                                   val));
                }
            }
        }
    }
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadata(const T* obj, U (*func)(const T* obj),
                                                             U expResultForInvalidArgs, U expResult,
                                                             const char* funcName) {
    if (__builtin_available(android 36, *)) {
        U got = func(nullptr);
        if (!equals(got, expResultForInvalidArgs)) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                           funcName, toString(got).c_str(),
                                           toString(expResultForInvalidArgs).c_str()));
        } else if (nullptr != obj) {
            got = func(obj);
            if (!equals(got, expResult)) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %s expected %s \n",
                                               mCodecName, funcName, toString(got).c_str(),
                                               toString(expResult).c_str()));
            } else {
                return true;
            }
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateCodecKind(int expectedKind) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        AMediaCodecKind>(mCodecInfo, AMediaCodecInfo_getKind,
                                                         AMediaCodecKind_INVALID,
                                                         (AMediaCodecKind)expectedKind,
                                                         "AMediaCodecInfo_getKind");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsVendor(bool isVendor) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo, int32_t>(mCodecInfo,
                                                                  AMediaCodecInfo_isVendor, -1,
                                                                  isVendor,
                                                                  "AMediaCodecInfo_isVendor");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateCanonicalName(const char* name) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        const char*>(mCodecInfo, AMediaCodecInfo_getCanonicalName,
                                                     nullptr, name,
                                                     "AMediaCodecInfo_getCanonicalName");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMaxSupportedInstances(int maxSupportedInstances) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        int32_t>(mCodecInfo,
                                                 AMediaCodecInfo_getMaxSupportedInstances, -1,
                                                 maxSupportedInstances,
                                                 "AMediaCodecInfo_getMaxSupportedInstances");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMediaCodecInfoType(int expectedCodecType) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        AMediaCodecType>(mCodecInfo,
                                                         AMediaCodecInfo_getMediaCodecInfoType,
                                                         AMediaCodecType_INVALID_CODEC_INFO,
                                                         (AMediaCodecType)expectedCodecType,
                                                         "AMediaCodecInfo_getMediaCodecInfoType");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMediaType(const char* expectedMediaType) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        const char*>(mCodecInfo, AMediaCodecInfo_getMediaType,
                                                     nullptr, expectedMediaType,
                                                     "AMediaCodecInfo_getMediaType");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataArgs(
        const T* obj, int32_t (*func)(const T* obj, const U arg), int32_t expResultForInvalidArgs,
        U arg, int32_t expResult, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        int got = func(nullptr, arg);
        if (!equals(got, expResultForInvalidArgs)) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                           funcName, toString(got).c_str(),
                                           toString(expResultForInvalidArgs).c_str()));
            return false;
        }
        if (std::is_pointer_v<U>) {
            got = func(nullptr, (U)0);
            if (!equals(got, expResultForInvalidArgs)) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                               funcName, toString(got).c_str(),
                                               toString(expResultForInvalidArgs).c_str()));
                return false;
            }
        }
        if (nullptr != obj) {
            if (std::is_pointer_v<U>) {
                got = func(obj, (U)0);
                if (!equals(got, expResultForInvalidArgs)) {
                    mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                                   funcName, toString(got).c_str(),
                                                   toString(expResultForInvalidArgs).c_str()));
                    return false;
                }
            }
            got = func(obj, arg);
            if (!equals(got, expResult)) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %s expected %s \n",
                                               mCodecName, funcName, toString(got).c_str(),
                                               toString(expResult).c_str()));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFeatureSupported(const char* feature,
                                                               int hasSupport) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<AMediaCodecInfo,
                                            const char*>(mCodecInfo,
                                                         AMediaCodecInfo_isFeatureSupported, -1,
                                                         feature, hasSupport,
                                                         "AMediaCodecInfo_isFeatureSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFeatureRequired(const char* feature, int isRequired) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<AMediaCodecInfo,
                                            const char*>(mCodecInfo,
                                                         AMediaCodecInfo_isFeatureRequired, -1,
                                                         feature, isRequired,
                                                         "AMediaCodecInfo_isFeatureRequired");
    }
    return false;
}

AMediaFormat* setUpExtractor(const char* srcFile, const char* mediaType) {
    FILE* fp = fopen(srcFile, "rbe");
    if (!fp) return nullptr;
    struct stat buf {};
    AMediaFormat* currFormat = nullptr;
    if (!fstat(fileno(fp), &buf)) {
        AMediaExtractor* extractor = AMediaExtractor_new();
        media_status_t res = AMediaExtractor_setDataSourceFd(extractor, fileno(fp), 0, buf.st_size);
        if (res == AMEDIA_OK) {
            for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(extractor);
                 trackID++) {
                currFormat = AMediaExtractor_getTrackFormat(extractor, trackID);
                const char* currMediaType = nullptr;
                AMediaFormat_getString(currFormat, AMEDIAFORMAT_KEY_MIME, &currMediaType);
                if (strcmp(currMediaType, mediaType) == 0) {
                    break;
                }
                AMediaFormat_delete(currFormat);
                currFormat = nullptr;
            }
        }
        AMediaExtractor_delete(extractor);
    }
    fclose(fp);
    return currFormat;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFormatSupported(const char* file,
                                                              const char* mediaType,
                                                              bool isSupported) {
    bool isPass = false;
    if (__builtin_available(android 36, *)) {
        AMediaFormat* format = setUpExtractor(file, mediaType);
        if (format == nullptr) {
            mErrorLogs.append(StringFormat("Encountered unknown error while getting track format "
                                           "from file %s, mediaType %s \n",
                                           file, mediaType));
            return false;
        }
        isPass = validateGetCodecMetadataArgs<
                AMediaCodecInfo, const AMediaFormat*>(mCodecInfo, AMediaCodecInfo_isFormatSupported,
                                                      -1, format, isSupported,
                                                      "AMediaCodecInfo_isFormatSupported");
        AMediaFormat_delete(format);
    }
    return isPass;
}

template <typename T>
bool NativeAMediaCodecInfoUnitTest::validateGetCapabilities(
        const T* obj, media_status_t (*func)(const AMediaCodecInfo* info, const T** obj),
        media_status_t expResult, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        media_status_t status = func(nullptr, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        status = func(nullptr, &obj);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (mCodecInfo != nullptr) {
            status = func(mCodecInfo, nullptr);
            if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
                mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(mCodecInfo, &obj);
            if (expResult != status) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %d, expected %d\n",
                                               mCodecName, funcName, status, expResult));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetAudioCaps(bool isAudio) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecAudioCapabilities>(mAudioCaps, AMediaCodecInfo_getAudioCapabilities,
                                         isAudio ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                         "AMediaCodecInfo_getAudioCapabilities");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetVideoCaps(bool isVideo) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecVideoCapabilities>(mVideoCaps, AMediaCodecInfo_getVideoCapabilities,
                                         isVideo ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                         "AMediaCodecInfo_getVideoCapabilities");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetEncoderCaps(bool isEncoder) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecEncoderCapabilities>(mEncoderCaps, AMediaCodecInfo_getEncoderCapabilities,
                                           isEncoder ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                           "AMediaCodecInfo_getEncoderCapabilities");
    }
    return false;
}

template <typename T>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataIntRange(
        const T* obj, media_status_t (*func)(const T* obj, AIntRange* outRange),
        AIntRange& expRange, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        media_status_t status = func(nullptr, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        AIntRange got;
        status = func(nullptr, &got);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (obj != nullptr) {
            status = func(obj, nullptr);
            if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &got);
            if (AMEDIA_OK != status) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %d expected %d \n",
                                               mCodecName, funcName, status, AMEDIA_OK));
                return false;
            }
            if (!equals(expRange, got)) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %s, expected %s \n",
                                               mCodecName, funcName, toString(got).c_str(),
                                               toString(expRange).c_str()));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecBitRateRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getBitrateRange,
                                         expected, "ACodecVideoCapabilities_getBitrateRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecWidthRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedWidths,
                                         expected, "ACodecVideoCapabilities_getSupportedWidths");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecHeightRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedHeights,
                                         expected, "ACodecVideoCapabilities_getSupportedHeights");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecFrameRatesRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedFrameRates,
                                         expected,
                                         "ACodecVideoCapabilities_getSupportedFrameRates");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecWidthAlignment(int alignment) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecVideoCapabilities,
                                        int32_t>(mVideoCaps,
                                                 ACodecVideoCapabilities_getWidthAlignment, -1,
                                                 alignment,
                                                 "ACodecVideoCapabilities_getWidthAlignment");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecHeightAlignment(int alignment) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecVideoCapabilities,
                                        int32_t>(mVideoCaps,
                                                 ACodecVideoCapabilities_getHeightAlignment, -1,
                                                 alignment,
                                                 "ACodecVideoCapabilities_getHeightAlignment");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecBitRateRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecAudioCapabilities>(mAudioCaps, ACodecAudioCapabilities_getBitrateRange,
                                         expected, "ACodecAudioCapabilities_getBitrateRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecMaxInputChannelCount(
        int maxInputChannelCount) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecAudioCapabilities,
                                        int32_t>(mAudioCaps,
                                                 ACodecAudioCapabilities_getMaxInputChannelCount,
                                                 -1, maxInputChannelCount,
                                                 "ACodecAudioCapabilities_getMaxInputChannelCount");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecMinInputChannelCount(
        int minInputChannelCount) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecAudioCapabilities,
                                        int32_t>(mAudioCaps,
                                                 ACodecAudioCapabilities_getMinInputChannelCount,
                                                 -1, minInputChannelCount,
                                                 "ACodecAudioCapabilities_getMinInputChannelCount");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataArgsArray(
        const T* obj, media_status_t (*func)(const T* obj, const U** outArray, size_t* outCount),
        const U* expArray, size_t expCount, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        const U* gotArray = nullptr;
        size_t gotCount = 0;

        media_status_t status = func(nullptr, &gotArray, &gotCount);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (nullptr != obj) {
            status = func(obj, nullptr, &gotCount);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &gotArray, nullptr);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &gotArray, &gotCount);
            if (status != AMEDIA_OK) {
                mErrorLogs.append(
                        StringFormat("%s returned %d, expected %d\n", funcName, status, AMEDIA_OK));
                return false;
            }
            if (gotCount != expCount) {
                mErrorLogs.append(StringFormat("%s returned array count as %d, expected %d\n",
                                               funcName, gotCount, expCount));
                return false;
            }
            for (int i = 0; i < expCount; ++i) {
                if (!equals(expArray[i], gotArray[i])) {
                    mErrorLogs.append(StringFormat("For %s, array item at index %d: returned %s, "
                                                   "expected %s\n",
                                                   funcName, i, toString(gotArray[i]).c_str(),
                                                   toString(expArray[i]).c_str()));
                    return false;
                }
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecSupportedSampleRates(int* sampleRates,
                                                                           int count) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities, int>(mAudioCaps,
                                              ACodecAudioCapabilities_getSupportedSampleRates,
                                              sampleRates, count,
                                              "ACodecAudioCapabilities_getSupportedSampleRates");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecSupportedSampleRateRanges(
        int* sampleRateRanges, int count) {
    if (__builtin_available(android 36, *)) {
        AIntRange ranges[count];
        for (int i = 0; i < count; i++) {
            ranges[i].mLower = sampleRateRanges[2 * i];
            ranges[i].mUpper = sampleRateRanges[2 * i + 1];
        }
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities,
                AIntRange>(mAudioCaps, ACodecAudioCapabilities_getSupportedSampleRateRanges, ranges,
                           count, "ACodecAudioCapabilities_getSupportedSampleRateRanges");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecInputChannelCountRanges(
        int* channelCountRanges, int count) {
    if (__builtin_available(android 36, *)) {
        AIntRange ranges[count];
        for (int i = 0; i < count; i++) {
            ranges[i].mLower = channelCountRanges[2 * i];
            ranges[i].mUpper = channelCountRanges[2 * i + 1];
        }
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities,
                AIntRange>(mAudioCaps, ACodecAudioCapabilities_getInputChannelCountRanges, ranges,
                           count, "ACodecAudioCapabilities_getInputChannelCountRanges");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecIsSampleRateSupported(int sampleRate,
                                                                            int isSupported) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<
                ACodecAudioCapabilities, int32_t>(mAudioCaps,
                                                  ACodecAudioCapabilities_isSampleRateSupported, -1,
                                                  sampleRate, isSupported,
                                                  "ACodecAudioCapabilities_isSampleRateSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderIsBitrateModeSupported(int bitrateMode,
                                                                          int isSupported) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<
                ACodecEncoderCapabilities,
                ABitrateMode>(mEncoderCaps, ACodecEncoderCapabilities_isBitrateModeSupported, -1,
                              (ABitrateMode)bitrateMode, isSupported,
                              "ACodecEncoderCapabilities_isBitrateModeSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderComplexityRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecEncoderCapabilities>(mEncoderCaps,
                                           ACodecEncoderCapabilities_getComplexityRange, expected,
                                           "ACodecEncoderCapabilities_getComplexityRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderQualityRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecEncoderCapabilities>(mEncoderCaps, ACodecEncoderCapabilities_getQualityRange,
                                           expected, "ACodecEncoderCapabilities_getQualityRange");
    }
    return false;
}

jboolean nativeTestAMediaCodecInfo(JNIEnv* env, jobject, jstring jCodecName, jboolean isEncoder,
                                   jint jCodecKind, jboolean isVendor, jstring jCanonicalName,
                                   jint jMaxSupportedInstances, jint jExpectedCodecType,
                                   jstring jMediaType, jobjectArray jFeaturesList,
                                   jint jFeatureSupportMap, jint jFeatureRequiredMap,
                                   jobjectArray jFileArray, jbooleanArray jIsFormatSupportedArray,
                                   jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    const char* canonicalName = env->GetStringUTFChars(jCanonicalName, nullptr);
    const char* mediaType = env->GetStringUTFChars(jMediaType, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName);
    jsize featureCount = env->GetArrayLength(jFeaturesList);
    jsize formatCount = env->GetArrayLength(jFileArray);
    jstring jFeature, jFile;
    const char *feature = nullptr, *file = nullptr;
    jboolean* isFormatSupportedArray = nullptr;
    bool isPass;
    CLEANUP_IF_FALSE(testUtil->validateCodecKind(jCodecKind))
    CLEANUP_IF_FALSE(testUtil->validateIsVendor(isVendor))
    CLEANUP_IF_FALSE(testUtil->validateCanonicalName(canonicalName))
    CLEANUP_IF_FALSE(testUtil->validateMaxSupportedInstances(jMaxSupportedInstances))
    CLEANUP_IF_FALSE(testUtil->validateMediaCodecInfoType(jExpectedCodecType))
    CLEANUP_IF_FALSE(testUtil->validateMediaType(mediaType))
    for (auto i = 0; i < featureCount; i++) {
        jFeature = (jstring)env->GetObjectArrayElement(jFeaturesList, i);
        feature = env->GetStringUTFChars(jFeature, nullptr);
        CLEANUP_IF_FALSE(testUtil->validateIsFeatureSupported(feature, jFeatureSupportMap & 1))
        jFeatureSupportMap >>= 1;
        CLEANUP_IF_FALSE(testUtil->validateIsFeatureRequired(feature, jFeatureRequiredMap & 1))
        jFeatureRequiredMap >>= 1;
        env->ReleaseStringUTFChars(jFeature, feature);
        feature = nullptr;
    }
    isFormatSupportedArray = env->GetBooleanArrayElements(jIsFormatSupportedArray, nullptr);
    for (auto i = 0; i < formatCount; i++) {
        jFile = (jstring)env->GetObjectArrayElement(jFileArray, i);
        file = env->GetStringUTFChars(jFile, nullptr);
        CLEANUP_IF_FALSE(
                testUtil->validateIsFormatSupported(file, mediaType, isFormatSupportedArray[i]))
        env->ReleaseStringUTFChars(jFile, file);
        file = nullptr;
    }
    CLEANUP_IF_FALSE(
            testUtil->validateGetAudioCaps(strncmp(mediaType, "audio/", strlen("audio/")) == 0))
    CLEANUP_IF_FALSE(
            testUtil->validateGetVideoCaps(strncmp(mediaType, "video/", strlen("video/")) == 0))
    CLEANUP_IF_FALSE(testUtil->validateGetEncoderCaps(isEncoder))
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jCodecName, codecName);
    env->ReleaseStringUTFChars(jCanonicalName, canonicalName);
    env->ReleaseStringUTFChars(jMediaType, mediaType);
    if (feature) env->ReleaseStringUTFChars(jFeature, feature);
    if (file) env->ReleaseStringUTFChars(jFile, file);
    if (isFormatSupportedArray)
        env->ReleaseBooleanArrayElements(jIsFormatSupportedArray, isFormatSupportedArray, 0);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoVideoCaps(JNIEnv* env, jobject, jstring jCodecName,
                                            jint jBitRateRangeLower, jint jBitRateRangeUpper,
                                            jint jSupportedWidthLower, jint jSupportedWidthUpper,
                                            jint jSupportedHeightLower, jint jSupportedHeightUpper,
                                            jint jSupportedFrameRateLower,
                                            jint jSupportedFrameRateUpper, jint jWidthAlignment,
                                            jint jHeightAlignment, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, false, true);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecBitRateRange(jBitRateRangeLower, jBitRateRangeUpper))
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecWidthRange(jSupportedWidthLower, jSupportedWidthUpper))
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecHeightRange(jSupportedHeightLower, jSupportedHeightUpper))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecFrameRatesRange(jSupportedFrameRateLower,
                                                                 jSupportedFrameRateUpper))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecWidthAlignment(jWidthAlignment))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecHeightAlignment(jHeightAlignment))
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jCodecName, codecName);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoGetAudioCapabilities(
        JNIEnv* env, jobject, jstring jCodecName, jint jBitRateRangeLower, jint jBitRateRangeUpper,
        jint jMaxInputChannelCount, jint jMinInputChannelCount, jintArray jSampleRates,
        jintArray jSampleRateRanges, jintArray jInputChannelCountRanges,
        jintArray jStandardSampleRatesArray, jint jStandardSampleRatesSupportMap, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    jint* sampleRatesArray = nullptr;
    jsize sampleRatesCount = 0;
    if (jSampleRates != nullptr) {
        sampleRatesArray = env->GetIntArrayElements(jSampleRates, nullptr);
        sampleRatesCount = env->GetArrayLength(jSampleRates);
    }
    jint* sampleRateRanges = env->GetIntArrayElements(jSampleRateRanges, nullptr);
    jsize sampleRateRangeCount = env->GetArrayLength(jSampleRateRanges);
    jint* channelCountRanges = env->GetIntArrayElements(jInputChannelCountRanges, nullptr);
    jsize channelCountRangeCount = env->GetArrayLength(jInputChannelCountRanges);
    jint* standardSampleRatesArray = env->GetIntArrayElements(jStandardSampleRatesArray, nullptr);
    jsize standardSampleRatesCount = env->GetArrayLength(jStandardSampleRatesArray);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, true, false);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecBitRateRange(jBitRateRangeLower, jBitRateRangeUpper))
    CLEANUP_IF_FALSE(testUtil->validateAudioCodecMinInputChannelCount(jMinInputChannelCount))
    CLEANUP_IF_FALSE(testUtil->validateAudioCodecMaxInputChannelCount(jMaxInputChannelCount))
    if (sampleRatesArray) {
        CLEANUP_IF_FALSE(testUtil->validateAudioCodecSupportedSampleRates(sampleRatesArray,
                                                                          sampleRatesCount))
    }
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecSupportedSampleRateRanges(sampleRateRanges,
                                                                  sampleRateRangeCount / 2))
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecInputChannelCountRanges(channelCountRanges,
                                                                channelCountRangeCount / 2))
    for (auto i = 0; i < standardSampleRatesCount; i++) {
        CLEANUP_IF_FALSE(
                testUtil->validateAudioCodecIsSampleRateSupported(standardSampleRatesArray[i],
                                                                  jStandardSampleRatesSupportMap &
                                                                          1))
        jStandardSampleRatesSupportMap >>= 1;
    }
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    env->ReleaseIntArrayElements(jStandardSampleRatesArray, standardSampleRatesArray, 0);
    if (sampleRatesArray) env->ReleaseIntArrayElements(jSampleRates, sampleRatesArray, 0);
    env->ReleaseIntArrayElements(jSampleRateRanges, sampleRateRanges, 0);
    env->ReleaseIntArrayElements(jInputChannelCountRanges, channelCountRanges, 0);
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoGetEncoderCapabilities(
        JNIEnv* env, jobject, jstring jCodecName, jint jComplexityRangeLower,
        jint jComplexityRangeUpper, jint jQualityRangeLower, jint jQualityRangeUpper,
        jint jBitrateModeSupportMap, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateEncoderComplexityRange(jComplexityRangeLower, jComplexityRangeUpper))
    CLEANUP_IF_FALSE(testUtil->validateEncoderQualityRange(jQualityRangeLower, jQualityRangeUpper))
    for (int i = 0; i < 4; i++) {
        CLEANUP_IF_FALSE(
                testUtil->validateEncoderIsBitrateModeSupported(i, jBitrateModeSupportMap & 1))
        jBitrateModeSupportMap >>= 1;
    }
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return static_cast<jboolean>(isPass);
}

int registerAndroidMediaV2CtsNativeMediaCodecInfoUnitTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestAMediaCodecInfo",
             "(Ljava/lang/String;ZIZLjava/lang/String;IILjava/lang/String;[Ljava/lang/"
             "String;II[Ljava/lang/String;[ZLjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfo},
            {"nativeTestAMediaCodecInfoVideoCaps",
             "(Ljava/lang/String;IIIIIIIIIILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoVideoCaps},
            {"nativeTestAMediaCodecInfoGetAudioCapabilities",
             "(Ljava/lang/String;IIII[I[I[I[IILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoGetAudioCapabilities},
            {"nativeTestAMediaCodecInfoGetEncoderCapabilities",
             "(Ljava/lang/String;IIIIILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoGetEncoderCapabilities},
    };
    jclass c = env->FindClass("android/mediav2/cts/NativeAMediaCodecInfoTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsNativeMediaCodecInfoUnitTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
