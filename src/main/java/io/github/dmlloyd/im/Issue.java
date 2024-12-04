package io.github.dmlloyd.im;

import java.time.ZonedDateTime;

/**
 * An issue to import.
 */
public record Issue(
    String key,
    String summary,
    String description,
    ZonedDateTime created,
    ZonedDateTime updated,
    ZonedDateTime resolved
) {
}
