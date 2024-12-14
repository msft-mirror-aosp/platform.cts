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

package com.android.xts.apimapper

import com.android.xts.apimapper.adapter.AndroidApiInjectionSettings
import com.android.xts.apimapper.adapter.MethodCallHookingAdapter
import com.android.xts.apimapper.adapter.RuleInjectingAdapter
import com.android.xts.apimapper.adapter.shouldProcessClass
import com.android.xts.apimapper.asm.ClassNodes
import com.android.xts.apimapper.asm.InvalidJarFileException
import com.android.xts.apimapper.asm.loadClassStructure
import com.android.xts.apimapper.asm.zipEntryNameToClassName
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

/** Inject the code to record API calls. */
class ApiMapper(val args: Array<String>) {

    fun run() {
        val apiMapperOption = ApiMapperOption(args)
        apiMapperOption.validateOptions()
        process(apiMapperOption.getInJar(), apiMapperOption.getOutJar())
    }

    private fun process(inJar: String, outJar: String) {
        val classNodes = loadClassStructure(inJar)
        ZipFile(inJar).use { inZip ->
            val inEntries = inZip.entries()
            ZipOutputStream(FileOutputStream(outJar)).use { outZip ->
                while (inEntries.hasMoreElements()) {
                    val entry = inEntries.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        throw InvalidJarFileException("$inJar is not a jar file.")
                    }
                    val className = zipEntryNameToClassName(entry.name)
                    if (className != null && shouldProcessClass(className)) {
                        processSingleClass(inZip, entry, outZip, classNodes)
                    } else {
                        // Simply copy entries not need to be instrumented.
                        copyZipEntry(inZip, entry, outZip)
                    }
                }
            }
        }
    }

    /** Copy a single ZIP entry to the output. */
    private fun copyZipEntry(
        inZip: ZipFile,
        entry: ZipEntry,
        outZip: ZipOutputStream,
    ) {
        BufferedInputStream(inZip.getInputStream(entry)).use { bis ->
            val outEntry = ZipEntry(entry.name)
            outZip.putNextEntry(outEntry)
            while (bis.available() > 0) {
                outZip.write(bis.read())
            }
            outZip.closeEntry()
        }
    }

    private fun processSingleClass(
        inZip: ZipFile,
        entry: ZipEntry,
        outZip: ZipOutputStream,
        classNodes: ClassNodes,
    ) {
        val newEntry = ZipEntry(entry.name)
        outZip.putNextEntry(newEntry)
        BufferedInputStream(inZip.getInputStream(entry)).use { bis ->
            val cr = ClassReader(bis)
            val flags = ClassWriter.COMPUTE_MAXS

            val cw = ClassWriter(flags)
            var outVisitor: ClassVisitor = cw
            outVisitor = RuleInjectingAdapter(outVisitor, classNodes)
            outVisitor = MethodCallHookingAdapter(
                outVisitor,
                AndroidApiInjectionSettings(),
                classNodes
            )
            cr.accept(outVisitor, ClassReader.EXPAND_FRAMES)
            val data = cw.toByteArray()
            outZip.write(data)
        }
        outZip.closeEntry()
    }
}
