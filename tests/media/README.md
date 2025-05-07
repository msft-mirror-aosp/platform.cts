# MediaV2 CTS Tests

## Overview

This folder comprises of tests designed to verify MediaCodec components. The tests verify media extractors, media muxers, and media codecs functionality through both SDK and NDK APIs.

The test suite aims to:
- Validate components against CDD (Compatibility Definition Document) requirements
- Check functionality of media codec components and their plugins individually
- Test interactions between codec components, extractors and muxers

## Test Vectors

Android TestFramework automatically downloads and copies the required resources from [url](https://dl.google.com/android/xts/cts/tests/media/CtsMediaV2TestCases-5.9.zip) while running the tests. Manual download and copy is also supported. This can be done by running the script `copy_media.sh`. All Big Buck Bunny (bbb) test vectors used by this suite are derived from [Blender Foundation](https://peach.blender.org/download/). All Cosmos Laundromat (cosmat) test vectors used by this suite are derived from [xiph.org](https://media.xiph.org/)

## Test Organization

The test suite covers MediaCodec API in both normal operation and error scenarios. Error cases are separated into dedicated classes with "UnitTest" suffix (e.g., `CodecUnitTest`, `MediaFormatUnitTest`).

## Running Tests

### Basic Commands

```
# Run all Media V2 CTS tests
atest android.mediav2.cts

# Run specific test classes
atest MctsMediaV2TestCases:CodecDecoderTest CtsMediaV2TestCases:CodecDecoderTest

# Run unit tests for error cases
atest CtsMediaV2TestCases:CodecUnitTest CtsMediaV2TestCases:MediaFormatUnitTest
```

## Test Selection Features

All tests support attributes for selective execution based on various criteria:

### Filtering by Codec Name

Use `codec-prefix` to select codecs whose names begin with a specific prefix:

```
# Test only mainline MP3 codecs
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2.android.mp3

# Test only mainline HEVC decoder
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2.android.hevc.decoder

# Test all mainline c2 codecs
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2

# Test all vendor c2 codecs
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:codec-prefix:=c2
```

### Filtering by Media Type

Use `media-type-prefix` to select codecs whose supported media types begin with a specific prefix:

```
# Test only AVC video codecs
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:media-type-prefix:=video/avc
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:media-type-prefix:=video/avc

# Test all video codecs
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:media-type-prefix:=video
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:media-type-prefix:=video
```

Use `media-type-sel` to select codecs handling specific media types:

```
# Test MP3 and Vorbis audio codecs
atest MctsMediaV2TestCases -- --module-arg MctsMediaV2TestCases:instrumentation-arg:media-type-sel:=mp3,vorbis
atest CtsMediaV2TestCases -- --module-arg CtsMediaV2TestCases:instrumentation-arg:media-type-sel:=mp3,vorbis

# Test CodecDecoderTest for codecs supporting media types video/avc and audio/mp4a-latm
atest MctsMediaV2TestCases:CodecDecoderTest -- --module-arg MctsMediaV2TestCases:instrumentation-arg:media-type-sel:=avc,aac
atest CtsMediaV2TestCases:CodecDecoderTest -- --module-arg CtsMediaV2TestCases:instrumentation-arg:media-type-sel:=avc,aac
```

### Using Regular Expressions

Use `codec-filter` with regular expressions for more complex selection patterns:

```
# Run VideoEncoderTest for mainline AVC and VP9 encoders
atest MctsMediaV2TestCases:VideoEncoderTest -- --module-arg MctsMediaV2TestCases:instrumentation-arg:codec-filter:="c2\.android\.avc\.encoder\|c2\.android\.vp9\.encoder"
```

## `media-type-sel` identifiers list

| Identifier | Full Media Type |
|------------|-----------------|
| vp8 | video/x-vnd.on2.vp8 |
| vp9 | video/x-vnd.on2.vp9 |
| av1 | video/av01 |
| apv | video/apv |
| avc | video/avc |
| hevc | video/hevc |
| mpeg4 | video/mp4v-es |
| h263 | video/3gpp |
| mpeg2 | video/mpeg2 |
| vraw | video/raw |
| amrnb | audio/3gpp |
| amrwb | audio/amr-wb |
| mp3 | audio/mpeg |
| aac | audio/mp4a-latm |
| vorbis | audio/vorbis |
| opus | audio/opus |
| g711alaw | audio/g711-alaw |
| g711mlaw | audio/g711-mlaw |
| araw | audio/raw |
| flac | audio/flac |
| gsm | audio/gsm |
