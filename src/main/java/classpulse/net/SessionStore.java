package classpulse.net;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Stores session archives under %APPDATA%\ClassPulse\sessions (or the user
 * home folder when APPDATA is unavailable).
 */
public final class SessionStore {
    private static final DateTimeFormatter READABLE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault());

    private SessionStore() {
    }

    public static Path save(SessionArchive archive) throws IOException {
        String base = System.getenv("APPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        Path directory = Paths.get(base, "ClassPulse", "sessions");
        Files.createDirectories(directory);

        String safeClass = archive.className().replaceAll("[^a-zA-Z0-9._-]+", "-");
        if (safeClass.isBlank()) {
            safeClass = "class";
        }
        String stamp = ClassroomServer.timestampForFile();
        Path binary = directory.resolve(stamp + "-" + safeClass + ".dat");
        Path summary = directory.resolve(stamp + "-" + safeClass + ".txt");

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(binary))) {
            out.writeObject(archive);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(summary, StandardCharsets.UTF_8)) {
            writer.write("ClassPulse session summary");
            writer.newLine();
            writer.write("========================");
            writer.newLine();
            writer.write("Class: " + archive.className());
            writer.newLine();
            writer.write("Teacher: " + archive.teacherName());
            writer.newLine();
            writer.write("Started: " + READABLE.format(Instant.ofEpochMilli(archive.startedAt())));
            writer.newLine();
            writer.write("Ended: " + READABLE.format(Instant.ofEpochMilli(archive.endedAt())));
            writer.newLine();
            writer.write("Reason: " + archive.endReason());
            writer.newLine();
            writer.newLine();
            writer.write("Students (" + archive.students().size() + "):");
            writer.newLine();
            for (var student : archive.students()) {
                writer.write("  - " + student.name());
                writer.newLine();
            }
            writer.newLine();
            writer.write("Announcements (" + archive.announcements().size() + "):");
            writer.newLine();
            for (var announcement : archive.announcements()) {
                writer.write("  [" + READABLE.format(Instant.ofEpochMilli(announcement.sentAt())) + "] "
                        + announcement.text().replace("\n", " "));
                writer.newLine();
            }
            writer.newLine();
            writer.write("Questions (" + archive.quizResults().size() + "):");
            writer.newLine();
            for (var quiz : archive.quizResults()) {
                writer.write("  Q: " + quiz.prompt());
                writer.newLine();
                for (int i = 0; i < quiz.options().size(); i++) {
                    String marker = i == quiz.correctIndex() ? " (correct)" : "";
                    writer.write("     " + (char) ('A' + i) + ". " + quiz.options().get(i)
                            + marker + " - " + quiz.counts()[i] + " answer(s)");
                    writer.newLine();
                }
                writer.write("     Answered: " + quiz.answered() + " of " + quiz.totalStudents());
                writer.newLine();
            }
            writer.newLine();
            writer.write("Shared notes (" + archive.notes().size() + "):");
            writer.newLine();
            for (var note : archive.notes()) {
                writer.write("  - " + note.fileName() + " (" + note.sizeBytes() + " bytes)");
                writer.newLine();
            }
        }
        return summary;
    }
}
