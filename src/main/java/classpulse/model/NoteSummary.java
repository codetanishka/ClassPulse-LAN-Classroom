package classpulse.model;

import java.io.Serializable;

public record NoteSummary(
        String id,
        String fileName,
        long sizeBytes,
        long uploadedAt
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
