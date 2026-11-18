package classpulse.model;

import java.io.Serializable;

public record Announcement(String id, String text, long sentAt) implements Serializable {
    private static final long serialVersionUID = 1L;
}
