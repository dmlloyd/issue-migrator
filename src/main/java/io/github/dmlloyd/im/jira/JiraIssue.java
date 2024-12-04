package io.github.dmlloyd.im.jira;

import java.time.ZonedDateTime;
import java.util.Set;

import io.github.dmlloyd.im.Comment;
import io.github.dmlloyd.im.Issue;
import io.github.dmlloyd.im.Status;

/**
 * Represents a Jira issue.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public record JiraIssue(
        String description,
        String id,
        String summary,
        String type,
        String priority,
        JiraUser assignee,
        JiraUser reporter,
        ZonedDateTime created,
        ZonedDateTime updated,
        Set<JiraComment> comments
) {

    /**
     * Converts the Jira issue into an {@link Issue}.
     *
     * @return the new issue
     */
    public Issue toIssue() {
        return new Issue(
                id(),
                summary(),
                description(),
                Mappers.jiraUserMapper().apply(reporter()),
                Mappers.jiraUserMapper().apply(assignee()),
                Status.OPEN,
                null,
                created(),
                updated(),
                null,
                comments()
                        .stream()
                        .map(c -> new Comment(Mappers.jiraUserMapper().apply(c.author()), c.created(), c.body()))
                        .toList()
        );
    }
}
