package com.termux.app.models;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.runner.app.AppShell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Registry and state detection engine for A Termux services.
 */
public class AtermuxServiceRegistry {

    private final List<ManagedService> services = new ArrayList<>();

    public AtermuxServiceRegistry() {
        // Core Services
        services.add(new ManagedService("SSH Server", "openssh", "sshd", "sshd", "pkill sshd", "pgrep sshd", "/data/data/com.termux/files/home/.ssh/config", R.drawable.ic_terminal));
        services.add(new ManagedService("Cloudflare Tunnel", "cloudflared", "cloudflared", "cloudflared tunnel run", "pkill cloudflared", "pgrep cloudflared", "/data/data/com.termux/files/home/.cloudflared/config.yml", R.drawable.ic_settings));
        
        // Development Tools (Status only, no start/stop)
        services.add(new ManagedService("Python Environment", "python", "python", null, null, null, null, R.mipmap.ic_launcher));
        services.add(new ManagedService("Node.js Runtime", "nodejs", "node", null, null, null, null, R.mipmap.ic_launcher));
        services.add(new ManagedService("Git Version Control", "git", "git", null, null, null, null, R.drawable.ic_service_notification));
        
        // Linux Distros
        services.add(new ManagedService("Ubuntu (pd)", "proot-distro", "proot-distro", "proot-distro login ubuntu", null, "pgrep proot", null, R.drawable.ic_settings));
    }

    public List<ManagedService> getServices() {
        return services;
    }

    /**
     * Non-blocking check of all service states.
     */
    public void refreshStates(TermuxService termuxService, Runnable onComplete) {
        new Thread(() -> {
            for (ManagedService service : services) {
                // Check if installed
                File binary = new File(service.getBinaryPath());
                if (!binary.exists()) {
                    service.setState(ManagedService.ServiceState.UNINSTALLED);
                    continue;
                }

                if (service.isToolOnly()) {
                    service.setState(ManagedService.ServiceState.INSTALLED); // Tool exists
                    continue;
                }

                // Check if running via pgrep (Blocking in this thread)
                try {
                    boolean running = checkIsRunningSync(termuxService, service.getCheckRunningCmd());
                    service.setState(running ? ManagedService.ServiceState.RUNNING : ManagedService.ServiceState.STOPPED);
                } catch (Exception e) {
                    service.setState(ManagedService.ServiceState.STOPPED);
                }
            }
            if (onComplete != null) onComplete.run();
        }).start();
    }

    private boolean checkIsRunningSync(TermuxService service, String cmd) throws InterruptedException {
        // This is a bit tricky since createTermuxTask is async.
        // For simplicity in this version, we can use Runtime.exec for quick pgrep checks as they are harmless.
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
