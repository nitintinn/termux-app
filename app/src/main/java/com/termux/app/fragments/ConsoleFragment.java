package com.termux.app.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.models.AtermuxServiceRegistry;
import com.termux.app.models.ManagedService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.util.List;

public class ConsoleFragment extends Fragment {

    private static final String LOG_TAG = "ConsoleFragment";
    private RecyclerView mRecyclerView;
    private ServiceAdapter mAdapter;
    private TextView mStatusText;
    private WebView mWebView;
    private MaterialButton mBtnLaunchDashboard, mBtnSetupDashboard;
    private AtermuxServiceRegistry mRegistry;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            mHandler.postDelayed(this, 5000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_console, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mStatusText = view.findViewById(R.id.console_status_text);
        mRecyclerView = view.findViewById(R.id.recycler_quick_actions); // Keep ID from layout
        mWebView = view.findViewById(R.id.console_webview);
        mBtnLaunchDashboard = view.findViewById(R.id.btn_launch_dashboard);
        mBtnSetupDashboard = view.findViewById(R.id.btn_setup_dashboard);

        mRegistry = new AtermuxServiceRegistry();
        setupRecyclerView();
        setupWebView();
        
        if (mBtnLaunchDashboard != null) {
            mBtnLaunchDashboard.setOnClickListener(v -> launchDashboardServer());
        }
        
        if (mBtnSetupDashboard != null) {
            mBtnSetupDashboard.setOnClickListener(v -> setupDashboard());
        }

        mHandler.post(mRefreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacks(mRefreshRunnable);
    }

    private void setupRecyclerView() {
        mAdapter = new ServiceAdapter(mRegistry.getServices(), this::onServiceAction);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);
    }

    private void setupWebView() {
        if (mWebView != null) {
            mWebView.setBackgroundColor(Color.TRANSPARENT);
            mWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (url.contains("localhost:3000")) {
                        if (mBtnLaunchDashboard != null) mBtnLaunchDashboard.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    if (failingUrl.contains("localhost")) {
                        if (mBtnLaunchDashboard != null) mBtnLaunchDashboard.setVisibility(View.VISIBLE);
                        String html = "<html><head><style>" +
                                      "body { background:#121212; color:#ffffff; font-family:-apple-system,sans-serif; display:flex; flex-direction:column; justify-content:center; align-items:center; height:100vh; margin:0; padding:20px; text-align:center; overflow:hidden; }" +
                                      "h1 { color:#00E5FF; font-size:24px; margin-bottom:8px; text-transform:uppercase; letter-spacing:2px; }" +
                                      "p { opacity:0.6; font-size:14px; margin:4px 0; }" +
                                      ".box { border:1px solid rgba(0,229,255,0.3); background:rgba(0,229,255,0.05); padding:16px; border-radius:12px; margin-top:20px; width:80%; }" +
                                      "code { color:#00E5FF; font-family:monospace; font-size:13px; }" +
                                      "</style></head><body>" +
                                      "<h1>ATERMUX CONSOLE</h1>" +
                                      "<p>Proactive management dashboard</p>" +
                                      "<p style='color:#FF5252;'>No local server detected</p>" +
                                      "<div class='box'>" +
                                      "<p style='font-size:12px; margin-bottom:8px;'>Tap 'LAUNCH DASHBOARD' above or run:</p>" +
                                      "<code>node atermux-server.js</code>" +
                                      "</div>" +
                                      "</body></html>";
                        view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                    }
                }
            });
            mWebView.getSettings().setJavaScriptEnabled(true);
            mWebView.getSettings().setDomStorageEnabled(true);
            mWebView.loadUrl("http://localhost:3000");
        }
    }

    private void launchDashboardServer() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null || activity.getTermuxService() == null) return;

        // Run the node command in the background
        activity.getTermuxService().createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", "node atermux-server.js"},
            null, null, false, "Atermux Dashboard Server");

        Toast.makeText(getContext(), "Launching Atermux Dashboard...", Toast.LENGTH_SHORT).show();
        
        // Refresh WebView after a short delay to give the server time to start
        mHandler.postDelayed(() -> {
            if (mWebView != null) mWebView.loadUrl("http://localhost:3000");
        }, 3000);
    }

    private void setupDashboard() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null || activity.getTermuxService() == null) return;

        String template = "const http = require('http');\n" +
                "const server = http.createServer((req, res) => {\n" +
                "  res.writeHead(200, { 'Content-Type': 'text/html' });\n" +
                "  res.end(`\n" +
                "    <!DOCTYPE html>\n" +
                "    <html>\n" +
                "    <head>\n" +
                "        <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "        <style>\n" +
                "            body { background: #0a0a0b; color: #ffffff; font-family: -apple-system, sans-serif; height: 100vh; margin: 0; display: flex; justify-content: center; align-items: center; }\n" +
                "            .card { background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(0, 255, 255, 0.2); padding: 30px; border-radius: 20px; text-align: center; backdrop-filter: blur(10px); width: 80%; }\n" +
                "            h1 { color: #00ffff; font-size: 24px; margin: 0 0 10px 0; letter-spacing: 2px; }\n" +
                "            .status { display: inline-block; padding: 4px 12px; border-radius: 20px; background: rgba(0, 255, 0, 0.1); color: #00ff00; font-size: 12px; margin-top: 10px; }\n" +
                "            .info { margin-top: 20px; font-size: 14px; opacity: 0.6; }\n" +
                "        </style>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <div class='card'>\n" +
                "            <h1>ATERMUX PRO</h1>\n" +
                "            <div class='status'>SYSTEM ONLINE</div>\n" +
                "            <div class='info'>Server running from local Termux node</div>\n" +
                "            <p style='font-size: 12px; opacity: 0.4; margin-top: 30px;'>Atermux Pro v1.0 • Built for Performance</p>\n" +
                "        </div>\n" +
                "    </body>\n" +
                "    </html>\n" +
                "  `);\n" +
                "});\n" +
                "server.listen(3000, 'localhost', () => {\n" +
                "  console.log('Atermux Console running at http://localhost:3000/');\n" +
                "});";

        // Write the template to a file
        String createCmd = "cat <<EOF > atermux-server.js\n" + template + "\nEOF";
        
        activity.getTermuxService().createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", createCmd},
            null, null, false, "Initializing Atermux Dashboard");

        Toast.makeText(getContext(), "Initializing Dashboard Server...", Toast.LENGTH_SHORT).show();
        
        // Launch it immediately after creation
        mHandler.postDelayed(this::launchDashboardServer, 2000);
    }

    private void refreshStatus() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null) return;
        TermuxService service = activity.getTermuxService();
        if (service == null) return;

        mRegistry.refreshStates(service, () -> {
            activity.runOnUiThread(() -> {
                mAdapter.notifyDataSetChanged();
                int sessionCount = service.getTermuxSessions().size();
                mStatusText.setText("System Ready • " + sessionCount + " Active Sessions");
            });
        });
    }

    private void onServiceAction(ManagedService service) {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null) return;
        TermuxService termuxService = activity.getTermuxService();
        if (termuxService == null) return;

        switch (service.getState()) {
            case UNINSTALLED:
                installService(service, termuxService);
                break;
            case STOPPED:
                startService(service, termuxService);
                break;
            case RUNNING:
                stopService(service, termuxService);
                break;
        }
    }

    private void installService(ManagedService service, TermuxService termuxService) {
        service.setState(ManagedService.ServiceState.ACTION_IN_PROGRESS);
        mAdapter.notifyDataSetChanged();

        String installCmd = "pkg install -y " + service.getPackageName();
        termuxService.createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", installCmd},
            null, null, false, "Installing " + service.getTitle());

        Toast.makeText(getContext(), "Installing " + service.getTitle() + " in background...", Toast.LENGTH_LONG).show();
    }

    private void startService(ManagedService service, TermuxService termuxService) {
        if (service.getStartCmd() == null) return;
        
        service.setState(ManagedService.ServiceState.ACTION_IN_PROGRESS);
        mAdapter.notifyDataSetChanged();

        termuxService.createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", service.getStartCmd()},
            null, null, false, "Starting " + service.getTitle());
    }

    private void stopService(ManagedService service, TermuxService termuxService) {
        if (service.getStopCmd() == null) return;
        
        service.setState(ManagedService.ServiceState.ACTION_IN_PROGRESS);
        mAdapter.notifyDataSetChanged();

        termuxService.createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", service.getStopCmd()},
            null, null, false, "Stopping " + service.getTitle());
    }

    /**
     * Service Adapter
     */
    static class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ViewHolder> {
        private final List<ManagedService> services;
        private final OnServiceListener listener;

        interface OnServiceListener {
            void onAction(ManagedService service);
        }

        ServiceAdapter(List<ManagedService> services, OnServiceListener listener) {
            this.services = services;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_managed_service, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ManagedService service = services.get(position);
            holder.title.setText(service.getTitle());
            holder.pkg.setText("pkg: " + service.getPackageName());
            holder.icon.setImageResource(service.getIconResId());
            
            updateUIState(holder, service);

            holder.btnAction.setOnClickListener(v -> listener.onAction(service));
            
            if (service.getConfigPath() != null && service.getState() != ManagedService.ServiceState.UNINSTALLED) {
                holder.btnConfig.setVisibility(View.VISIBLE);
                holder.btnConfig.setOnClickListener(v -> {
                    // Start an editor in the terminal
                    TermuxActivity activity = (TermuxActivity) v.getContext();
                    activity.getTermuxService().createTermuxSession(
                        TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
                        new String[]{"-c", "nano " + service.getConfigPath()},
                        null, null, false, "Editing " + service.getTitle() + " Config");
                    Toast.makeText(v.getContext(), "Editing config in Terminal...", Toast.LENGTH_SHORT).show();
                });
            } else {
                holder.btnConfig.setVisibility(View.GONE);
            }
        }

        private void updateUIState(ViewHolder holder, ManagedService service) {
            holder.progress.setVisibility(View.GONE);
            holder.btnAction.setEnabled(true);
            holder.statusBadge.setVisibility(View.VISIBLE);

            switch (service.getState()) {
                case UNINSTALLED:
                    holder.statusBadge.setText("Missing");
                    holder.statusBadge.setChipBackgroundColorResource(android.R.color.darker_gray);
                    holder.btnAction.setText("Install");
                    break;
                case INSTALLED:
                    holder.statusBadge.setText("Installed");
                    holder.statusBadge.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#3300FFFF"))); // Faded Cyan
                    holder.btnAction.setText("Installed");
                    holder.btnAction.setEnabled(false);
                    break;
                case STOPPED:
                    holder.statusBadge.setText("Stopped");
                    holder.statusBadge.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#33FF0000"))); // Faded Red
                    holder.btnAction.setText("Start");
                    break;
                case RUNNING:
                    holder.statusBadge.setText("Running");
                    holder.statusBadge.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#3300FF00"))); // Faded Green
                    holder.btnAction.setText("Stop");
                    if (service.isToolOnly()) {
                        holder.btnAction.setText("Installed");
                        holder.btnAction.setEnabled(false);
                        holder.statusBadge.setText("Active");
                    }
                    break;
                case ACTION_IN_PROGRESS:
                    holder.statusBadge.setVisibility(View.INVISIBLE);
                    holder.progress.setVisibility(View.VISIBLE);
                    holder.btnAction.setEnabled(false);
                    break;
            }
        }

        @Override
        public int getItemCount() { return services.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, pkg;
            ImageView icon;
            Chip statusBadge;
            MaterialButton btnAction, btnConfig;
            ProgressBar progress;

            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.service_title);
                pkg = view.findViewById(R.id.service_package);
                icon = view.findViewById(R.id.service_icon);
                statusBadge = view.findViewById(R.id.status_badge);
                btnAction = view.findViewById(R.id.btn_action);
                btnConfig = view.findViewById(R.id.btn_config);
                progress = view.findViewById(R.id.service_progress);
            }
        }
    }
}
