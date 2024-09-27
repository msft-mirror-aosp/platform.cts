## Av1FilmGrainValidationTest

The test uses crowd_run_854x480_1mbps_av1_40fg.mp4 video clip, which is included in
[CtsMediaPerformanceClassTestCases-3.2.zip](https://dl.google.com/android/xts/cts/tests/mediapc/CtsMediaPerformanceClassTestCases-3.2.zip).

### Data Generation Process
 * An AV1 clip is created with film-grain parameters using reference encoder.
 * The clip is decoded twice using reference decoder: once with film-grain enabled and once
   with film-grain disabled.
 * The variance of all frames in both decoded clips is calculated.
 * Frames with a significant difference in variance (based on a threshold) are selected.
 * The variance values for these selected frames are used in the Av1FilmGrainValidationTest.

### Commands to generate test validation data for Av1FilmGrainValidationTest

```sh
# Encode video with film-grain using reference encoder (SvtAv1EncApp)
$ SvtAv1EncApp -b <output.ivf> -i <input.yuv> -w <int> -h <int> --fps 30 --rc 2 --tbr 1M \
  --svtav1-params film-grain=40:tile-rows=0:tile-columns=0

# Decode encoded video with film-grain enabled using reference decoder (SvtAv1DecApp)
$ SvtAv1DecApp -i <output.ivf> -o <with-film-grain.yuv>

# Decode encoded video with film-grain disabled using reference decoder (SvtAv1DecApp)
$ SvtAv1DecApp -i <output.ivf> -skip-film-grain -o <without-film-grain.yuv>

# Compute per frame variance for the decoded yuvs and write to txt file
# The txt files contain frame index and variance for all the frames.
$ javac Variance.java
$ java Variance <with-film-grain.yuv> variance_fgyes.txt
$ java Variance <without-film-grain.yuv> variance_fgno.txt

# Class is available at tests/mediapc/common/tests/src/android/mediapc/cts/common/Variance.java
```
