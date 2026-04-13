package com.termux.app.models;

import androidx.annotation.DrawableRes;

/**
 * Represents a service or tool that can be managed from the Console Dashboard.
 */
public class ManagedService {
    
    public enum ServiceState {
        UNINSTALLED,
        INSTALLED,
        STOPPED,
        RUNNING,
        ACTION_IN_PROGRESS,
        ERROR
    }

    private final String title;
    private final String packageName;
    private final String binaryName;
    private final String startCmd;
    private final String stopCmd;
    private final String checkRunningCmd;
    private final String configPath;
    @DrawableRes private final int iconResId;
    
    private ServiceState state = ServiceState.UNINSTALLED;
    private String lastOutput = "";
    private String publicUrl = "";
    private String accountStats = "";
    private boolean watchdogEnabled = false;

    public ManagedService(String title, String packageName, String binaryName, String startCmd, String stopCmd, String checkRunningCmd, String configPath, @DrawableRes int iconResId) {
        this.title = title;
        this.packageName = packageName;
        this.binaryName = binaryName;
        this.startCmd = startCmd;
        this.stopCmd = stopCmd;
        this.checkRunningCmd = checkRunningCmd;
        this.configPath = configPath;
        this.iconResId = iconResId;
    }

    // Getters and Setters
    public String getTitle() { return title; }
    public String getPackageName() { return packageName; }
    public String getBinaryName() { return binaryName; }
    public String getStartCmd() { return startCmd; }
    public String getStopCmd() { return stopCmd; }
    public String getCheckRunningCmd() { return checkRunningCmd; }
    public String getConfigPath() { return configPath; }
    public int getIconResId() { return iconResId; }
    public ServiceState getState() { return state; }
    public void setState(ServiceState state) { this.state = state; }
    public String getLastOutput() { return lastOutput; }
    public void setLastOutput(String lastOutput) { this.lastOutput = lastOutput; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getAccountStats() { return accountStats; }
    public void setAccountStats(String accountStats) { this.accountStats = accountStats; }

    public boolean isWatchdogEnabled() { return watchdogEnabled; }
    public void setWatchdogEnabled(boolean watchdogEnabled) { this.watchdogEnabled = watchdogEnabled; }

    public String getBinaryPath() {
        return "/data/data/com.termux/files/usr/bin/" + binaryName;
    }

    public boolean isToolOnly() {
        return checkRunningCmd == null || checkRunningCmd.isEmpty();
    }
}
