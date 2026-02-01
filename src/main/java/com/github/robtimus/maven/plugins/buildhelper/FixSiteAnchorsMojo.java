/*
 * FixSiteAnchorsMojo.java
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
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Fixes anchors in URLs in site files.
 * <p>
 * If a Markdown file contains a link to a method's Javadoc, in modern Java versions this link contains {@code (} and {@code )}. Even if these are
 * escaped, the site plugin replaces these with {@code .28} and {@code .29} respectively. This mojo can be used to fix these incorrect replacements.
 *
 * @author Rob Spoor
 * @since 2.0
 */
@Mojo(name = "fix-site-anchors", defaultPhase = LifecyclePhase.SITE, requiresProject = true, threadSafe = true)
public class FixSiteAnchorsMojo extends AbstractMojo {

    private static final Pattern TAG_PATTERN = Pattern.compile("(?:src|href)=\"([^\"]*)\""); // //$NON-NLS-1$

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * The files to replace anchors in.
     *
     * @since 2.0
     */
    @Parameter(required = true)
    File[] files;

    /**
     * The replacements to make. If not specified this is equal to the following:
     * <pre><code>
     * &lt;replacements&gt;
     *   &lt;replacement&gt;
     *     &lt;search&gt;.28&lt;/search&gt;
     *     &lt;replace&gt;(&lt;/replace&gt;
     *   &lt;/replacement&gt;
     *   &lt;replacement&gt;
     *     &lt;search&gt;.29&lt;/search&gt;
     *     &lt;replace&gt;)&lt;/replace&gt;
     *   &lt;/replacement&gt;
     *   &lt;replacement&gt;
     *     &lt;search&gt;.25&lt;/search&gt;
     *     &lt;replace&gt;%&lt;/replace&gt;
     *   &lt;/replacement&gt;
     * &lt;/replacements&gt;
     * </code></pre>
     *
     * @since 2.0
     */
    @Parameter(required = false)
    @SuppressWarnings("nls")
    Replacement[] replacements = {
            new Replacement(".28", "("),
            new Replacement(".29", ")"),
            new Replacement(".25", "%"),
    };

    /**
     * The encoding to use for reading and writing site files.
     *
     * @since 2.0
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    String encoding;

    @Override
    public void execute() throws MojoExecutionException {
        Charset charset = getCharset();
        for (File file : files) {
            fixSiteAnchors(file.toPath(), charset);
        }
    }

    Charset getCharset() {
        if (encoding == null) {
            Charset defaultCharset = Charset.defaultCharset();
            getLog().warn(Messages.fixSiteAnchors.noEncoding(defaultCharset));
            return defaultCharset;
        }
        return Charset.forName(encoding);
    }

    void fixSiteAnchors(Path path, Charset charset) throws MojoExecutionException {
        String filePath = path.toString();

        getLog().info(Messages.fixSiteAnchors.fixingAnchors(filePath));

        String content = readContent(path, charset);
        content = fixSiteAnchors(filePath, content);
        writeContent(path, content, charset);
    }

    private String readContent(Path file, Charset charset) throws MojoExecutionException {
        try (Reader reader = Files.newBufferedReader(file, charset)) {
            return IOUtils.toString(reader);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void writeContent(Path file, String content, Charset charset) throws MojoExecutionException {
        try (Writer writer = Files.newBufferedWriter(file, charset)) {
            writer.write(content);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private String fixSiteAnchors(String filePath, String content) {
        Matcher matcher = TAG_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            String url = matcher.group(1);

            result.append(content, index, start);

            String fixedUrl = fixSiteAnchorsInUrl(url);

            result.append(fixedUrl);

            if (!url.equals(fixedUrl)) {
                getLog().debug(Messages.fixSiteAnchors.fixedAnchor(filePath, url, fixedUrl));
            }

            index = end;
        }
        result.append(content, index, content.length());
        return result.toString();
    }

    private String fixSiteAnchorsInUrl(String url) {
        String result = url;
        for (Replacement replacement : replacements) {
            result = result.replace(replacement.search, replacement.replace);
        }
        return result;
    }

    /**
     * A single replacement.
     *
     * @author Rob Spoor
     * @since 2.0
     */
    public static final class Replacement {

        private String search;
        private String replace;

        /**
         * Creates a new replacement.
         */
        public Replacement() {
        }

        Replacement(String search, String replace) {
            this();
            setSearch(search);
            setReplace(replace);
        }

        /**
         * Returns the string to search for.
         *
         * @return The string to search for.
         */
        public String getSearch() {
            return search;
        }

        /**
         * Sets the string to search for.
         *
         * @param search The string to search for.
         */
        public void setSearch(String search) {
            this.search = search;
        }

        /**
         * Returns the string to replace with.
         *
         * @return The string to replace with.
         */
        public String getReplace() {
            return replace;
        }

        /**
         * Sets the string to replace with.
         *
         * @param replace The string to replace with.
         */
        public void setReplace(String replace) {
            this.replace = replace;
        }

        @Override
        @SuppressWarnings("nls")
        public String toString() {
            return search + " => " + replace;
        }
    }
}
