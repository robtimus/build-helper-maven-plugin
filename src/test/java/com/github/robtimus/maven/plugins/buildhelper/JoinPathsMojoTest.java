/*
 * JoinPathsMojoTest.java
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.File;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@SuppressWarnings("nls")
class JoinPathsMojoTest {

    @Test
    @DisplayName("execute")
    void testPropertyNotSet() {
        String path1 = Paths.get("README.md").toAbsolutePath().toString();
        String path2 = Paths.get("pom.xml").toAbsolutePath().toString();
        String separator = File.pathSeparator;

        JoinPathsMojo mojo = new JoinPathsMojo();
        mojo.project = mock(MavenProject.class);
        mojo.paths = new String[] { path1, path2 };
        mojo.propertyName = "joinedPaths";

        Properties properties = new Properties();

        when(mojo.project.getProperties()).thenReturn(properties);

        assertDoesNotThrow(mojo::execute);

        assertEquals(path1 + separator + path2, properties.getProperty(mojo.propertyName));
    }

    @Test
    @DisplayName("execute")
    void testPropertyAlreadySetToEqualValue() {
        String path1 = Paths.get("README.md").toAbsolutePath().toString();
        String path2 = Paths.get("pom.xml").toAbsolutePath().toString();
        String separator = File.pathSeparator;

        JoinPathsMojo mojo = new JoinPathsMojo();
        mojo.project = mock(MavenProject.class);
        mojo.paths = new String[] { path1, path2 };
        mojo.propertyName = "joinedPaths";

        Properties properties = new Properties();
        properties.setProperty(mojo.propertyName, path1 + separator + path2);

        when(mojo.project.getProperties()).thenReturn(properties);

        assertDoesNotThrow(mojo::execute);

        assertEquals(path1 + separator + path2, properties.getProperty(mojo.propertyName));
    }

    @Test
    @DisplayName("execute")
    void testPropertyAlreadySetToDifferentValue() {
        String path1 = Paths.get("README.md").toAbsolutePath().toString();
        String path2 = Paths.get("pom.xml").toAbsolutePath().toString();

        JoinPathsMojo mojo = new JoinPathsMojo();
        mojo.project = mock(MavenProject.class);
        mojo.paths = new String[] { path1, path2 };
        mojo.propertyName = "joinedPaths";

        Properties properties = new Properties();
        properties.setProperty(mojo.propertyName, "existingValue");

        when(mojo.project.getProperties()).thenReturn(properties);

        assertThrows(MojoExecutionException.class, mojo::execute);

        assertEquals("existingValue", properties.getProperty(mojo.propertyName));
    }
}
