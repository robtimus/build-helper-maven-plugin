/*
 * AbstractLicenseMojo.java
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

import static com.github.robtimus.maven.plugins.buildhelper.MojoUtils.isGitRoot;
import static com.github.robtimus.maven.plugins.buildhelper.MojoUtils.isMavenProjectFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

abstract class AbstractLicenseMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * The name of the license file.
     *
     * @since 1.0
     */
    @Parameter(defaultValue = "LICENSE.txt", required = true)
    String licenseFilename;

    /**
     * The maximum number of parent directories to check. This allows locating the license file to abort early if it could not be found.
     *
     * @since 1.0
     */
    @Parameter(defaultValue = "0", required = true)
    private int maxParentCount;

    /**
     * Set this to {@code true} to skip adding licenses to JAR files.
     *
     * @since 1.0
     */
    @Parameter(property = "robtimus.license.skip", defaultValue = "false")
    boolean skipLicense;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipLicense) {
            getLog().info(Messages.license.skipped());
            return;
        }

        Path licenseFile = findLicenseFile(project.getBasedir().toPath(), licenseFilename, maxParentCount, getLog());
        processLicenseFile(licenseFile);
    }

    abstract void processLicenseFile(Path licenseFile) throws MojoExecutionException, MojoFailureException;

    static Path findLicenseFile(Path baseDir, String filename, int maxParentCount, Log log) throws MojoFailureException {
        Path dir = baseDir.toAbsolutePath().normalize();

        Path candidate = dir.resolve(filename).normalize();
        if (!candidate.startsWith(dir)) {
            // Trying to escape the project, not allowed
            throw new MojoFailureException(Messages.license.invalidFilename(filename));
        }
        if (Files.isRegularFile(candidate)) {
            log.debug(Messages.license.fileFound(candidate));
            return candidate;
        }

        for (int i = 0; i < maxParentCount; i++) {
            abortIfGitRoot(dir);

            dir = dir.getParent();
            if (!isMavenProjectFolder(dir) && !isGitRoot(dir)) {
                throw new MojoFailureException(Messages.license.leavingMavenProject());
            }

            candidate = dir.resolve(filename).normalize();
            if (Files.isRegularFile(candidate)) {
                log.debug(Messages.license.fileFound(candidate));
                return candidate;
            }
        }

        throw new MojoFailureException(Messages.license.fileNotFound(filename, dir));
    }

    private static void abortIfGitRoot(Path dir) throws MojoFailureException {
        if (isGitRoot(dir)) {
            throw new MojoFailureException(Messages.license.leavingGitProject());
        }
    }
}
