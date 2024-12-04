package io.github.dmlloyd.im;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Stuff for dealing with GitHub.
 */
public final class GitHub {
    private GitHub() {}

    /**
     * Used to construct the issue creation request.
     */
    public static final class IssueCreationFactory {
        private final URI jiraUrl;
        private final Map<String, String> userMapping;

        public IssueCreationFactory(final URI jiraUrl, final Map<String, String> userMapping) {
            this.jiraUrl = fixJiraUri(jiraUrl);
            this.userMapping = userMapping;
        }

        public JsonObject issueCreateRequest(Issue issue) {
            JsonObjectBuilder root = Json.createObjectBuilder();
            root.add("title", issue.summary());
            // todo: transform user references
            StringBuilder mdDesc = new StringBuilder(1024);
            mdDesc.append("This issue was imported from JIRA. The original issue was: ");
            mdDesc.append('[').append(issue.key()).append(']').append('(').append(jiraUrl).append("browse/").append(issue.key()).append(')').append("\n\n");
            mdDesc.append(FlexmarkHtmlConverter.builder().build().convert(issue.description()));
            mdDesc.append("\n\nOriginal issue creation date: ").append(issue.created());
            mdDesc.append("\nOriginal issue updated date: ").append(issue.updated());
            if (issue.resolved() != null) {
                mdDesc.append("\nOriginal issue resolved date: ").append(issue.resolved());
            }
            root.add("body", mdDesc.toString());
            // todo: milestone
            // todo: labels
            // todo: assignees (array)
            return root.build();
        }
    }

    /**
     * Used to construct comment creation request.
     */
    public static final class CommentCreationFactory {
        private final URI jiraUrl;
        private final Map<String, String> userMapping;
        private final Map<String, Integer> mappedIssueNumbers;

        public CommentCreationFactory(final URI jiraUrl, final Map<String, String> userMapping, final Map<String, Integer> mappedIssueNumbers) {
            this.jiraUrl = jiraUrl;
            this.userMapping = userMapping;
            this.mappedIssueNumbers = mappedIssueNumbers;
        }

        public JsonObject commentCreateRequest(Issue issue, Comment comment) {
            JsonObjectBuilder root = Json.createObjectBuilder();
            StringBuilder mdDesc = new StringBuilder(1024);
            mdDesc.append("This comment was imported from JIRA.\n\n");
            // todo: comment link
            mdDesc.append(FlexmarkHtmlConverter.builder().build().convert(comment.body()));
            mdDesc.append("\n\nOriginal comment creation date: ").append(comment.created());
            root.add("body", remapIssueKeys(mdDesc.toString(), mappedIssueNumbers));
            return root.build();
        }
    }

    /**
     * Used in second pass to update issue body with corrected issue links, and set the issue status.
     */
    public static final class IssueUpdateFactory {
        private final URI jiraUrl;
        private final Map<String, Integer> mappedIssueNumbers;

        public IssueUpdateFactory(final URI jiraUrl, final Map<String, Integer> mappedIssueNumbers) {
            this.jiraUrl = fixJiraUri(jiraUrl);
            this.mappedIssueNumbers = mappedIssueNumbers;
        }

        public JsonObject issueUpdateRequest(Issue issue, String originalBody) {
            JsonObjectBuilder root = Json.createObjectBuilder();
            Integer mapped = mappedIssueNumbers.get(issue.key());
            if (mapped == null) {
                throw new IllegalArgumentException("The issue key " + issue.key() + " was not mapped");
            }
            // this is pretty janky
            root.add("body", remapIssueKeys(originalBody, mappedIssueNumbers));
            // todo: issue status
            // root.add("state", "open"|"closed");
            // root.add("state_reason", "completed"|"not_planned"|"reopened");
            return root.build();
        }
    }

    /**
     * HTTP client for dealing with GitHub.
     */
    public static final class Client {
        private final HttpClient client;
        private final URI jiraUrl;
        private final String owner;
        private final String repo;
        private final Map<String, String> userMapping;
        private final Map<String, String> tokens;
        private final String defaultToken;

        public Client(final HttpClient client, final URI jiraUrl, final String owner, final String repo, final Map<String, String> userMapping, final Map<String, String> tokens, final String defaultToken) {
            this.client = client;
            this.jiraUrl = fixJiraUri(jiraUrl);
            this.owner = owner;
            this.repo = repo;
            this.userMapping = userMapping;
            this.tokens = tokens;
            this.defaultToken = defaultToken;
        }

        /**
         * Create the issue.
         *
         * @param issue the issue
         * @return the new issue number
         * @throws IOException if there was an error
         */
        public int createIssue(Issue issue) throws IOException {
            IssueCreationFactory icf = new IssueCreationFactory(jiraUrl, userMapping);
            JsonObject req = icf.issueCreateRequest(issue);
            final String createdByJira = "<todo>";
            final String token;
            String createdBy = userMapping.get(createdByJira);
            if (createdBy != null) {
                token = tokens.getOrDefault(createdBy, defaultToken);
            } else {
                token = defaultToken;
            }
            final HttpRequest request = HttpRequest.newBuilder()
                .setHeader("Accept", "application/vnd.github+json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/issues"))
                .build();
            try {
                HttpResponse<JsonObject> response = client.send(request, ri -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), str -> Json.createReader(new StringReader(str)).readObject()));
                JsonObject body = response.body();
                if (response.statusCode() == 201) {
                    return body.getInt("number");
                } else {
                    throw new IOException("Failed with status " + response.statusCode() + ": " + body.getString("message", "<no message>"));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted");
            }
        }

        public void createComment(Issue issue, Comment comment, final Map<String, Integer> mappedIssueNumbers) throws IOException {
            Integer issueNum = mappedIssueNumbers.get(issue.key());
            if (issueNum == null) {
                throw new IllegalArgumentException("Issue " + issue.key() + " was not mapped");
            }
            CommentCreationFactory ccf = new CommentCreationFactory(jiraUrl, userMapping, mappedIssueNumbers);
            JsonObject req = ccf.commentCreateRequest(issue, comment);
            final String createdByJira = "<todo>";
            final String token;
            String createdBy = userMapping.get(createdByJira);
            if (createdBy != null) {
                token = tokens.getOrDefault(createdBy, defaultToken);
            } else {
                token = defaultToken;
            }
            final HttpRequest request = HttpRequest.newBuilder()
                .setHeader("Accept", "application/vnd.github+json")
                .setHeader("Authorization", "Bearer " + token)
                .setHeader("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(req.toString()))
                .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/issues/" + issueNum + "/comments"))
                .build();
            try {
                HttpResponse<JsonObject> response = client.send(request, ri -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), str -> Json.createReader(new StringReader(str)).readObject()));
                JsonObject body = response.body();
                if (response.statusCode() == 201) {
                    return;
                } else {
                    throw new IOException("Failed with status " + response.statusCode() + ": " + body.getString("message", "<no message>"));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpectedly interrupted");
            }
        }
    }

    private static @NotNull String remapIssueKeys(final String originalBody, final Map<String, Integer> mappedIssueNumbers) {
        StringBuilder newBody = new StringBuilder(originalBody.length());
        Pattern pattern = Pattern.compile("(?:https?://[a-zA-Z0-9./]+)?([A-Z0-9]+-\\d+)(?:\\\\?\\S+)?");
        Matcher matcher = pattern.matcher(originalBody);
        while (matcher.find()) {
            String key = matcher.group(1);
            Integer id = mappedIssueNumbers.get(key);
            if (id != null) {
                matcher.appendReplacement(newBody, "#" + id);
            } else {
                // didn't match, just leave it alone
                matcher.appendReplacement(newBody, matcher.group(0));
            }
        }
        matcher.appendTail(newBody);
        return newBody.toString();
    }

    private static URI fixJiraUri(final URI jiraUrl) {
        if (! jiraUrl.isAbsolute()) {
            throw new IllegalArgumentException("JIRA URL has to be absolute (" + jiraUrl + ")");
        }
        String origPath = Objects.requireNonNullElse(jiraUrl.getPath(), "");
        if (origPath.endsWith("/")) {
            return jiraUrl;
        } else {
            return jiraUrl.resolve(origPath + "/");
        }
    }

}
