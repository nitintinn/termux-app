package com.termux.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;

public class ConsoleFragment extends Fragment {

    private static final String LOG_TAG = "ConsoleFragment";
    private RecyclerView mRecyclerView;
    private ActionAdapter mAdapter;
    private TextView mStatusText;
    private WebView mWebView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_console, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mStatusText = view.findViewById(R.id.console_status_text);
        mRecyclerView = view.findViewById(R.id.recycler_quick_actions);
        mWebView = view.findViewById(R.id.console_webview);

        setupRecyclerView();
        setupWebView();
        updateStatus();
    }

    private void setupRecyclerView() {
        List<ConsoleAction> actions = new ArrayList<>();
        actions.add(new ConsoleAction("Quick Update", "pkg update && pkg upgrade -y", R.drawable.ic_service_notification));
        actions.add(new ConsoleAction("Start SSH", "sshd", R.drawable.ic_terminal));
        actions.add(new ConsoleAction("Network Info", "ifconfig && curl ifconfig.me", R.mipmap.ic_launcher));
        actions.add(new ConsoleAction("Python REPL", "python", R.mipmap.ic_launcher));
        actions.add(new ConsoleAction("Node.js REPL", "node", R.mipmap.ic_launcher));
        actions.add(new ConsoleAction("System Info", "uname -a && uptime", R.drawable.ic_settings));

        mAdapter = new ActionAdapter(actions, this::onActionClicked);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerView.setAdapter(mAdapter);
    }

    private void setupWebView() {
        if (mWebView != null) {
            mWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    if (failingUrl.contains("localhost")) {
                        String html = "<html><body style='background:#121212;color:#00E5FF;font-family:sans-serif;display:flex;flex-direction:column;justify-content:center;align-items:center;height:100vh;margin:0;padding:20px;text-align:center;'>" +
                                      "<h1>ANTIGRAVITY DASHBOARD</h1>" +
                                      "<p style='color:#ffffff;opacity:0.7;'>No local dashboard detected at localhost:3000.</p>" +
                                      "<div style='border:1px solid #00E5FF;padding:15px;border-radius:8px;margin-top:20px;'>" +
                                      "<p style='font-size:14px;'>To launch the dashboard, run:</p>" +
                                      "<code style='background:#000;padding:5px;border-radius:4px;'>node antigravity-server.js</code>" +
                                      "</div>" +
                                      "</body></html>";
                        view.loadData(html, "text/html", "UTF-8");
                    }
                }
            });
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.getSettings().setDomStorageEnabled(true);
            // Default to a useful local dashboard if it exists, otherwise a placeholder
            mWebView.loadUrl("http://localhost:3000");
        }
    }

    private void onActionClicked(ConsoleAction action) {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null) return;

        TermuxService service = activity.getTermuxService();
        if (service == null) {
            Toast.makeText(getContext(), "Service not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Logger.logInfo(LOG_TAG, "Console Action Clicked: " + action.getTitle());
        
        // Execute the command in a new terminal session
        service.createTermuxSession(
            "/data/data/com.termux/files/usr/bin/login", 
            new String[]{"-c", action.getCommand()}, 
            null, null, false, action.getTitle());

        Toast.makeText(getContext(), "Starting " + action.getTitle() + "...", Toast.LENGTH_SHORT).show();
        
        // Switch to the terminal tab to show the output
        // activity.findViewById(R.id.bottom_navigation).setSelectedItemId(R.id.navigation_terminal);
    }

    private void updateStatus() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity != null && activity.getTermuxService() != null) {
            int sessionCount = activity.getTermuxService().getTermuxSessions().size();
            mStatusText.setText("System Ready • " + sessionCount + " Active Sessions");
        }
    }

    /**
     * Dashboard Action Adapter
     */
    static class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ViewHolder> {
        private final List<ConsoleAction> actions;
        private final OnActionListener listener;

        interface OnActionListener {
            void onAction(ConsoleAction action);
        }

        ActionAdapter(List<ConsoleAction> actions, OnActionListener listener) {
            this.actions = actions;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_console_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ConsoleAction action = actions.get(position);
            holder.title.setText(action.getTitle());
            holder.status.setText(action.getStatus());
            holder.icon.setImageResource(action.getIconResId());
            holder.itemView.setOnClickListener(v -> listener.onAction(action));
        }

        @Override
        public int getItemCount() {
            return actions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, status;
            ImageView icon;

            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.action_title);
                status = view.findViewById(R.id.action_status);
                icon = view.findViewById(R.id.action_icon);
            }
        }
    }
}



