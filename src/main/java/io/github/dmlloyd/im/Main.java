package io.github.dmlloyd.im;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.dmlloyd.im.jira.JiraIssue;
import io.github.dmlloyd.im.jira.JiraIssueFetcher;

/**
 * The main entry.
 */
public final class Main {
    public static void main(String[] argsArray) throws Exception {
        List<String> args = List.of(argsArray);
        Iterator<String> iterator = args.iterator();
        URI jiraUrl = null;
        String jiraProjectId = null;
        URI input = null;
        String owner = null;
        String repo = null;
        boolean dryRun = false;
        while (iterator.hasNext()) {
            String arg = iterator.next();
            switch (arg) {
                case "--help" -> {
                    System.out.println("""
                            Supported arguments:
                               --help          this message
                               --jira-url      the base URL of the JIRA service
                               --jira-project  the Jira project id
                               --input         the input file name or remote URL
                               --dry-run       to not actually commit anything
                               --repo          the GitHub owner/repo
                            """);
                }
                case "--jira-url" -> jiraUrl = new URI(iterator.next());
                case "--jira-project" -> jiraProjectId = iterator.next();
                case "--input" -> input = new URI(iterator.next());
                case "--dry-run" -> dryRun = true;
                case "--repo" -> {
                    String orgRepo = iterator.next();
                    Pattern pattern = Pattern.compile("([a-zA-Z0-9-_.]+)/([a-zA-Z0-9-_.]+)");
                    Matcher matcher = pattern.matcher(orgRepo);
                    if (matcher.matches()) {
                        owner = matcher.group(1);
                        repo = matcher.group(2);
                    } else {
                        throw new IllegalArgumentException("Invalid syntax for --repo (expected `owner/repo`)");
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
        }
        if (jiraUrl == null) {
            throw new IllegalArgumentException("No JIRA URL given");
        }
        if (jiraProjectId == null) {
            throw new IllegalArgumentException("No JIRA project id given");
        }
        if (input == null) {
            throw new IllegalArgumentException("No input file or URL given");
        }
        if (owner == null || repo == null) {
            throw new IllegalArgumentException("No GitHub owner/repo given");
        }
        final Set<JiraIssue> jiraIssues;
        if (!dryRun) {
            try (JiraIssueFetcher fetcher = new JiraIssueFetcher(jiraProjectId, jiraUrl.toASCIIString())) {
                jiraIssues = fetcher.fetch().get();
            }
        } else {
            jiraIssues = Set.of();
        }
        if (jiraIssues.isEmpty()) {
            System.out.printf("No issues found for JIRA project: %s/browse/%s%n", jiraUrl, jiraProjectId);
            System.exit(0);
        }
        // todo: actually do it...

    }
}
