package io.github.dmlloyd.im.jira;

/**
 * A simple JIRA user.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public record JiraUser(String key, String name, String displayName, boolean active) {
}
