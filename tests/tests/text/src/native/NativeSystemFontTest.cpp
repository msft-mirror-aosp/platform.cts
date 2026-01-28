/**
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

#define LOG_TAG "NativeSystemFontTest"

#include <android/font.h>
#include <android/font_matcher.h>
#include <gtest/gtest.h>
#include <sys/stat.h>
#include <unistd.h>

#include <string>
#include <vector>

namespace android {

class NativeSystemFontTest : public ::testing::Test {
protected:
    // Replicates the logic from the JNI to setup the matcher and call AFontMatcher_match
    std::pair<AFont*, uint32_t> matchFamilyStyleCharacter(const char* familyName, int weight,
                                                          bool italic, const char* languageTags,
                                                          int familyVariant,
                                                          const std::vector<uint16_t>& text) {
        AFontMatcher* matcher = AFontMatcher_create();
        AFontMatcher_setStyle(matcher, weight, italic);
        AFontMatcher_setLocales(matcher, languageTags);
        AFontMatcher_setFamilyVariant(matcher, familyVariant);

        uint32_t runLength;
        AFont* font = AFontMatcher_match(matcher, familyName, text.data(), text.size(), &runLength);
        AFontMatcher_destroy(matcher);
        return std::make_pair(font, runLength);
    }
};

TEST_F(NativeSystemFontTest, MatchFamilyStyleCharacter) {
    auto [fontA, runLengthA] = matchFamilyStyleCharacter("sans", 400, false, "en-US", 0, {'A'});
    auto [fontB, runLengthB] = matchFamilyStyleCharacter("sans", 400, false, "en-US", 0, {'B'});

    EXPECT_EQ(runLengthA, runLengthB);
    EXPECT_STREQ(AFont_getFontFilePath(fontA), AFont_getFontFilePath(fontB));

    AFont_close(fontA);
    AFont_close(fontB);
}

TEST_F(NativeSystemFontTest, MatchFamilyStyleCharacter_fallback) {
    auto [fontA, runLengthA] =
            matchFamilyStyleCharacter("Unknown-Generic-Family", 400, false, "en-US", 0, {'A'});
    auto [fontB, runLengthB] = matchFamilyStyleCharacter("Another-Unknown-Generic-Family", 400,
                                                         false, "en-US", 0, {'B'});

    EXPECT_EQ(runLengthA, runLengthB);
    EXPECT_STREQ(AFont_getFontFilePath(fontA), AFont_getFontFilePath(fontB));

    AFont_close(fontA);
    AFont_close(fontB);
}

TEST_F(NativeSystemFontTest, MatchFamilyStyleCharacter_notCrash) {
    const char* genericFamilies[] = {
            "sans",
            "sans-serif",
            "monospace",
            "cursive",
            "fantasy", // generic families
            "Helvetica",
            "Roboto",
            "Times",            // known family names but not supported by Android
            "Unknown Families", // Random string
    };

    int weights[] = {
            0,    150, 400, 700, 1000, // valid weights
            -100, 1100                 // out-of-range
    };

    const char* languageTags[] = {// Valid language tags
                                  "", "en-US", "und", "ja-JP,zh-CN", "en-Latn", "en-Zsye-US",
                                  "en-GB", "en-GB,en-AU",
                                  // Invalid language tags
                                  "aaa", "100", "あ", "-"};

    uint16_t familyVariants[] = {0, 1, 2}; // Family variants, DEFAULT, COMPACT and ELEGANT.

    const std::vector<uint16_t> inputTexts[] = {
            {'A'},
            {'B'},
            {'a', 'b', 'c'}, // Alphabet input
            {0x3042},
            {0x3042, 0x3046, 0x3048},
            {0x4f60, 0x597d}, // CJK characters
            {0x0627, 0x0644, 0x0639, 0x064e, 0x0631, 0x064e, 0x0628, 0x0650, 0x064a, 0x064e, 0x0651,
             0x0629}, // Arabic
            // Emoji, emoji sequence and surrogate pairs
            {0xd83d, 0xde00},
            {0xd83c, 0xddfa, 0xd83c, 0xddf8},
            {0xd83d, 0xdc68, 0x200d, 0xd83c, 0xdfa4},
            // Unpaired surrogate pairs
            {0xd83d},
            {0xde00},
            {0xde00, 0xd83d},
    };

    for (const char* familyName : genericFamilies) {
        for (int weight : weights) {
            for (bool italic : {false, true}) {
                for (const char* languageTag : languageTags) {
                    for (uint16_t familyVariant : familyVariants) {
                        for (const std::vector<uint16_t>& inputText : inputTexts) {
                            auto [font, runLength] =
                                    matchFamilyStyleCharacter(familyName, weight, italic,
                                                              languageTag, familyVariant,
                                                              inputText);

                            ASSERT_NE(nullptr, font)
                                    << "Failed with family=" << familyName << ", weight=" << weight
                                    << ", italic=" << italic << ", lang=" << languageTag
                                    << ", variant=" << familyVariant;

                            EXPECT_GE(runLength, 1u);

                            const char* fontFile = AFont_getFontFilePath(font);
                            ASSERT_NE(nullptr, fontFile);
                            struct stat st;
                            ASSERT_EQ(0, stat(fontFile, &st)) << "Failed to stat " << fontFile;
                            EXPECT_TRUE(S_ISREG(st.st_mode));
                            EXPECT_GT(st.st_size, 0);
                            EXPECT_EQ(0, access(fontFile, R_OK));
                            EXPECT_NE(0, access(fontFile, W_OK));
                            EXPECT_NE(0, access(fontFile, X_OK));

                            AFont_close(font);
                        }
                    }
                }
            }
        }
    }
}

} // namespace android
