package com.termux.app.models;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.runner.app.AppShell;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry and state detection engine for A Termux services.
 */
public class AtermuxServiceRegistry {

    private final List<ManagedService> services = new ArrayList<>();

    public AtermuxServiceRegistry() {
        // Core Services
        services.add(new ManagedService("SSH Server", "openssh", "sshd", "sshd", "pkill sshd", "pgrep sshd", "/data/data/com.termux/files/home/.ssh/config", R.drawable.ic_terminal));
        
        // Cloudflare Setup: Log to a file we can scrape
        String cfStart = "mkdir -p ~/.atermux && cloudflared tunnel --url http://localhost:8080 > ~/.atermux/cloudflared.log 2>&1";
        services.add(new ManagedService("Cloudflare Tunnel", "cloudflared", "cloudflared", cfStart, "pkill cloudflared", "pgrep cloudflared", "/data/data/com.termux/files/home/.cloudflared/config.yml", R.drawable.ic_settings));
        
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
                    
                    // IF Cloudflared is running, try to detect the URL
                    if (service.getTitle().contains("Cloudflare")) {
                        if (running) detectCloudflareUrl(service);
                        fetchCloudflareInsights(service);
                    } else if (service.getTitle().contains("Git")) {
                        fetchGitInsights(service);
                    }
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

    private void detectCloudflareUrl(ManagedService service) {
        try {
            File logFile = new File("/data/data/com.termux/files/home/.atermux/cloudflared.log");
            if (!logFile.exists()) return;

            String content = new String(Files.readAllBytes(logFile.toPath()));
            // Look for patterns like https://*.trycloudflare.com
            Pattern pattern = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");
            Matcher matcher = pattern.matcher(content);
            
            String foundUrl = null;
            while (matcher.find()) {
                foundUrl = matcher.group(); // Get the last found URL
            }
            
            if (foundUrl != null) {
                service.setPublicUrl(foundUrl);
            }
        } catch (Exception e) {
            // Log silent failure
        }
    }

    private void fetchCloudflareInsights(ManagedService service) {
        try {
            StringBuilder stats = new StringBuilder();
            
            // 1. Check Login Status
            File certFile = new File("/data/data/com.termux/files/home/.cloudflared/cert.pem");
            boolean loggedIn = certFile.exists();
            stats.append(loggedIn ? "ACCOUNT: Authenticated\n" : "ACCOUNT: Login Required\n");

            // 2. Fetch Tunnel List Count
            // We run a silent check
            Process p = Runtime.getRuntime().exec("/data/data/com.termux/files/usr/bin/cloudflared tunnel list");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("    ") || line.contains("-")) { // Heuristic for table data
                    count++;
                }
            }
            reader.close();
            p.waitFor();
            
            // Adjust count (subtract header/separator if needed, but tunnel list usually has header + separator)
            // Header is ID NAME CREATED CONNECTIONS. Separator is ---
            // If we have 2 lines of header/separator, subtract
            int actualTunnels = Math.max(0, count - 1); 
            stats.append("TUNNELS: ").append(actualTunnels).append(" active\n");

            // 3. DNS Routes (Simplified check)
            stats.append("SITES: Managed via Dashboard");

            service.setAccountStats(stats.toString());
        } catch (Exception e) {
            service.setAccountStats("Account: Info Unavailable");
        }
    }

    private void fetchGitInsights(ManagedService service) {
        try {
            StringBuilder stats = new StringBuilder();
            File wwwDir = new File("/data/data/com.termux/files/home/www");
            File[] files = wwwDir.listFiles();

            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File gitDir = new File(file, ".git");
                        if (gitDir.exists()) {
                            // It's a repo!
                            String branch = getGitBranch(file.getAbsolutePath());
                            boolean isDirty = isGitDirty(file.getAbsolutePath());
                            
                            stats.append(file.getName())
                                 .append(" ● ").append(branch)
                                 .append(isDirty ? " [dirty]" : " [clean]")
                                 .append("\n");
                        }
                    }
                }
            } else {
                // Secondary check: look for jewellery or common targets if www is just files
                File target = new File("/data/data/com.termux/files/home/www/.git");
                if (target.exists()) {
                    String branch = getGitBranch("/data/data/com.termux/files/home/www");
                    stats.append("www ● ").append(branch).append("\n");
                }
            }

            if (stats.length() == 0) {
                stats.append("No active repositories detected.");
            }

            service.setAccountStats(stats.toString().trim());
        } catch (Exception e) {
            service.setAccountStats("Git: Metadata fetch failed");
        }
    }

    private String getGitBranch(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/data/data/com.termux/files/usr/bin/git", "-C", path, "branch", "--show-current"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String branch = reader.readLine();
            reader.close();
            p.waitFor();
            return (branch != null && !branch.isEmpty()) ? branch : "unknown";
        } catch (Exception e) {
            return "error";
        }
    }

    private boolean isGitDirty(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/data/data/com.termux/files/usr/bin/git", "-C", path, "status", "--porcelain"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            boolean dirty = reader.readLine() != null; // If output exists, it's dirty
            reader.close();
            p.waitFor();
            return dirty;
        } catch (Exception e) {
            return false;
        }
    }
}
