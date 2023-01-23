/*
 * AbstractLicenseMojoTest.java
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

import static com.github.robtimus.maven.plugins.buildhelper.AbstractLicenseMojo.findLicenseFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;

@SuppressWarnings("nls")
class AbstractLicenseMojoTest {

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("success")
        void testSuccess() {
            AtomicReference<Path> capturedLicenseFile = new AtomicReference<>();

            AbstractLicenseMojo mojo = new AbstractLicenseMojo() {
                @Override
                void processLicenseFile(Path licenseFile) {
                    capturedLicenseFile.set(licenseFile);
                }
            };
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.licenseFilename = "LICENSE.txt";
            mojo.project = mock(MavenProject.class);

            when(mojo.project.getBasedir()).thenReturn(new File("."));

            assertDoesNotThrow(mojo::execute);

            assertEquals(Paths.get(mojo.licenseFilename).toAbsolutePath(), capturedLicenseFile.get());

            verify(log).debug(Messages.license.fileFound(capturedLicenseFile.get()));
            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("skipped")
        void testSkipped() {
            AbstractLicenseMojo mojo = new AbstractLicenseMojo() {
                @Override
                void processLicenseFile(Path licenseFile) {
                    // does nothing
                }
            };
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.skipLicense = true;

            assertDoesNotThrow(mojo::execute);

            verify(log).info(Messages.license.skipped());
            verifyNoMoreInteractions(log);
        }
    }

    @Nested
    @DisplayName("findLicenseFile")
    class FindLicenseFile {

        private Path baseDir = Paths.get(URI.create("memory:/level1/level2/level3"));

        private Log log;

        @BeforeEach
        void initFileSystemAndMocks() {
            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> Files.createDirectories(baseDir));

            log = mock(Log.class);
        }

        @Test
        @DisplayName("invalid license file")
        void testInvalidLicenseFile() {
            String filename = "../LICENSE.txt";

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 0, log));
            assertEquals(Messages.license.invalidFilename(filename), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license found in base dir")
        void testLicenseFoundInBaseDir() {
            String filename = "LICENSE.txt";

            Path licenseFile = baseDir.resolve(filename);
            assertDoesNotThrow(() -> Files.createFile(licenseFile));

            Path foundLicenseFile = assertDoesNotThrow(() -> findLicenseFile(baseDir, filename, 0, log));

            assertEquals(licenseFile, foundLicenseFile);

            verify(log).debug(Messages.license.fileFound(foundLicenseFile));
            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license not found in Git root")
        void testLicenseNotFoundInGitRoot() {
            String filename = "LICENSE.txt";

            assertDoesNotThrow(() -> Files.createDirectory(baseDir.resolve(".git")));

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 0, log));
            assertEquals(Messages.license.fileNotFound(filename, baseDir), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license found outside Git root")
        void testLicenseFoundOutsideGitRoot() {
            String filename = "LICENSE.txt";

            Path licenseFile = baseDir.getParent().getParent().resolve(filename);
            assertDoesNotThrow(() -> {
                Files.createFile(licenseFile);
                Files.createDirectory(baseDir.getParent().resolve(".git"));
            });

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 2, log));
            assertEquals(Messages.license.leavingGitProject(), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license in non-allowed parent")
        void testLicenseInNonAllowedParent() {
            String filename = "LICENSE.txt";

            Path licenseFile = baseDir.getParent().resolve(filename);
            assertDoesNotThrow(() -> Files.createFile(licenseFile));

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 0, log));
            assertEquals(Messages.license.fileNotFound(filename, baseDir), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license in non-allowed grand-parent")
        void testLicenseInNonAllowedGrandParent() {
            String filename = "LICENSE.txt";

            Path licenseFile = baseDir.getParent().getParent().resolve(filename);
            assertDoesNotThrow(() -> {
                Files.createFile(licenseFile);
                Files.createFile(baseDir.getParent().resolve("pom.xml"));
            });

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 1, log));
            assertEquals(Messages.license.fileNotFound(filename, baseDir.getParent()), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("license in non-allowed file system root")
        void testLicenseInNonAllowedFileSystemRoot() {
            String filename = "LICENSE.txt";

            Path licenseFile = Paths.get(URI.create("memory:/")).resolve(filename);
            assertDoesNotThrow(() -> {
                Files.createFile(licenseFile);
                for (Path dir = baseDir; dir != null; dir = dir.getParent()) {
                    Files.createFile(dir.resolve("pom.xml"));
                }
            });

            MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 2, log));
            assertEquals(Messages.license.fileNotFound(filename, baseDir.getParent().getParent()), exception.getMessage());

            verifyNoMoreInteractions(log);
        }

        @Nested
        @DisplayName("license outside project dir")
        class LicenseOutsideProjectDir {

            @Test
            @DisplayName("parent is Git root")
            void testParentIsGitRoot() {
                String filename = "LICENSE.txt";

                Path licenseFile = baseDir.getParent().getParent().resolve(filename);
                assertDoesNotThrow(() -> {
                    Files.createFile(licenseFile);
                    Files.createFile(baseDir.getParent().resolve("pom.xml"));
                    Files.createDirectories(baseDir.getParent().getParent().resolve(".git"));
                });

                Path foundLicenseFile = assertDoesNotThrow(() -> findLicenseFile(baseDir, filename, 2, log));

                assertEquals(licenseFile, foundLicenseFile);

                verify(log).debug(Messages.license.fileFound(foundLicenseFile));
                verifyNoMoreInteractions(log);
            }

            @Test
            @DisplayName("parent is not Git root")
            void testParentIsNotGitRoot() {
                String filename = "LICENSE.txt";

                Path licenseFile = baseDir.getParent().getParent().resolve(filename);
                assertDoesNotThrow(() -> {
                    Files.createFile(licenseFile);
                    Files.createFile(baseDir.getParent().resolve("pom.xml"));
                });

                MojoFailureException exception = assertThrows(MojoFailureException.class, () -> findLicenseFile(baseDir, filename, 2, log));
                assertEquals(Messages.license.leavingMavenProject(), exception.getMessage());

                verifyNoMoreInteractions(log);
            }
        }
    }
}
