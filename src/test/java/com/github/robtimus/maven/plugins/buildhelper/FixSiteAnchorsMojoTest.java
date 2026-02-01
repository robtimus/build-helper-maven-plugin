/*
 * FixSiteAnchorsMojoTest.java
 * Copyright 2025 Rob Spoor
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.memory.MemoryFileAttributeView;
import com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;

@SuppressWarnings("nls")
class FixSiteAnchorsMojoTest {

    private FixSiteAnchorsMojo mojo;
    private Log log;

    @BeforeEach
    void setupMojo() {
        mojo = new FixSiteAnchorsMojo();
        log = mock(Log.class);
        mojo.setLog(log);
    }

    @Test
    void testDefaultReplacments() {
        assertEquals(3, mojo.replacements.length);

        assertEquals(".28", mojo.replacements[0].getSearch());
        assertEquals("(", mojo.replacements[0].getReplace());
        assertEquals(".28 => (", mojo.replacements[0].toString());

        assertEquals(".29", mojo.replacements[1].getSearch());
        assertEquals(")", mojo.replacements[1].getReplace());
        assertEquals(".29 => )", mojo.replacements[1].toString());

        assertEquals(".25", mojo.replacements[2].getSearch());
        assertEquals("%", mojo.replacements[2].getReplace());
        assertEquals(".25 => %", mojo.replacements[2].toString());
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        void testFileNotFound() {
            mojo.files = new File[] {
                    new File(UUID.randomUUID().toString()),
            };
            mojo.encoding = "UTF-8";

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::execute);
            assertInstanceOf(NoSuchFileException.class, exception.getCause());
        }
    }

    @Nested
    @DisplayName("getCharset")
    class GetCharset {

        @Test
        void testGetInputCharsetNotSet() {
            Charset defaultCharset = Charset.defaultCharset();

            assertEquals(defaultCharset, mojo.getCharset());

            verify(log).warn(Messages.fixSiteAnchors.noEncoding(defaultCharset));
        }

        @Test
        void testGetInputCharsetSet() {
            mojo.encoding = "UTF-8";
            assertEquals(StandardCharsets.UTF_8, mojo.getCharset());

            verify(log, never()).warn(any(CharSequence.class));

            mojo.encoding = "US-ASCII";
            assertEquals(StandardCharsets.US_ASCII, mojo.getCharset());

            verify(log, never()).warn(any(CharSequence.class));
        }
    }

    @Nested
    @DisplayName("fixSiteAnchors")
    class FixSiteAnchors {

        private Path file;

        @BeforeEach
        void setupFile() throws IOException {
            MemoryFileSystemProvider.clear();

            file = Paths.get(URI.create("memory:/base/target/site/index.html"));

            Files.createDirectories(file.getParent());
        }

        @Test
        void testNoMatchesFound() throws IOException, MojoExecutionException {
            String content = "Content with no links";

            MemoryFileSystemProvider.setContentAsString(file, content);

            mojo.fixSiteAnchors(file, StandardCharsets.UTF_8);

            assertEquals(content, MemoryFileSystemProvider.getContentAsString(file));

            verify(log).info(Messages.fixSiteAnchors.fixingAnchors(file.toString()));
            verifyNoMoreInteractions(log);
        }

        @Test
        void testNoReplacementsNeeded() throws IOException, MojoExecutionException {
            String content = "Content with <a href=\"https://example.org/index.html#anchor\">link</a>"
                    + " and <img src=\"https://example.org/image.png\">";

            MemoryFileSystemProvider.setContentAsString(file, content);

            mojo.fixSiteAnchors(file, StandardCharsets.UTF_8);

            assertEquals(content, MemoryFileSystemProvider.getContentAsString(file));

            verify(log).info(Messages.fixSiteAnchors.fixingAnchors(file.toString()));
            verifyNoMoreInteractions(log);
        }

        @Test
        void testReplacementsNeeded() throws IOException, MojoExecutionException {
            String content = "Content with <a href=\"https://example.org/index.html#anchor.28int.29\">link</a>"
                    + " and <img src=\"https://example.org/image.png?q=.2528\">";

            String expected = "Content with <a href=\"https://example.org/index.html#anchor(int)\">link</a>"
                    + " and <img src=\"https://example.org/image.png?q=%28\">";

            MemoryFileSystemProvider.setContentAsString(file, content);

            mojo.fixSiteAnchors(file, StandardCharsets.UTF_8);

            assertEquals(expected, MemoryFileSystemProvider.getContentAsString(file));

            verify(log).info(Messages.fixSiteAnchors.fixingAnchors(file.toString()));
            verify(log).debug(Messages.fixSiteAnchors.fixedAnchor(file, "https://example.org/index.html#anchor.28int.29",
                    "https://example.org/index.html#anchor(int)"));
            verify(log).debug(Messages.fixSiteAnchors.fixedAnchor(file, "https://example.org/image.png?q=.2528",
                    "https://example.org/image.png?q=%28"));
            verifyNoMoreInteractions(log);
        }

        @Test
        void testFileNotFound() {
            MemoryFileSystemProvider.clear();

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> mojo.fixSiteAnchors(file, StandardCharsets.UTF_8));
            assertInstanceOf(NoSuchFileException.class, exception.getCause());
        }

        @Test
        void testFileNotWritable() throws IOException {
            String content = "Content with no links";

            MemoryFileSystemProvider.setContentAsString(file, content);

            Files.getFileAttributeView(file, MemoryFileAttributeView.class).setReadOnly(true);

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> mojo.fixSiteAnchors(file, StandardCharsets.UTF_8));
            assertInstanceOf(AccessDeniedException.class, exception.getCause());
        }
    }
}
