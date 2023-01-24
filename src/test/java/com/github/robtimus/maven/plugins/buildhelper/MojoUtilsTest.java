/*
 * MojoUtilsTest.java
 * Copyright 2023 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.maven.plugins.buildhelper;

import static com.github.robtimus.maven.plugins.buildhelper.MojoUtils.getProjectRoot;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;

@SuppressWarnings("nls")
class MojoUtilsTest {

    @Nested
    @DisplayName("getProjectRoot")
    class GetProjectRoot {

        private Path baseDir = Paths.get(URI.create("memory:/level1/level2/level3"));

        @BeforeEach
        void initFileSystem() {
            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> Files.createDirectories(baseDir));
        }

        @Test
        @DisplayName("current dir is Git root")
        void testCurrentDirIsGitRoot() {
            assertDoesNotThrow(() -> {
                for (Path dir = baseDir; dir != null; dir = dir.getParent()) {
                    Files.createFile(dir.resolve("pom.xml"));
                }
                Files.createDirectories(baseDir.resolve(".git"));
            });

            assertEquals(baseDir, getProjectRoot(baseDir));
        }

        @Test
        @DisplayName("parent dir is Git root")
        void testParentDirIsGitRoot() {
            assertDoesNotThrow(() -> {
                for (Path dir = baseDir; dir != null; dir = dir.getParent()) {
                    Files.createFile(dir.resolve("pom.xml"));
                }
                Files.createDirectories(baseDir.resolve("../.git"));
            });

            assertEquals(baseDir.getParent(), getProjectRoot(baseDir));
        }

        @Test
        @DisplayName("only parent contains pom")
        void testOnlyParentDirContainsPom() {
            assertDoesNotThrow(() -> Files.createFile(baseDir.resolve("../pom.xml")));

            assertEquals(baseDir.getParent(), getProjectRoot(baseDir));
        }

        @Test
        @DisplayName("parent does not contain pom")
        void testParentDirDoesNotContainPom() {
            assertEquals(baseDir, getProjectRoot(baseDir));
        }

        @Test
        @DisplayName("each folder has pom")
        void testEachFolderHasPom() {
            assertDoesNotThrow(() -> {
                for (Path dir = baseDir; dir != null; dir = dir.getParent()) {
                    Files.createFile(dir.resolve("pom.xml"));
                }
            });

            assertEquals(Paths.get(URI.create("memory:/")), getProjectRoot(baseDir));
        }
    }
}
