package io.github.dmlloyd.im.jira;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Parses JSON from a Jira issue and creates a set of {@linkplain JiraIssue Jira issues}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JiraIssueParser implements AutoCloseable {
    private final Jsonb jsonb;
    private final boolean closeJsonb;

    public JiraIssueParser() {
        this(JsonbBuilder.create(new JsonbConfig().
                setProperty(JsonbConfig.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSSZ")),
                true
        );
    }

    public JiraIssueParser(final Jsonb jsonb) {
        this(jsonb, false);
    }

    private JiraIssueParser(final Jsonb jsonb, final boolean closeJsonb) {
        this.jsonb = jsonb;
        this.closeJsonb = closeJsonb;
    }

    /**
     * Creates a collection of {@link JiraIssue Jira issues} from the path. If the path is a directory, all
     * {@code *.json} files are parsed into Jira issues. If the path is a file, the file must be a JSON file and a
     * set with a single Jira issue is returned.
     *
     * @param path the path to the directory to find files or a specific file to parse
     *
     * @return a set of Jira issues
     *
     * @throws IOException if an error occurs reading a file
     */
    public Set<JiraIssue> parse(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                return stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                        .map(f -> {
                            try (BufferedReader reader = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                                return parse(reader);
                            } catch (IOException e) {
                                throw new RuntimeException(String.format("Failed to process file %s", f), e);
                            }
                        })
                        .collect(Collectors.toSet());
            }
        } else {
            try (InputStream in = Files.newInputStream(path)) {
                return Set.of(parse(in));
            }
        }
    }

    /**
     * Parses the JSON input stream and creates a {@link JiraIssue} from content.
     *
     * @param json the JSON content
     *
     * @return a new Jira issue
     */
    public JiraIssue parse(final InputStream json) {
        return parse(new InputStreamReader(json, StandardCharsets.UTF_8));
    }

    /**
     * Parses the JSON reader and creates a {@link JiraIssue} from content.
     *
     * @param json the JSON content
     *
     * @return a new Jira issue
     */
    public JiraIssue parse(final Reader json) {
        final IssueResult issue = jsonb.fromJson(json, IssueResult.class);
        final IssueField fields = issue.fields();
        return new JiraIssue(fields.description(), issue.key(), fields.summary(), fields.issuetype()
                .name(), null, fields.assignee(), fields.reporter(), fields.created(), fields.updated(), fields.comment()
                .comments());
    }

    @Override
    public void close() throws Exception {
        if (closeJsonb) {
            jsonb.close();
        }
    }

    public record IssueResult(long id, String key, IssueField fields) {
    }

    public record IssueField(
            JiraUser assignee,
            JiraUser creator,
            JiraUser reporter,
            ZonedDateTime created,
            ZonedDateTime updated,
            IssueType issuetype,
            String description,
            String summary,
            IssueComment comment

    ) {

    }

    public record IssueComment(Set<JiraComment> comments) {
    }

    public record IssueType(String id, String name, String description) {
    }
}
