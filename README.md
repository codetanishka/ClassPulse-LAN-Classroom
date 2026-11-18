# ClassPulse — LAN Classroom Interaction System

A desktop classroom app for a teacher and students on the same local network.
The teacher hosts a class from a Windows PC; students join from the same app
running on other computers. Announcements, multiple-choice questions with a live
countdown, live answer counts, final results and shared notes all travel over a
plain TCP connection.

- **Version:** 1.0.0
- **Language / UI:** Java 21, Swing and AWT (standard library only)
- **Platform:** Windows 10/11 (the installer is Windows-specific; the jar also
  runs anywhere Java 21 is installed)
- **Installer:** `dist\installer\ClassPulse-1.0.0.exe`

---

## 1. What is in this folder

| Path | What it is |
| --- | --- |
| `dist\installer\ClassPulse-1.0.0.exe` | Windows installer (the deliverable) |
| `dist\ClassPulse.jar` | Runnable application jar |
| `assets\ClassPulse-logo.png` | Full-size logo |
| `assets\ClassPulse.ico` | Multi-size Windows icon used by the app and installer |
| `screenshots\` | Captured screens of the running application |
| `src\main\java\` | Application source code |
| `src\test\java\` | Headless protocol test, UI smoke test, screenshot tool |
| `build.ps1` | Clean build + asset generation + self-test + jar |
| `package.ps1` | Builds the `.exe` installer with `jpackage` |
| `run.bat` | Runs the packaged jar |
| `tools\wix\` | WiX 3 binaries used by `package.ps1` (not needed to run the app) |

---

## 2. Quick start on one computer

1. Double-click `dist\installer\ClassPulse-1.0.0.exe` and follow the wizard.
   It installs for the current user, adds a Start-menu shortcut and can be
   removed from **Settings → Apps → Installed apps → ClassPulse**.
2. Open **ClassPulse**.
3. Choose **Start a class**.
4. Leave the defaults (`Class 12 - Computer Science`, port `5050`) or change
   them, then click **Start class**.
5. The classroom window shows an address such as `192.168.1.7:5050`.
6. Start **ClassPulse a second time** (Start menu → ClassPulse). It opens a
   second independent window. Choose **Join a class**.
7. Enter your name, the address `127.0.0.1` (same computer) and port `5050`,
   then **Join class**.
8. Send an announcement, ask an MCQ and share a notes file from the teacher
   window; answer it from the student window.

> The two windows use the same installed app. There is no separate server
> program: the teacher window contains the server.

---

## 3. Two-computer demo (classroom LAN)

1. Connect both computers to the **same Wi-Fi or wired LAN**. A phone hotspot
   also works if client isolation is disabled.
2. On the teacher computer, open ClassPulse → **Start a class** → **Start class**.
3. If Windows Defender Firewall asks, **allow ClassPulse on Private networks**.
   Without this, students cannot connect.
4. Read the address in the teacher window (for example `192.168.1.7:5050`).
   The sidebar also shows the `127.0.0.1` address for the same computer.
5. On each student computer, open ClassPulse → **Join a class**, enter:
   - **Your name** — 2 to 30 characters, unique in the class
   - **Teacher's IP address** — the address shown on the teacher screen
   - **Port** — the same port as the teacher (`5050` by default)
6. Click **Join class**. The student name appears in the teacher's **In class**
   list immediately.

Use `Copy address` in the teacher header to put the full `IP:port` on the
clipboard and paste it into a chat message for students.

### If students cannot connect

- Both devices must be on the same subnet. Guest Wi-Fi networks often block
  device-to-device traffic (AP isolation); use a normal network or a hotspot.
- Check the teacher's IPv4 address with `ipconfig` if the shown address belongs
  to the wrong adapter (VPN or virtual adapter).
- Allow the app through the firewall:
  **Control Panel → System and Security → Windows Defender Firewall →
  Allow an app or feature through Windows Defender Firewall → Change settings →
  allow ClassPulse on Private**.
- A port can only be used once. If `5050` is busy, pick another port in the
  teacher setup and use the same port on the student side.
- The connection is plain TCP with no encryption, which is appropriate for a
  trusted classroom LAN only.

---

## 4. Teacher console

**Start screen** — class name, teacher name and port (validated 1–65535).
A busy port produces a clear message and keeps you on the setup screen.

**Live classroom**

- **In class** — live roster with name, join time and online dot.
- **Announcements** — up to 1000 characters; every student sees it instantly.
  Recent announcements are kept newest-first.
- **MCQ & Results** — question plus 2 to 4 options, mark the correct option,
  choose a countdown from 5 to 600 seconds.
  - The question opens immediately and the countdown starts automatically.
  - Live bars show how many students chose each option and how many answered.
  - **End question & reveal** closes it early; otherwise it closes when the
    timer reaches zero.
  - The correct option and final counts are then shared with students.
- **Notes** — share one file (PDF, DOCX, PPTX, image, text, …) up to 20 MB.
  Every student receives the file entry and can download it.
- **End class** — asks for confirmation, disconnects all students, writes the
  session archive and returns to the launcher.

A class can have only one open question at a time. Answers are accepted only
while the question is open, only once per student, and never reveal the correct
answer before the teacher closes the question.

---

## 5. Student console

- **Join screen** — name, teacher IP address and port, with inline validation
  and readable connection errors (host not found, refused, timeout, duplicate
  name, class closed).
- **Announcements** — messages arrive without refreshing.
- **Questions** — the MCQ appears with a countdown; pick an option and submit.
  A receipt confirms the answer; the question can only be answered once.
  After the teacher closes it, the student sees the correct option, their own
  choice, a correct/incorrect message and the class distribution.
- **Notes** — list of shared files with a **Download** button and a Save dialog
  (defaults to the Downloads folder).
- **Leave class** — disconnects and returns to the join form. If the teacher
  ends the class or the network drops, the student is returned to the join
  form with an explanation.

---

## 6. Running without installing

Requires Java 21 on `PATH`.

```powershell
java -jar dist\ClassPulse.jar
```

Or double-click `run.bat`.

---

## 7. Building from source

Requirements: JDK 21 (`javac`, `jar`, `jpackage`), PowerShell.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

This cleans `build\`, compiles the app, regenerates the logo/icon, compiles and
runs the headless network test, and writes `dist\ClassPulse.jar`.

To rebuild the installer:

```powershell
powershell -ExecutionPolicy Bypass -File .\package.ps1
```

`package.ps1` needs WiX 3.x. It checks `%WIX%`, `tools\wix` (already present) and
`PATH`. The output is `dist\installer\ClassPulse-1.0.0.exe`, a per-user WiX
installer with the application icon, Start-menu entry, desktop shortcut and a
directory chooser.

For a computer lab, install silently with `ClassPulse-1.0.0.exe /quiet`
(`/passive` shows a progress bar). Note that `/S` is not the jpackage switch and
will leave the installer waiting for input. Uninstall from
**Settings → Apps → Installed apps → ClassPulse**.

---

## 8. Testing

### Headless protocol test

`build.ps1` runs it automatically. To run it alone:

```powershell
java -Djava.awt.headless=true -cp "build\classes;build\test-classes" classpulse.NetworkSelfTest
```

It starts a real server on an ephemeral port and two real clients, and verifies:

1. Server starts and binds a port.
2. Two students join and the roster reaches two entries.
3. A duplicate student name is rejected with a clear error.
4. An announcement reaches both students intact.
5. A live question and its 10-second countdown reach both students.
6. Both answers are counted live on the teacher side.
7. Ending the question shares the result and the correct answer with everyone.
8. A note is shared and downloaded byte-for-byte.
9. Ending the class disconnects both students cleanly.

### UI smoke test

```powershell
java "-Dclasspulse.test=true" -cp "build\classes;build\test-classes" classpulse.UiSmokeTest
```

It opens the real Swing windows and drives them: launcher → student role →
teacher role → start class on port 5061 → student joins over a real socket →
announcement → MCQ → student answer → live teacher count → reveal. Dialogs are
suppressed during the test so it can run unattended. The same test was run
against the jar installed by the `.exe`, and the installed `ClassPulse.exe` was
launched to confirm the window and embedded icon.

### Screenshots

```powershell
java "-Dclasspulse.test=true" -cp "build\classes;build\test-classes" classpulse.ScreenshotTool
```

Writes the current UI to `screenshots\` (launcher, setup screens, dashboard,
MCQ composer, live results, final results).

### Manual test checklist

- [x] Installer runs, app opens with the ClassPulse icon.
- [x] Invalid port `abc` / `70000` is refused with a message.
- [x] Starting on an occupied port shows a clear error.
- [x] Student joining with `127.0.0.1` works.
- [x] Duplicate student name is refused.
- [x] Blank name / name shorter than 2 characters is refused.
- [x] Wrong host or port shows a connection error instead of freezing.
- [x] Announcement appears on the student screen.
- [x] MCQ countdown appears on both screens.
- [x] Answer is acknowledged once; second attempt is refused.
- [x] Teacher sees the live count and final distribution.
- [x] Student sees the correct answer after the reveal.
- [x] Note is transferred and saved correctly.
- [x] Ending the class disconnects students and saves the session log.
- [x] Closing a live teacher window asks for confirmation.

---

## 9. Data and files

- **Session archive:** `%APPDATA%\ClassPulse\sessions\`
  - `<timestamp>-<class>.dat` — serialized `SessionArchive` (students,
    announcements, quiz results, notes)
  - `<timestamp>-<class>.txt` — readable summary of the same session
- **Downloaded notes:** wherever the student chooses in the Save dialog
  (default: the user's `Downloads` folder).
- Shared notes stay in memory for the duration of the class; the archive stores
  their names and sizes, not the file bytes.

---

## 10. Project structure

```
src/main/java/classpulse/
├── Main.java                  entry point, look and feel, global error handler
├── brand/Brand.java           palette, fonts, Java2D logo, PNG/ICO export
├── model/                     serializable DTOs (Message, QuizView, QuizResult, ...)
├── net/
│   ├── ClassroomServer.java   ServerSocket, sessions, roster, quiz timer, notes, archive
│   ├── ClassroomClient.java   connection handshake, reader thread, request methods
│   ├── ServerListener.java    server callbacks
│   ├── ClientListener.java    client callbacks
│   ├── SessionArchive.java    serialized end-of-class record
│   └── SessionStore.java      .dat + .txt session output
└── ui/
    ├── LauncherFrame.java     role selection
    ├── TeacherFrame.java      setup, roster, announcements, MCQ, notes
    ├── StudentFrame.java      join, announcements, answering, downloads
    ├── Ui.java                shared layout/typography/helpers
    └── component/             rounded panels/buttons, pill labels, avatars, bars, options
```

---

## 11. Java concepts demonstrated

| Concept | Where |
| --- | --- |
| **Encapsulation / records / interfaces** | `model` records with compact constructors and defensive copies; `ServerListener` and `ClientListener` interfaces |
| **Inheritance / polymorphism** | `JFrame` subclasses; custom `JPanel`, `JButton`, `JToggleButton` subclasses overriding `paintComponent`; `OptionButton` |
| **Collections** | `List`, `ArrayList`, `Map`, `HashMap`, `Set`; `CopyOnWriteArrayList` for live lists; unmodifiable copies in records |
| **Generics** | Listener lists, `SwingWorker<JoinInfo, Void>`, generic UI helpers |
| **Exceptions** | Checked `IOException`/`ClassNotFoundException`; validation with `IllegalArgumentException`/`IllegalStateException`; `try`/`catch`/`finally`; try-with-resources in `SessionStore`; user-facing error dialogs |
| **Multithreading** | Server accept thread, per-client reader threads (`ExecutorService`), `ScheduledExecutorService` countdown, `synchronized` roster/quiz locks, `volatile` state, `SwingWorker` for connect/download, `SwingUtilities.invokeLater`, `Swing Timer` for countdown display |
| **Sockets** | `ServerSocket`, `Socket`, per-connection object streams, handshake, heartbeat-free request/response protocol |
| **Serialization** | `Message` envelope and all DTOs implement `Serializable`; `ObjectOutputStream`/`ObjectInputStream` in both directions; session archive serialization |
| **File handling** | `Files.readAllBytes`, `Files.write`, `JFileChooser`, NIO paths, `ImageIO`, session text writer |
| **GUI (Swing/AWT)** | `CardLayout`, `BorderLayout`, `GridBagLayout`, `BoxLayout`, `GridLayout`, `JSplitPane`, `JTabbedPane`, custom painting with Java2D, clipboard, dialogs, keyboard-friendly forms |
| **Error handling** | Port validation, name validation, duplicate-name rejection, file-size limit, unknown message types, lost connections, busy port, clipboard failure, uncaught exception handler |

---

## 12. Limits and design notes

- One class per teacher instance; the server lives inside the teacher window.
- Plain TCP and Java serialization; no TLS, accounts or passwords. Intended for
  a trusted classroom LAN.
- Questions support 2–4 text options and one correct answer.
- Announcements: 1000 characters. Notes: 20 MB per file.
- Student names: 2–30 characters, letters/numbers/spaces/`.`/`_`/`-`/`'`,
  unique while online.
- The countdown is enforced on the server; the UI timer only displays it.
- Session logs are written when the class ends normally. Killing the process
  from Task Manager skips the archive.

---

## 13. Screenshots

| File | Screen |
| --- | --- |
| `screenshots\01-launcher.png` | Role selection |
| `screenshots\02-teacher-setup.png` | Create classroom |
| `screenshots\03-teacher-dashboard-empty.png` | Live dashboard, waiting for students |
| `screenshots\05-student-join.png` | Student join form |
| `screenshots\06-student-announcements.png` | Student announcements |
| `screenshots\07-teacher-mcq-composer.png` | MCQ composer |
| `screenshots\08-student-question.png` | Live question on the student side |
| `screenshots\09-teacher-live-results.png` | Live answer bars |
| `screenshots\10-teacher-final-results.png` | Revealed results |
| `screenshots\11-student-result.png` | Student feedback |
| `screenshots\12-installed-icon.png` | Icon embedded in the installed executable |
