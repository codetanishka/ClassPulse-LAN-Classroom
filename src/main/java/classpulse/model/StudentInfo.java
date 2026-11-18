package classpulse.model;

import java.io.Serializable;

public record StudentInfo(String id, String name, long joinedAt) implements Serializable {
    private static final long serialVersionUID = 1L;
}
