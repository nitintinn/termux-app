package com.termux.app;

import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class BootstrapStatusSession {

    static final String SESSION_NAME = "Initializing A Termux";

    private static final String LOG_DIR_PATH = TermuxConstants.TERMUX_FILES_DIR_PATH + "/bootstrap";
    private static final String LOG_FILE_PATH = LOG_DIR_PATH + "/startup.log";
    private static final String DONE_FILE_PATH = LOG_DIR_PATH + "/startup.done";

    private static final String LOG_TAG = "BootstrapStatusSession";

    private BootstrapStatusSession() {}

    static boolean isStartupFlowRequired(TermuxActivity activity) {
        return TermuxInstaller.isBootstrapSetupRequired(activity) ||
            AtermuxPackageInstaller.isExtraPackagesInstallRequired();
    }

    static TermuxSession startOrAttach(TermuxActivity activity, TermuxService service) {
        TermuxSession existing = service.getTermuxSessionForShellName(SESSION_NAME);
        if (existing != null) {
            show(activity, existing.getTerminalSession());
            return existing;
        }

        resetLog();
        appendStatus("== Initializing A Termux ==");
        appendStatus("Startup is running in the background.");
        appendStatus("Open the left drawer any time to revisit this session.");

        String command =
            "mkdir -p \"" + LOG_DIR_PATH + "\"; " +
            "touch \"" + LOG_FILE_PATH + "\"; " +
            "rm -f \"" + DONE_FILE_PATH + "\"; " +
            "tail -n +1 -f \"" + LOG_FILE_PATH + "\" & " +
            "TAIL_PID=$!; " +
            "while [ ! -f \"" + DONE_FILE_PATH + "\" ]; do sleep 1; done; " +
            "kill $TAIL_PID >/dev/null 2>&1; " +
            "wait $TAIL_PID 2>/dev/null; " +
            "printf '\\nInitialization session complete. Check the main shell for normal use.\\n'; " +
            "exit 1";

        TermuxSession session = service.createTermuxSession("/system/bin/sh",
            new String[]{"-c", command}, null, "/", true, SESSION_NAME);
        if (session != null) {
            show(activity, session.getTerminalSession());
        } else {
            Logger.showToast(activity, "Failed to start initialization session", true);
        }
        return session;
    }

    static void appendStatus(String status) {
        writeLine(status);
    }

    static void appendError(String error) {
        writeLine("ERROR: " + error);
    }

    static void complete(String finalStatus) {
        if (finalStatus != null && !finalStatus.isEmpty()) {
            writeLine(finalStatus);
        }

        try {
            File logDir = new File(LOG_DIR_PATH);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            new File(DONE_FILE_PATH).createNewFile();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create startup done flag", e);
        }
    }

    static String getLogFilePath() {
        return LOG_FILE_PATH;
    }

    private static void show(TermuxActivity activity, TerminalSession session) {
        if (activity == null || session == null) return;

        BottomNavigationView bottomNavigationView = activity.findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.navigation_terminal);
        }

        if (activity.getTermuxTerminalSessionClient() != null) {
            activity.getTermuxTerminalSessionClient().setCurrentSession(session);
        }
    }

    private static void resetLog() {
        try {
            File logDir = new File(LOG_DIR_PATH);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            File doneFile = new File(DONE_FILE_PATH);
            if (doneFile.exists()) {
                doneFile.delete();
            }

            File logFile = new File(LOG_FILE_PATH);
            try (FileOutputStream out = new FileOutputStream(logFile, false)) {
                out.write(new byte[0]);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to reset startup log", e);
        }
    }

    private static synchronized void writeLine(String line) {
        try {
            File logDir = new File(LOG_DIR_PATH);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            try (FileOutputStream out = new FileOutputStream(LOG_FILE_PATH, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to append startup log", e);
        }
    }
}
