package io.github.dmlloyd.im.jira;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Options to fetch Jira issue data.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JiraIssueFetcher implements AutoCloseable {

    private final HttpClient client;
    private final Jsonb jsonb;
    private final String projectId;
    private final String baseUri;
    private final JiraIssueParser parser;

    /**
     * Creates a new issue fetcher.
     *
     * @param projectId the Jira project id
     * @param baseUri   the base URI for the Jira instance
     */
    public JiraIssueFetcher(final String projectId, final String baseUri) {
        client = HttpClient.newHttpClient();
        jsonb = JsonbBuilder.create(new JsonbConfig().
                setProperty(JsonbConfig.DATE_FORMAT, "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        this.projectId = projectId;
        this.baseUri = baseUri;
        this.parser = new JiraIssueParser(jsonb);
    }

    /**
     * Retrieves the Jira issues and holds them in memory.
     *
     * @return a future which completes when all the issues have been processed
     */
    public CompletableFuture<Set<JiraIssue>> fetch() {
        final CompletableFuture<Set<JiraIssue>> cf = new CompletableFuture<>();
        getIssues().whenComplete((result, ex) -> {
            if (ex != null) {
                cf.completeExceptionally(ex);
            } else {
                final Set<JiraIssue> processed = new LinkedHashSet<>();
                for (IssueId issue : result.issues()) {
                    final HttpRequest issueRequest = HttpRequest.newBuilder(createIssueUri(issue.key()))
                            .header("Accept", "application/json")
                            .GET()
                            .build();
                    try {
                        final var issueResponse = client.send(issueRequest, HttpResponse.BodyHandlers.ofInputStream());
                        if (issueResponse.statusCode() != 200) {
                            cf.completeExceptionally(new RuntimeException(String.format("Failed fetch issue %s from %s ", issue.key(), issueRequest.uri())));
                            return;
                        }
                        processed.add(parser.parse(issueResponse.body()));
                    } catch (IOException | InterruptedException e) {
                        cf.completeExceptionally(e);
                        return;
                    }
                }
                cf.complete(Set.copyOf(processed));

            }
        });
        return cf;
    }

    /**
     * Downloads the Jira issue data in JSON format to the directory.
     *
     * @param dir the directory to download the JSON files to
     *
     * @return a future which completes when all the files have been downloaded
     */
    public CompletableFuture<Set<Path>> download(final Path dir) {
        final CompletableFuture<Set<Path>> cf = new CompletableFuture<>();
        getIssues().whenComplete((result, ex) -> {
            final Set<Path> downloaded = new LinkedHashSet<>();
            for (IssueId issue : result.issues()) {
                final HttpRequest issueRequest = HttpRequest.newBuilder(createIssueUri(issue.key()))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                try {
                    final var issueResponse = client.send(issueRequest, HttpResponse.BodyHandlers.ofInputStream());
                    if (issueResponse.statusCode() != 200) {
                        cf.completeExceptionally(new RuntimeException(String.format("Failed fetch issue %s from %s ", issue.key(), issueRequest.uri())));
                        return;
                    }
                    final var jsonFile = dir.resolve(issue.key() + ".json");
                    Files.copy(issueResponse.body(), jsonFile, StandardCopyOption.REPLACE_EXISTING);
                    downloaded.add(jsonFile);
                } catch (IOException | InterruptedException e) {
                    cf.completeExceptionally(e);
                    return;
                }
            }
            cf.complete(Set.copyOf(downloaded));
        });
        return cf;
    }

    @Override
    public void close() throws Exception {
        try {
            jsonb.close();
        } finally {
            client.close();
        }
    }

    private CompletableFuture<QueryResult> getIssues() {
        final CompletableFuture<QueryResult> cf = new CompletableFuture<>();
        final HttpRequest request = HttpRequest.newBuilder(createJqlUri()).GET()
                .header("Accept", "application/json")
                .build();
        final CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        future.whenComplete((response, ex) -> {
            if (ex != null) {
                cf.completeExceptionally(new RuntimeException("Failed fetch issues from " + request.uri(), ex));
                return;
            }
            if (response.statusCode() != 200) {
                cf.completeExceptionally(new RuntimeException(String.format("Failed fetch issues from %s%n%d: %s", request.uri(), response.statusCode(), response.body())));
                return;
            }
            cf.complete(jsonb.fromJson(response.body(), QueryResult.class));
        });
        return cf;
    }

    private URI createJqlUri() {
        final StringBuilder uri = new StringBuilder();
        uri.append(baseUri);
        if (!baseUri.endsWith("/")) {
            uri.append('/');
        }
        uri.append("rest/api/2/search?jql=project+%3d+")
                .append(projectId)
                .append("+AND+resolution+%3D+Unresolved+ORDER+BY+priority+DESC%2C+updated+DESC");
        return URI.create(uri.toString());
    }

    private URI createIssueUri(final String issueId) {
        final StringBuilder uri = new StringBuilder();
        uri.append(baseUri);
        if (!baseUri.endsWith("/")) {
            uri.append('/');
        }
        uri.append("rest/api/2/issue/").append(issueId);
        return URI.create(uri.toString());
    }

    public record QueryResult(int startAt, int total, List<IssueId> issues) {
    }

    public record IssueId(String key) {
    }
}
