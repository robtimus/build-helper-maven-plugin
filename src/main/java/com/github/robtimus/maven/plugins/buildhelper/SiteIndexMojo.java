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
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

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

    private static final String URL_REGEX_QUERY = "(?:(?:\\?|&).*)?"; //$NON-NLS-1$

    private static final Parser MARKDOWN_PARSER = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

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

            String content = IOUtils.toString(input);
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

    String removeProjectUrl(String content, String projectUrl) throws IOException {
        if (StringUtils.isBlank(projectUrl)) {
            return content;
        }

        try (StringReader reader = new StringReader(content)) {
            Node node = MARKDOWN_PARSER.parseReader(reader);

            RemoveProjectUrlVisitor visitor = new RemoveProjectUrlVisitor(content, projectUrl);
            node.accept(visitor);
            visitor.appendTail();
            return visitor.result.toString();
        }
    }

    private final class RemoveProjectUrlVisitor extends AbstractVisitor {

        private final String content;
        private final String projectUrl;
        private final StringBuilder result;

        private int index;

        private RemoveProjectUrlVisitor(String content, String projectUrl) {
            this.content = content;
            this.projectUrl = projectUrl;
            this.result = new StringBuilder(content.length());

            this.index = 0;
        }

        @Override
        public void visit(LinkReferenceDefinition linkReferenceDefinition) {
            super.visit(linkReferenceDefinition);

            String url = linkReferenceDefinition.getDestination();
            if (url.startsWith(projectUrl)) {
                int endOfNode = getEndOfNode(linkReferenceDefinition);
                int startOfUrl = content.lastIndexOf(projectUrl, endOfNode);
                result.append(content, index, startOfUrl);
                index = startOfUrl + projectUrl.length();
            }
        }

        @Override
        public void visit(Link link) {
            super.visit(link);
            processLink(link, link.getDestination());
        }

        @Override
        public void visit(Image image) {
            super.visit(image);
            processLink(image, image.getDestination());
        }

        // If HTML links need to be processed, override visit(HtmlBlock htmlBlock) and/or visit(HtmlInline htmlInline) as well

        private void processLink(Node node, String url) {
            if (url.startsWith(projectUrl)) {
                appendUntilUrl(node);

                getLog().debug(Messages.siteIndex.removedProjectUrl(url, url.substring(projectUrl.length())));
            }
        }

        private void appendUntilUrl(Node node) {
            int endOfNode = getEndOfNode(node);
            int startOfUrl = getStartOfUrl(node);
            if (startOfUrl < endOfNode) {
                result.append(content, index, startOfUrl);
                index = startOfUrl + projectUrl.length();
            }
            // else the URL itself is not part of the node, most likely because the node is a link reference
        }

        private int getStartOfUrl(Node node) {
            Node lastChild = node.getLastChild();
            int endOfLastChild = getEndOfNode(lastChild);
            return content.indexOf(projectUrl, endOfLastChild);
        }

        private int getEndOfNode(Node node) {
            return node
                    .getSourceSpans()
                    .stream()
                    .mapToInt(span -> span.getInputIndex() + span.getLength())
                    .max()
                    .orElseThrow();
        }

        private void appendTail() {
            result.append(content, index, content.length());
            index = content.length();
        }
    }

    String removeBadges(String content) throws IOException {
        try (StringReader reader = new StringReader(content)) {
            Node node = MARKDOWN_PARSER.parseReader(reader);

            RemoveBadgesVisitor visitor = new RemoveBadgesVisitor(content);
            node.accept(visitor);
            visitor.appendTail();
            return visitor.result.toString();
        }
    }

    private final class RemoveBadgesVisitor extends AbstractVisitor {

        private final String content;
        private final StringBuilder result;

        private int index;

        private RemoveBadgesVisitor(String content) {
            this.content = content;
            this.result = new StringBuilder(content.length());

            this.index = 0;
        }

        @Override
        public void visit(Image image) {
            if (isBadgeImage(image)) {
                skipBadge(image);

                String text = extractText(image);
                getLog().debug(Messages.siteIndex.removedBadgeWithoutLink(image.getDestination(), text));
            } else {
                super.visit(image);
            }
        }

        @Override
        public void visit(Link link) {
            Image badgeImage = getSingleBadgeImageChild(link);
            if (badgeImage != null) {
                skipBadge(link);

                String text = extractText(link);
                getLog().debug(Messages.siteIndex.removedBadgeWithLink(badgeImage.getDestination(), text, link.getDestination()));
            } else {
                super.visit(link);
            }
        }

        private boolean isBadgeImage(Image image) {
            return isMatchingBadge(image.getDestination());
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

        private Image getSingleBadgeImageChild(Link link) {
            Node firstChild = link.getFirstChild();
            Node lastChild = link.getLastChild();
            if (firstChild == lastChild && firstChild instanceof Image) {
                Image image = (Image) firstChild;
                return isBadgeImage(image) ? image : null;
            }
            return null;
        }

        private void skipBadge(Node node) {
            int startOfNode = getStartOfNode(node);
            int endOfNode = getEndOfNode(node);
            if (isSingleLine(startOfNode, endOfNode)) {
                skipBadgeLine(startOfNode, endOfNode);
            } else {
                skipBadge(startOfNode, endOfNode);
            }
        }

        private void skipBadgeLine(int startOfNode, int endOfNode) {
            result.append(content, index, startOfNode);
            index = endOfNode;
            if (index < content.length() && content.charAt(index) == '\r') {
                index++;
            }
            if (index < content.length() && content.charAt(index) == '\n') {
                index++;
            }
        }

        private void skipBadge(int startOfNode, int endOfNode) {
            int start = startOfNode > 0 && content.charAt(startOfNode - 1) == ' ' ? startOfNode - 1 : startOfNode;
            result.append(content, index, start);
            index = endOfNode;
        }

        private int getStartOfNode(Node node) {
            return node
                    .getSourceSpans()
                    .stream()
                    .mapToInt(SourceSpan::getInputIndex)
                    .max()
                    .orElseThrow();
        }

        private int getEndOfNode(Node node) {
            return node
                    .getSourceSpans()
                    .stream()
                    .mapToInt(span -> span.getInputIndex() + span.getLength())
                    .max()
                    .orElseThrow();
        }

        private boolean isSingleLine(int startOfNode, int endOfNode) {
            return isStartOfLine(startOfNode) && isEndOfLine(endOfNode);
        }

        private boolean isStartOfLine(int startOfNode) {
            return startOfNode == 0 || isLineBreakChar(content.charAt(startOfNode - 1));
        }

        private boolean isEndOfLine(int endOfNode) {
            return endOfNode == content.length() || isLineBreakChar(content.charAt(endOfNode));
        }

        private boolean isLineBreakChar(char c) {
            return c == '\r' || c == '\n';
        }

        private String extractText(Node node) {
            TextOnlyVisitor visitor = new TextOnlyVisitor();
            node.accept(visitor);
            return visitor.content.toString();
        }

        private void appendTail() {
            result.append(content, index, content.length());
            index = content.length();
        }
    }

    private static final class TextOnlyVisitor extends AbstractVisitor {

        private final StringBuilder content = new StringBuilder();

        @Override
        public void visit(Text text) {
            super.visit(text);
            content.append(text.getLiteral());
        }
    }
}
