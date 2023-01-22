/*
 * JavadocLicenseMojoTest.java
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.memory.MemoryFileAttributeView;
import com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;

@SuppressWarnings("nls")
class JavadocLicenseMojoTest {

    @Test
    @DisplayName("processLicenseFile")
    void testProcessLicenseFile() {
        AtomicReference<Path> capturedBuildDir = new AtomicReference<>();

        JavadocLicenseMojo mojo = new JavadocLicenseMojo() {
            @Override
            void copyLicenseFile(Path licenseFile, Path buildDir) throws MojoExecutionException {
                capturedBuildDir.set(buildDir);
            }
        };
        mojo.project = mock(MavenProject.class);

        Build build = mock(Build.class);

        when(build.getDirectory()).thenReturn("test-dir");
        when(mojo.project.getBuild()).thenReturn(build);

        assertDoesNotThrow(() -> mojo.processLicenseFile(null));

        assertEquals(Paths.get("test-dir"), capturedBuildDir.get());
    }

    @Nested
    @DisplayName("copyLicenseFile")
    class CopyLicenseFile {

        private Path licenseFile = Paths.get(URI.create("memory:/project/LICENSE.txt"));
        private Path buildDir = Paths.get(URI.create("memory:/project/target"));
        private Path targetFile = buildDir.resolve("apidocs/META-INF/LICENSE.txt");

        @BeforeEach
        void initFileSystem() {
            MemoryFileSystemProvider.clear();
            assertDoesNotThrow(() -> {
                Files.createDirectories(licenseFile.getParent());
                MemoryFileSystemProvider.setContent(licenseFile, "dummy content".getBytes());
            });
        }

        @Test
        @DisplayName("file doesn't exist yet")
        void testFileDoesntExistYet() {
            JavadocLicenseMojo mojo = new JavadocLicenseMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            assertFalse(Files.isRegularFile(targetFile));

            assertDoesNotThrow(() -> mojo.copyLicenseFile(licenseFile, buildDir));

            assertTrue(Files.isRegularFile(targetFile));

            String content = assertDoesNotThrow(() -> new String(MemoryFileSystemProvider.getContent(targetFile)));
            assertEquals("dummy content", content);

            verify(log).info(Messages.javadocLicense.copiedLicense(licenseFile, targetFile));
            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("file already exists")
        void testFileAlreadyExists() {
            JavadocLicenseMojo mojo = new JavadocLicenseMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            assertDoesNotThrow(() -> {
                Files.createDirectories(targetFile.getParent());
                MemoryFileSystemProvider.setContent(targetFile, "replaced".getBytes());
            });

            assertTrue(Files.isRegularFile(targetFile));

            assertDoesNotThrow(() -> mojo.copyLicenseFile(licenseFile, buildDir));

            assertTrue(Files.isRegularFile(targetFile));

            String content = assertDoesNotThrow(() -> new String(MemoryFileSystemProvider.getContent(targetFile)));
            assertEquals("dummy content", content);

            verify(log).info(Messages.javadocLicense.copiedLicense(licenseFile, targetFile));
            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("project folder is readonly")
        void testProjectFolderIsReadOnly() {
            JavadocLicenseMojo mojo = new JavadocLicenseMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            assertDoesNotThrow(() -> Files.getFileAttributeView(licenseFile.getParent(), MemoryFileAttributeView.class).setReadOnly(true));

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> mojo.copyLicenseFile(licenseFile, buildDir));
            AccessDeniedException cause = assertInstanceOf(AccessDeniedException.class, exception.getCause());
            assertEquals(cause.getMessage(), exception.getMessage());
            assertEquals(buildDir.getParent().toString(), cause.getFile());

            verifyNoMoreInteractions(log);
        }
    }
}
