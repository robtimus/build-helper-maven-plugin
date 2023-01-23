/*
 * SiteIndexMojoTest.java
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.github.robtimus.filesystems.memory.MemoryFileAttributeView;
import com.github.robtimus.filesystems.memory.MemoryFileSystemProvider;
import com.github.robtimus.junit.support.extension.testresource.EOL;
import com.github.robtimus.junit.support.extension.testresource.Encoding;
import com.github.robtimus.junit.support.extension.testresource.TestResource;

@SuppressWarnings("nls")
class SiteIndexMojoTest {

    @Nested
    @DisplayName("generateSiteIndex")
    class GenerateSiteIndex {

        @Test
        @DisplayName("success")
        void testSuccess(@TestResource("site-index-input.md") @EOL(EOL.LF) @Encoding("UTF-8") String input,
                @TestResource("site-index-expected.md") @EOL(EOL.LF) @Encoding("UTF-8") String expected) {

            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.project = mock(MavenProject.class);
            mojo.siteIndexTitle = "Overview";
            mojo.badgePatterns = new String[] {
                    "https://github.com/.*/badge.svg",
                    "https://img.shields.io/.*?",
                    "https://snyk.io/test/.*/badge.svg",
                    "https://sonarcloud.io/api/project_badges/.*",
            };
            mojo.encoding = "UTF-8";

            when(mojo.project.getUrl()).thenReturn("https://robtimus.github.io/build-helper-maven-plugin/");

            Path sourceFile = Paths.get(URI.create("memory:/README.md"));
            Path targetFile = Paths.get(URI.create("memory:/src/site/markdown/index.md"));

            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> {
                MemoryFileSystemProvider.setContent(sourceFile, input.getBytes());
                mojo.generateSiteIndex(sourceFile, targetFile);
            });

            String output = assertDoesNotThrow(() -> new String(MemoryFileSystemProvider.getContent(targetFile)));

            assertEquals(expected, output);

            verify(mojo.project).getUrl();
            verify(log).debug(Messages.siteIndex.removedProjectUrl(
                    "https://robtimus.github.io/build-helper-maven-plugin/plugin-info.html",
                    "plugin-info.html"));
            verify(log).debug(Messages.siteIndex.removedProjectUrl(
                    "https://robtimus.github.io/build-helper-maven-plugin/team.html",
                    "team.html"));
            verify(log).debug(Messages.siteIndex.removedProjectUrl(
                    "https://robtimus.github.io/build-helper-maven-plugin/licenses.html",
                    "licenses.html"));

            // Header
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://img.shields.io/maven-central/v/com.github.robtimus/build-helper-maven-plugin",
                    "Maven Central",
                    "https://search.maven.org/artifact/com.github.robtimus/build-helper-maven-plugin"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml/badge.svg",
                    "Build Status",
                    "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg",
                    "Known Vulnerabilities",
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=alert_status",
                    "Quality Gate Status"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=coverage",
                    "Coverage"));

            // Badges in lists
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://img.shields.io/maven-central/v/com.github.robtimus/build-helper-maven-plugin",
                    "Maven Central"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml/badge.svg",
                    "Build Status"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg",
                    "Known Vulnerabilities"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=alert_status",
                    "Quality Gate Status",
                    "https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Abuild-helper-maven-plugin"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=coverage",
                    "Coverage",
                    "https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Abuild-helper-maven-plugin"));

            // Additional
            verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg?targetFile=pom.xml",
                    "Known Vulnerabilities",
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin?targetFile=pom.xml"));
            verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                    "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg?targetFile=pom.xml",
                    "Known Vulnerabilities"));

            verify(log).info(Messages.siteIndex.generated(sourceFile, targetFile));

            verifyNoMoreInteractions(log, mojo.project);
        }

        @Test
        @DisplayName("target directory create error")
        void testTargetDirectoryCreateError(@TestResource("site-index-input.md") @EOL(EOL.LF) @Encoding("UTF-8") String input) {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.project = mock(MavenProject.class);
            mojo.encoding = "UTF-8";

            Path sourceFile = Paths.get(URI.create("memory:/README.md"));
            Path targetFile = Paths.get(URI.create("memory:/src/site/markdown/index.md"));
            Path siteDir = Paths.get(URI.create("memory:/src/site"));

            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> {
                MemoryFileSystemProvider.setContent(sourceFile, input.getBytes());

                Files.createDirectories(siteDir);
                Files.getFileAttributeView(siteDir, MemoryFileAttributeView.class).setReadOnly(true);
            });

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> mojo.generateSiteIndex(sourceFile, targetFile));
            AccessDeniedException cause = assertInstanceOf(AccessDeniedException.class, exception.getCause());
            assertEquals(cause.getMessage(), exception.getMessage());
            assertEquals(siteDir.toString(), cause.getFile());

            verifyNoInteractions(log, mojo.project);
        }

        @Test
        @DisplayName("target file write error")
        void testTargetFileWriteError(@TestResource("site-index-input.md") @EOL(EOL.LF) @Encoding("UTF-8") String input) {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.project = mock(MavenProject.class);
            mojo.encoding = "UTF-8";

            Path sourceFile = Paths.get(URI.create("memory:/README.md"));
            Path targetFile = Paths.get(URI.create("memory:/src/site/markdown/index.md"));

            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> {
                MemoryFileSystemProvider.setContent(sourceFile, input.getBytes());

                Files.createDirectories(targetFile.getParent());
                Files.createFile(targetFile);
                Files.getFileAttributeView(targetFile, MemoryFileAttributeView.class).setReadOnly(true);
            });

            MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> mojo.generateSiteIndex(sourceFile, targetFile));
            AccessDeniedException cause = assertInstanceOf(AccessDeniedException.class, exception.getCause());
            assertEquals(cause.getMessage(), exception.getMessage());
            assertEquals(targetFile.toString(), cause.getFile());

            verifyNoInteractions(log, mojo.project);
        }

        @Test
        @DisplayName("skipped")
        void testSkipped() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);
            mojo.skipSiteIndex = true;

            Path sourceFile = Paths.get(URI.create("memory:/README.md"));
            Path targetFile = Paths.get(URI.create("memory:/src/site/markdown/index.md"));

            MemoryFileSystemProvider.clear();

            assertDoesNotThrow(() -> mojo.generateSiteIndex(sourceFile, targetFile));

            assertFalse(Files.isRegularFile(targetFile));

            verify(log).info(Messages.siteIndex.skipped());

            verifyNoMoreInteractions(log);
        }
    }

    @Nested
    @DisplayName("getCharset")
    class GetCharset {

        @Test
        void testGetInputCharsetNotSet() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            Charset defaultCharset = Charset.defaultCharset();

            assertEquals(defaultCharset, mojo.getCharset());

            verify(log).warn(Messages.siteIndex.noEncoding(defaultCharset));
        }

        @Test
        void testGetInputCharsetSet() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            mojo.encoding = "UTF-8";
            assertEquals(StandardCharsets.UTF_8, mojo.getCharset());

            verify(log, never()).warn(any(CharSequence.class));

            mojo.encoding = "US-ASCII";
            assertEquals(StandardCharsets.US_ASCII, mojo.getCharset());

            verify(log, never()).warn(any(CharSequence.class));
        }
    }

    @Nested
    @DisplayName("removeProjectUrl")
    class RemoveProjectUrl {

        private String projectUrl = "https://robtimus.github.io/build-helper-maven-plugin/";
        private String content = "A plugin that contains several utility [goals](" + projectUrl + "plugin-info.html).";

        @Test
        @DisplayName("blank projectUrl")
        void testBlankProjectUrl() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            assertEquals(content, mojo.removeProjectUrl(content, null));
            assertEquals(content, mojo.removeProjectUrl(content, ""));
            assertEquals(content, mojo.removeProjectUrl(content, " "));

            verifyNoInteractions(log);
        }

        @Test
        @DisplayName("matching projectUrl")
        void testMatchingProjectUrl() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            String expected = content.replace(projectUrl, "");
            assertEquals(expected, mojo.removeProjectUrl(content, projectUrl).toString());

            verify(log).debug(Messages.siteIndex.removedProjectUrl(projectUrl + "plugin-info.html", "plugin-info.html"));
            verifyNoMoreInteractions(log);
        }

        @Test
        @DisplayName("non-matching projectUrl")
        void testNonMatchingProjectUrl() {
            SiteIndexMojo mojo = new SiteIndexMojo();
            Log log = mock(Log.class);
            mojo.setLog(log);

            assertEquals(content, mojo.removeProjectUrl(content, "https://robtimus.github.io/other/").toString());

            verifyNoInteractions(log);
        }
    }

    @Test
    @DisplayName("removeBadges")
    void testRemoveBadges(@TestResource("site-index-input.md") @EOL(EOL.LF) @Encoding("UTF-8") String input,
            @TestResource("site-index-expected.badges-only.md") @EOL(EOL.LF) @Encoding("UTF-8") String expected) {

        SiteIndexMojo mojo = new SiteIndexMojo();
        Log log = mock(Log.class);
        mojo.setLog(log);
        mojo.badgePatterns = new String[] {
                "https://github.com/.*/badge.svg",
                "https://img.shields.io/.*?",
                "https://snyk.io/test/.*/badge.svg",
                "https://sonarcloud.io/api/project_badges/.*",
        };

        assertEquals(expected, mojo.removeBadges(input).toString());

        // Header
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://img.shields.io/maven-central/v/com.github.robtimus/build-helper-maven-plugin",
                "Maven Central",
                "https://search.maven.org/artifact/com.github.robtimus/build-helper-maven-plugin"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml/badge.svg",
                "Build Status",
                "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg",
                "Known Vulnerabilities",
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=alert_status",
                "Quality Gate Status"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=coverage",
                "Coverage"));

        // Badges in lists
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://img.shields.io/maven-central/v/com.github.robtimus/build-helper-maven-plugin",
                "Maven Central"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://github.com/robtimus/build-helper-maven-plugin/actions/workflows/build.yml/badge.svg",
                "Build Status"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg",
                "Known Vulnerabilities"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=alert_status",
                "Quality Gate Status",
                "https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Abuild-helper-maven-plugin"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://sonarcloud.io/api/project_badges/measure?project=com.github.robtimus%3Abuild-helper-maven-plugin&metric=coverage",
                "Coverage",
                "https://sonarcloud.io/summary/overall?id=com.github.robtimus%3Abuild-helper-maven-plugin"));

        // Additional
        verify(log).debug(Messages.siteIndex.removedBadgeWithLink(
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg?targetFile=pom.xml",
                "Known Vulnerabilities",
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin?targetFile=pom.xml"));
        verify(log).debug(Messages.siteIndex.removedBadgeWithoutLink(
                "https://snyk.io/test/github/robtimus/build-helper-maven-plugin/badge.svg?targetFile=pom.xml",
                "Known Vulnerabilities"));

        verifyNoMoreInteractions(log);
    }
}
