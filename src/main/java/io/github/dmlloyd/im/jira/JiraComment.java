package io.github.dmlloyd.im.jira;

import java.time.ZonedDateTime;

/**
 * Represents a Jira comment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public record JiraComment(
        JiraUser author,
        JiraUser updateAuthor,
        ZonedDateTime created,
        ZonedDateTime updated,
        String body
) {
}
