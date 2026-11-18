package classpulse.model;

import java.io.Serializable;

public record NoteData(NoteSummary summary, byte[] content) implements Serializable {
    private static final long serialVersionUID = 1L;
}
