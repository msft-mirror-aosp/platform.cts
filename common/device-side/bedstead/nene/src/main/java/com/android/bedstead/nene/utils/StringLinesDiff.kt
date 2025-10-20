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
package com.android.bedstead.nene.utils

/**
 * Simple tool to find lines in strings that have changed or generate a diff of the strings.
 */
class StringLinesDiff(private val base: String, private val newString: String) {

    /**
     * Find lines in strings that have changed, ignores order of lines!
    */
    private fun findExtraLines(baseString: String, newString: String): List<String> {
        val baseStringLines = baseString.lines()
        return newString.lines().filterNot { baseStringLines.contains(it) }
    }

    /**
     * List of lines present in newString but absent in baseString.
     */
    val extraLines by lazy { findExtraLines(base, newString) }

    /**
     * Returns a string representation of the extra lines, joining them with a newline character.
     */
    fun extraLinesString() = extraLines.joinToString(separator = "\n")

    /**
     * List of lines present in baseString but absent in newString.
     */
    val missingLines by lazy { findExtraLines(newString, base) }

    /**
     * Returns number of lines different between baseString and newString.
     */
    fun countLinesDifference(): Int = maxOf(extraLines.size, missingLines.size)

    companion object {
        const val DEVICE_POLICY_STANDARD_LINES_DIFFERENCE = 4
    }

    /**
     * Diff of the two strings.
     */
    val diff by lazy { generateDiff() }

    /**
     * Returns the diff of the two strings.
     */
    fun diffString(): String = diff

    /**
    * Generates a diff respecting line order, using the longest common subsequence.
    */
    private fun generateDiff(): String {
        val baseLines = if (base.isEmpty()) emptyList() else base.split('\n')
        val newLines = if (newString.isEmpty()) emptyList() else newString.split('\n')

        val lcs = longestCommonSubsequence(baseLines, newLines)
        val diff = mutableListOf<String>()

        var baseIndex = 0
        var newIndex = 0
        var lcsIndex = 0

        while (lcsIndex < lcs.size) {
            val lcsLine = lcs[lcsIndex]

            while (baseIndex < baseLines.size && baseLines[baseIndex] != lcsLine) {
                diff.add("- " + baseLines[baseIndex])
                baseIndex++
            }

            while (newIndex < newLines.size && newLines[newIndex] != lcsLine) {
                diff.add("+ " + newLines[newIndex])
                newIndex++
            }

            if (baseIndex < baseLines.size && newIndex < newLines.size) {
                // diff.add("  " + lcsLine)
                baseIndex++
                newIndex++
                lcsIndex++
            } else {
                lcsIndex++ // Should not happen with correct LCS
            }
        }

        while (baseIndex < baseLines.size) {
            diff.add("- " + baseLines[baseIndex])
            baseIndex++
        }

        while (newIndex < newLines.size) {
            diff.add("+ " + newLines[newIndex])
            newIndex++
        }

        if (diff.isEmpty()) {
            return ""
        }

        return diff.joinToString("\n")
    }

   /**
   * Simple implementation of longest commom subsequence used to generate a diff of two strings.
   * Returns List<String> where each entry represents a line of the lcs for easier processing
   * when generating a diff from it.
   */
   private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<String> {
       val lengths = Array(a.size + 1) { IntArray(b.size + 1) }

       for (i in 0 until a.size) {
           for (j in 0 until b.size) {
               if (a[i] == b[j]) {
                   lengths[i + 1][j + 1] = lengths[i][j] + 1
               } else {
                   lengths[i + 1][j + 1] = maxOf(lengths[i + 1][j], lengths[i][j + 1])
               }
           }
       }

       val s = mutableListOf<String>()
       var x = a.size
       var y = b.size
       while (x > 0 && y > 0) {
           if (a[x - 1] == b[y - 1]) {
               s.add(a[x - 1])
               x--
               y--
           } else if (lengths[x - 1][y] >= lengths[x][y - 1]) {
               x--
           } else {
               y--
           }
       }
       return s.reversed()
   }
}
