package com.termux.app.fragments;

public class ConsoleAction {
    private String title;
    private String command;
    private int iconResId;
    private String status;

    public ConsoleAction(String title, String command, int iconResId) {
        this.title = title;
        this.command = command;
        this.iconResId = iconResId;
        this.status = "Ready";
    }

    public String getTitle() { return title; }
    public String getCommand() { return command; }
    public int getIconResId() { return iconResId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

