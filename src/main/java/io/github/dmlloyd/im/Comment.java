package io.github.dmlloyd.im;

import java.time.ZonedDateTime;

/**
 * An issue comment.
 */
public record Comment(
    String author,
    ZonedDateTime created,
    String body
) {
}
