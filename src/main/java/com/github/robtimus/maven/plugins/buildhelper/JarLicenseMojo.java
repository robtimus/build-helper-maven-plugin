/*
 * JarLicenseMojo.java
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

import java.nio.file.Path;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Add a license file to created JAR files and, if applicable, source JAR files.
 * <p>
 * License files are first resolved relative to the current directory. If it cannot be found there, parent directories are checked until one of the
 * following occurs:
 * <ul>
 *   <li>The parent directory is not part of the Maven project (does not contain a {@code pom.xml} file), and is not the root directory of the current
 *       Git project.</li>
 *   <li>The maximum number of parent directories has been reached.</li>
 * </ul>
 *
 * @author Rob Spoor
 */
@Mojo(name = "jar-license", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresProject = true, threadSafe = true)
public class JarLicenseMojo extends AbstractLicenseMojo {

    @Override
    void processLicenseFile(Path licenseFile) {
        Resource licenseResource = new Resource();

        licenseResource.setDirectory(licenseFile.getParent().toString());
        licenseResource.addInclude(licenseFile.getFileName().toString());
        licenseResource.setTargetPath("META-INF"); //$NON-NLS-1$

        project.addResource(licenseResource);

        getLog().info(Messages.jarLicense.addedResource(licenseFile));
    }
}
