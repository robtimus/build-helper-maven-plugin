/*
 * MojoUtils.java
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

import java.nio.file.Files;
import java.nio.file.Path;

final class MojoUtils {

    private MojoUtils() {
    }

    static boolean isMavenProjectFolder(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml")); //$NON-NLS-1$
    }

    static boolean isGitRoot(Path dir) {
        return Files.isDirectory(dir.resolve(".git")); //$NON-NLS-1$
    }
}
