package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Optimized installer for A Termux extra packages.
 */
public class AtermuxPackageInstaller {

    private static final String LOG_TAG = "AtermuxPackageInstaller";
    private static final String INSTALL_DONE_FILE = ".atermux_installed";
    private static final String TERMUX_MAIN_PRIMARY_REPO = "https://packages.termux.dev/apt/termux-main";
    private static final String TERMUX_MAIN_FALLBACK_REPO = "https://packages-cf.termux.dev/apt/termux-main";

    public static boolean isExtraPackagesInstallRequired() {
        File homeDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        File doneFile = new File(homeDir, INSTALL_DONE_FILE);
        return !doneFile.exists();
    }

    public static void installExtraPackagesIfNeeded(final Activity activity, final TermuxService service, final Runnable whenDone) {
        File homeDir = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
        File doneFile = new File(homeDir, INSTALL_DONE_FILE);

        if (doneFile.exists()) {
            whenDone.run();
            return;
        }

        if (!isNetworkAvailable(activity)) {
            BootstrapStatusSession.appendStatus("Internet connection is required to download core packages.");
            showNetworkErrorDialog(activity, () -> installExtraPackagesIfNeeded(activity, service, whenDone), whenDone);
            return;
        }

        new Thread() {
            @Override
            public void run() {
                try {
                    Logger.logInfo(LOG_TAG, "Starting optimized package installation");
                    BootstrapStatusSession.appendStatus("Downloading and installing core tools in the background...");

                    // Combined installation script
                    String installScript = 
                        "#!/data/data/com.termux/files/usr/bin/bash\n" +
                        "exec >> \"" + BootstrapStatusSession.getLogFilePath() + "\" 2>&1\n" +
                        "set -e\n" +
                        "repair_repo() {\n" +
                        "  local repo_url=\"$1\"\n" +
                        "  echo \"--- Configuring repository: ${repo_url} ---\"\n" +
                        "  mkdir -p /data/data/com.termux/files/usr/etc/apt\n" +
                        "  printf 'deb %s stable main\\n' \"$repo_url\" > /data/data/com.termux/files/usr/etc/apt/sources.list\n" +
                        "  apt clean\n" +
                        "  rm -rf /data/data/com.termux/files/usr/var/lib/apt/lists/*\n" +
                        "}\n" +
                        "update_repo() {\n" +
                        "  local repo_url=\"$1\"\n" +
                        "  repair_repo \"$repo_url\"\n" +
                        "  pkg update -y\n" +
                        "}\n" +
                        "echo '--- Fixing GPG Keys ---'\n" +
                        "curl -sL https://packages.termux.dev/apt/termux-main/pool/main/t/termux-keyring/termux-keyring_3.13_all.deb -o /data/data/com.termux/files/usr/tmp/keyring.deb\n" +
                        "dpkg -i /data/data/com.termux/files/usr/tmp/keyring.deb\n" +
                        "echo '--- Updating Repositories ---'\n" +
                        "if ! update_repo '" + TERMUX_MAIN_PRIMARY_REPO + "'; then\n" +
                        "  echo 'Primary repository update failed, retrying with Cloudflare mirror...'\n" +
                        "  update_repo '" + TERMUX_MAIN_FALLBACK_REPO + "'\n" +
                        "fi\n" +
                        "pkg upgrade -y\n" +
                        "echo '--- Installing Core Packages ---'\n" +
                        "pkg install -y git proot-distro proot openssh curl wget nano vim python nodejs tar gzip unzip\n" +
                        "echo '--- Setting up Cloudflared ---'\n" +
                        "pkg install -y cloudflared || echo 'Cloudflared not in repo, skipping for manual setup.'\n" +
                        "echo '--- Finalizing Setup ---'\n" +
                        "touch " + TermuxConstants.TERMUX_HOME_DIR_PATH + "/" + INSTALL_DONE_FILE + "\n" +
                        "exit\n";

                    executeScript(service, installScript);

                    Logger.logInfo(LOG_TAG, "Installation sequence finished.");
                    BootstrapStatusSession.appendStatus("Core package installation finished.");
                    
                    activity.runOnUiThread(() -> {
                        if (doneFile.exists()) {
                            Logger.showToast(activity, "Environment initialized successfully!", false);
                        } else {
                            Logger.showToast(activity, "Installation finished with some warnings.", true);
                        }
                        whenDone.run();
                    });

                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Package installation failed", e);
                    BootstrapStatusSession.appendError("Core package installation failed: " + e.getMessage());
                    activity.runOnUiThread(() -> {
                        Logger.showToast(activity, "Setup failed. Check your internet and try again.", true);
                        whenDone.run();
                    });
                }
            }
        }.start();
    }

    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private static void showNetworkErrorDialog(Activity activity, Runnable retry, Runnable skip) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                .setTitle("Network Required")
                .setMessage("A Termux needs internet to download its initial tools (git, python, etc.). Please connect to the internet and try again.")
                .setPositiveButton("Retry", (dialog, which) -> retry.run())
                .setNegativeButton("Skip for Now", (dialog, which) -> {
                    BootstrapStatusSession.appendStatus("Skipped extra package installation for now.");
                    skip.run();
                })
                .setCancelable(false)
                .show();
        });
    }

    private static void executeScript(final TermuxService service, final String script) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final com.termux.shared.termux.shell.command.runner.terminal.TermuxSession[] sessionContainer = new com.termux.shared.termux.shell.command.runner.terminal.TermuxSession[1];

        // Write script to a temp file
        File scriptFile = new File(TermuxConstants.TERMUX_FILES_DIR_PATH + "/usr/tmp/setup.sh");
        if (!scriptFile.getParentFile().exists()) scriptFile.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(scriptFile)) {
            out.write(script.getBytes());
        }

        Logger.logInfo(LOG_TAG, "Executing setup script...");
        
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                sessionContainer[0] = service.createTermuxSession(
                    TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash", 
                    new String[]{"/data/data/com.termux/files/usr/tmp/setup.sh"}, 
                    null, null, false, "Core package setup");
            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Failed to create setup session: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await(30, TimeUnit.SECONDS);
        com.termux.shared.termux.shell.command.runner.terminal.TermuxSession session = sessionContainer[0];

        if (session == null || session.getTerminalSession() == null) {
            throw new Exception("Failed to start setup session");
        }

        // Wait for session to finish
        while (session.getTerminalSession().isRunning()) {
            Thread.sleep(1000);
        }
        
        scriptFile.delete();
    }
}

