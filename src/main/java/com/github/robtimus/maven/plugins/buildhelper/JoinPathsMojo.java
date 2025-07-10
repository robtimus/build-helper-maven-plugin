/*
 * JoinPathsMojo.java
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

import java.io.File;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Join paths using the platform-specific path separator and provide the result as a Maven property.
 * <p>
 * While paths for most of the standard Maven plugins can be defined in a way that is platform agnostic, sometimes it's necessary to add flags
 * directly. For instance, when configuring Javadoc's {@code --snippet-path} flag, the value should use the platform-specific path separator.
 * <p>
 *
 *
 * @author Rob Spoor
 */
@Mojo(name = "join-paths", defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = true, threadSafe = true)
public class JoinPathsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * The paths to join.
     *
     * @since 1.0
     */
    @Parameter
    String[] paths;

    /**
     * The name of the resulting Maven property name. This property should not have been set before.
     *
     * @since 1.0
     */
    @Parameter
    String propertyName;

    @Override
    public void execute() throws MojoExecutionException {
        String separator = File.pathSeparator;
        String result = String.join(separator, paths);

        Properties properties = project.getProperties();
        String existingValue = properties.getProperty(propertyName);
        if (existingValue != null && !existingValue.equals(result)) {
            throw new MojoExecutionException(Messages.joinPaths.propertyExists(propertyName, existingValue));
        }
        properties.setProperty(propertyName, result);
    }
}
