/*
 * JarLicenseMojoTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("nls")
class JarLicenseMojoTest {

    @Test
    @DisplayName("processLicenseFile")
    void testProcessLicenseFile() {
        Path licenseFile = Paths.get(URI.create("memory:/project/LICENSE.txt"));

        JarLicenseMojo mojo = new JarLicenseMojo();
        Log log = mock(Log.class);
        mojo.setLog(log);
        mojo.project = mock(MavenProject.class);

        mojo.processLicenseFile(licenseFile);

        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);

        verify(log).info(Messages.jarLicense.addedResource(licenseFile));
        verify(mojo.project).addResource(resourceCaptor.capture());
        verifyNoMoreInteractions(log, mojo.project);

        Resource resource = resourceCaptor.getValue();
        assertEquals("/project", resource.getDirectory());
        assertEquals("META-INF", resource.getTargetPath());
        assertEquals(Collections.singletonList("LICENSE.txt"), resource.getIncludes());
        assertEquals(Collections.emptyList(), resource.getExcludes());
    }
}
