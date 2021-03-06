/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.tasks.featuresplit.toIdString
import com.google.common.io.Files
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.specs.Spec
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.stream.Collectors

/**
 * Implementation of [Spec] to filter out directories from a [FileCollection]
 *
 * @param artifacts the [ArtifactCollection] containing the elements to be filtered
 * @param excludedDirectoryFiles [FileCollection] containing text files which content are a list
 * of filtered artifacts.
 */
class FilteringSpec(
    internal val artifacts: ArtifactCollection,
    private val excludedDirectoryFiles: FileCollection,
    private val objectFactory: ObjectFactory
) : Spec<File>, Serializable {

    @Transient
    private var excluded: Lazy<Set<String>> = lazy { computeFilteredArtifacts() }

    @Transient
    private var filteredFileCollection: Lazy<FileCollection> = lazy { initFilteredFileCollection() }

    /** Keep lazy, as invoking getArtifacts() is quite costly with configuration caching. */
    @Transient
    private var originalArtifacts = lazy { artifacts.artifacts }

    override fun isSatisfiedBy(file: File): Boolean {
        if (excluded.value.isEmpty()) return true
        val keptFiles = originalArtifacts.value.asSequence()
            .filter { !excluded.value.contains(it.toIdString()) }
            .map { it.file }.toSet()
        return keptFiles.contains(file)
    }

    private fun computeFilteredArtifacts(): Set<String> = excludedDirectoryFiles
        .files
        .stream()
        .map { file: File ->
            if (file.isFile) Files.readLines(
                file,
                Charsets.UTF_8
            ) else listOf()
        }
        .flatMap { list: List<String> -> list.stream() }
        .collect(Collectors.toSet<String>())

    private fun initFilteredFileCollection() =
        objectFactory.fileCollection().from(artifacts.artifactFiles.filter(this))
            .builtBy(artifacts.artifactFiles.buildDependencies)
            .builtBy(excludedDirectoryFiles.buildDependencies)

    // Returns a MutableSet as FilteredArtifactCollection#getIterator expects this to be mutable to
    // returns a mutable iterator.
    fun getArtifactFiles(): MutableSet<ResolvedArtifactResult> {

        if (excluded.value.isEmpty()) {
            return originalArtifacts.value
        }

        return originalArtifacts.value.asSequence()
            .filter { !excluded.value.contains(it.toIdString()) }.toMutableSet()
    }

    fun getFilteredFileCollection(): FileCollection = filteredFileCollection.value

    private fun writeObject(objectOutputStream: ObjectOutputStream) {
        objectOutputStream.defaultWriteObject()
    }

    private fun readObject(objectInputStream: ObjectInputStream) {
        objectInputStream.defaultReadObject()
        excluded = lazy { computeFilteredArtifacts() }
        filteredFileCollection = lazy { initFilteredFileCollection() }
        originalArtifacts = lazy { artifacts.artifacts }
    }
}
