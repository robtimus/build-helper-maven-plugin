/*
 * SiteIndexMojo.java
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

import static com.github.robtimus.maven.plugins.buildhelper.MojoUtils.getProjectRoot;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generate a Markdown site index based on another Markdown file. This goal will provide the following transformations:
 * <ul>
 *   <li>Add an HTML title.</li>
 *   <li>Make any link that starts with the project's URL relative to that URL.</li>
 *   <li>Remove any badges that match any of a set of provided patterns. Badges will be removed if:
 *     <ul>
 *       <li>They are preceded by a space. In this case, the leading space is removed as well.</li>
 *       <li>They are on a line of their own. In this case, the entire line is removed.</li>
 *     </ul>
 * </ul>
 *
 * @author Rob Spoor
 */
@Mojo(name = "site-index", defaultPhase = LifecyclePhase.PRE_SITE, requiresProject = true, threadSafe = true)
public class SiteIndexMojo extends AbstractMojo {

    private static final String LINE_START_REGEX = "(?<=^|\n)"; //$NON-NLS-1$
    private static final String TEXT_REGEX = "\\[(?<text>[^\\]]*)\\]"; //$NON-NLS-1$
    private static final String URL_REGEX_PREFIX = "\\((?<url>"; //$NON-NLS-1$
    private static final String URL_REGEX_QUERY = "(?:(?:\\?|&)[^)]*)?"; //$NON-NLS-1$
    private static final String URL_REGEX_POSTFIX = ")\\)"; //$NON-NLS-1$
    private static final String URL_REGEX = URL_REGEX_PREFIX + "[^)]*" + URL_REGEX_POSTFIX; //$NON-NLS-1$
    private static final String LINK_REGEX = "\\((?<link>[^)]*)\\)"; //$NON-NLS-1$
    private static final String BADGE_REGEX_WITHOUT_LINK = "!" + TEXT_REGEX + URL_REGEX; //$NON-NLS-1$
    private static final String BADGE_REGEX_WITH_LINK = "\\[" + BADGE_REGEX_WITHOUT_LINK + "\\]" + LINK_REGEX; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String TEXT_GROUP = "text"; //$NON-NLS-1$
    private static final String URL_GROUP = "url"; //$NON-NLS-1$
    private static final String LINK_GROUP = "link"; //$NON-NLS-1$

    @Parameter(defaultValue = "${project}", readonly = true)
    MavenProject project;

    /**
     * The source Markdown file.
     *
     * 1.0
     */
    @Parameter(defaultValue = "${project.basedir}/README.md", required = true)
    File sourceFile;

    /**
     * The Markdown site index to generate.
     *
     * @since 1.0
     */
    @Parameter(defaultValue = "${project.basedir}/src/site/markdown/index.md", required = true)
    File targetFile;

    /**
     * The HTML title to add.
     *
     * @since 1.0
     */
    @Parameter(defaultValue = "Overview", required = true)
    String siteIndexTitle;

    /**
     * A set of patterns to use to recognize badges, based on the URL.
     *
     * @since 1.0
     */
    @Parameter
    String[] badgePatterns;

    /**
     * The encoding to use for reading and writing the site index.
     *
     * @since 1.0
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    String encoding;

    /**
     * Set this to {@code true} to skip generating a Markdown site index.
     *
     * @since 1.0
     */
    @Parameter(property = "robtimus.site-index.skip", defaultValue = "false")
    boolean skipSiteIndex;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        generateSiteIndex(project.getBasedir().toPath(), sourceFile.toPath(), targetFile.toPath());
    }

    void generateSiteIndex(Path baseDir, Path sourceFile, Path targetFile) throws MojoExecutionException, MojoFailureException {
        if (skipSiteIndex) {
            getLog().info(Messages.siteIndex.skipped());
            return;
        }

        Path projectRoot = getProjectRoot(baseDir);
        getLog().debug(Messages.siteIndex.projectRoot(projectRoot));
        if (!sourceFile.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new MojoFailureException(Messages.siteIndex.sourceOutsideProject());
        }
        if (!targetFile.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new MojoFailureException(Messages.siteIndex.targetOutsideProject());
        }

        Charset charset = getCharset();
        createTargetDir(targetFile);
        try (Reader input = Files.newBufferedReader(sourceFile, charset);
                Writer output = Files.newBufferedWriter(targetFile, charset)) {

            writeHeader(output);

            CharSequence content = IOUtils.toString(input);
            content = removeProjectUrl(content, project.getUrl());
            content = removeBadges(content);
            output.append(content);

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        getLog().info(Messages.siteIndex.generated(targetFile, sourceFile));
    }

    Charset getCharset() {
        if (encoding == null) {
            Charset defaultCharset = Charset.defaultCharset();
            getLog().warn(Messages.siteIndex.noEncoding(defaultCharset));
            return defaultCharset;
        }
        return Charset.forName(encoding);
    }

    private void createTargetDir(Path targetFile) throws MojoExecutionException {
        try {
            Files.createDirectories(targetFile.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("nls")
    private void writeHeader(Writer output) throws IOException {
        output.append("<head>\n");
        output.append("  <title>").append(siteIndexTitle).append("</title>\n");
        output.append("</head>\n");
        output.append('\n');
    }

    @SuppressWarnings("nls")
    CharSequence removeProjectUrl(CharSequence content, String projectUrl) {
        if (StringUtils.isBlank(projectUrl)) {
            return content;
        }

        final String relativeGroup = "relative";
        String regex = TEXT_REGEX + URL_REGEX_PREFIX + Pattern.quote(projectUrl) + "(?<relative>[^)]*)" + URL_REGEX_POSTFIX;

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        StringBuilder result = new StringBuilder(content.length());
        int index = 0;
        while (matcher.find()) {
            String url = matcher.group(URL_GROUP);
            String relative = matcher.group("relative");
            int end = matcher.end();

            result.append(content, index, matcher.start(URL_GROUP));
            result.append(content, matcher.start(relativeGroup), end);

            index = end;

            getLog().debug(Messages.siteIndex.removedProjectUrl(url, relative));
        }
        result.append(content, index, content.length());
        return result;
    }

    CharSequence removeBadges(CharSequence content) {
        CharSequence result = content;
        result = removeBadgesInLinesWithLink(result);
        result = removeBadgesInLinesWithoutLink(result);
        result = removeBadgeLinesWithLink(result);
        result = removeBadgeLinesWithoutLink(result);
        return result;
    }

    @SuppressWarnings("nls")
    CharSequence removeBadgesInLinesWithLink(CharSequence content) {
        return removeBadgesWithLink(content, " " + BADGE_REGEX_WITH_LINK);
    }

    @SuppressWarnings("nls")
    CharSequence removeBadgeLinesWithLink(CharSequence content) {
        return removeBadgesWithLink(content, LINE_START_REGEX + BADGE_REGEX_WITH_LINK + "\r?\n");
    }

    private CharSequence removeBadgesWithLink(CharSequence content, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        StringBuilder result = new StringBuilder(content.length());
        int index = 0;
        while (matcher.find()) {
            String text = matcher.group(TEXT_GROUP);
            String url = matcher.group(URL_GROUP);
            String link = matcher.group(LINK_GROUP);

            if (isMatchingBadge(url)) {
                result.append(content, index, matcher.start());
                getLog().debug(Messages.siteIndex.removedBadgeWithLink(url, text, link));
            } else {
                result.append(content, index, matcher.end());
            }
            index = matcher.end();
        }
        result.append(content, index, content.length());
        return result;
    }

    @SuppressWarnings("nls")
    CharSequence removeBadgesInLinesWithoutLink(CharSequence content) {
        return removeBadgesWithoutLink(content, " " + BADGE_REGEX_WITHOUT_LINK);
    }

    @SuppressWarnings("nls")
    CharSequence removeBadgeLinesWithoutLink(CharSequence content) {
        return removeBadgesWithoutLink(content, LINE_START_REGEX + BADGE_REGEX_WITHOUT_LINK + "\r?\n");
    }

    private CharSequence removeBadgesWithoutLink(CharSequence content, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(content);

        StringBuilder result = new StringBuilder(content.length());
        int index = 0;
        while (matcher.find()) {
            String text = matcher.group(TEXT_GROUP);
            String url = matcher.group(URL_GROUP);

            if (isMatchingBadge(url)) {
                result.append(content, index, matcher.start());
                getLog().debug(Messages.siteIndex.removedBadgeWithoutLink(url, text));
            } else {
                result.append(content, index, matcher.end());
            }
            index = matcher.end();
        }
        result.append(content, index, content.length());
        return result;
    }

    private boolean isMatchingBadge(String url) {
        for (String badgePattern : badgePatterns) {
            if (isMatchingBadge(url, badgePattern)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("nls")
    private boolean isMatchingBadge(String url, String badgePattern) {
        String regex = "^" + badgePattern + URL_REGEX_QUERY;
        return url.matches(regex);
    }
}
