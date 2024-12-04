package io.github.dmlloyd.im.jira;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.function.Function;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Mappers {

    private static final Function<JiraUser, String> JIRA_USER_MAPPER;

    static {
        try (InputStream in = Mappers.class.getResourceAsStream("/user-mapping.properties")) {
            if (in == null) {
                JIRA_USER_MAPPER = JiraUser::name;
            } else {
                final Properties properties = new Properties();
                properties.load(in);
                JIRA_USER_MAPPER = (user) -> {
                    String username = properties.getProperty(user.name());
                    if (username == null) {
                        username = user.name();
                    }
                    return username;
                };
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Mappers() {
    }

    /**
     * Maps a Jira user to a GitHub username.
     *
     * @return the GitHub username, or the Jira username if the user could not be mapped
     */
    public static Function<JiraUser, String> jiraUserMapper() {
        return JIRA_USER_MAPPER;
    }
}
