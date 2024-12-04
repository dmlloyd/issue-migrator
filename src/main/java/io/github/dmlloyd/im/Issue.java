package io.github.dmlloyd.im;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * An issue to import.
 */
public record Issue(
    String key,
    String summary,
    String description,
    ZonedDateTime created,
    ZonedDateTime updated,
    ZonedDateTime resolved,
    List<Comment> comments
) {
    public Issue {
        comments = List.copyOf(comments);
    }
}
