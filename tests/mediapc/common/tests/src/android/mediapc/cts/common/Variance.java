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

package android.mediapc.cts.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileWriter;

/**
 * Utility class for computing the per frame variance.
 */
public class Variance {
    public static <T> Double computeFrameVariance(int width, int height, T luma) {
        final int bSize = 16;
        double varianceSum = 0;
        int blocks = 0;
        for (int i = 0; i < height - bSize; i += bSize) {
            for (int j = 0; j < width - bSize; j += bSize) {
                long sse = 0, sum = 0;
                int offset = i * width + j;
                for (int p = 0; p < bSize; p++) {
                    for (int q = 0; q < bSize; q++) {
                        int sample;
                        if (luma instanceof byte[]) {
                            sample = ((byte[]) luma)[offset + p * width + q];
                        } else if (luma instanceof short[]) {
                            sample = ((short[]) luma)[offset + p * width + q];
                        } else {
                            throw new IllegalArgumentException("Unsupported data type");
                        }
                        sum += sample;
                        sse += sample * sample;
                    }
                }
                double meanOfSquares = ((double) sse) / (bSize * bSize);
                double mean = ((double) sum) / (bSize * bSize);
                double squareOfMean = mean * mean;
                double blockVariance = (meanOfSquares - squareOfMean);
                varianceSum += blockVariance;
                blocks++;
            }
        }
        return (varianceSum / blocks);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java Variance <filepath> <outputFilename>");
            System.exit(1);
        }

        String filePath = args[0];
        String outputFilename = args[1];

        int width = 854;
        int height = 480;
        int numFrames = 180;
        int ySize = width * height;
        byte[] luma = new byte[ySize];

        try (FileWriter writer = new FileWriter(outputFilename)) {
            for (int i = 0; i < numFrames; i++) {
                long frameOffset = i * 3 * width * height / 2;
                try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
                    fileInputStream.skip(frameOffset);
                    int bytesRead = fileInputStream.read(luma);
                    if (bytesRead != ySize) {
                        throw new IOException("Unexpected end of file or incorrect file format");
                    }
                }
                Double variance = computeFrameVariance(width, height, luma);
                writer.write(i + " " + variance + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }
}