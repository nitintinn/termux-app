package com.termux.app.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.ActivityManager;
import android.os.Environment;
import android.os.StatFs;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputType;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.progressindicator.LinearProgressIndicator;

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
    private TextView mTvRamStats, mTvStorageStats, mTvCpuStats;
    private LinearProgressIndicator mProgressRam, mProgressStorage, mProgressCpu;
    
    // Hosting Wizard UI
    private View mLayoutLiveUrl;
    private TextView mTvLiveUrl, mTvHostingDesc;
    private MaterialButton mBtnWizardGoLive, mBtnCopyUrl, mBtnOpenUrl;
    private ImageButton mBtnHostingSettings;
    
    // SharedPreferences Keys
    private static final String PREF_REPO_URL = "hosting_repo_url";
    private static final String PREF_CUSTOM_DOMAIN = "hosting_custom_domain";
    private static final String PREF_GIT_TOKEN = "git_token";
    
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
        mRecyclerView = view.findViewById(R.id.recycler_quick_actions); 
        
        mTvRamStats = view.findViewById(R.id.tv_ram_stats);
        mTvStorageStats = view.findViewById(R.id.tv_storage_stats);
        mTvCpuStats = view.findViewById(R.id.tv_cpu_stats);
        mProgressRam = view.findViewById(R.id.progress_ram);
        mProgressStorage = view.findViewById(R.id.progress_storage);
        mProgressCpu = view.findViewById(R.id.progress_cpu);

        // Hosting Hub Binding
        mLayoutLiveUrl = view.findViewById(R.id.layout_live_url);
        mTvLiveUrl = view.findViewById(R.id.tv_live_url);
        mTvHostingDesc = view.findViewById(R.id.tv_hosting_desc);
        mBtnWizardGoLive = view.findViewById(R.id.btn_wizard_go_live);
        mBtnCopyUrl = view.findViewById(R.id.btn_copy_url);
        mBtnOpenUrl = view.findViewById(R.id.btn_open_url);
        mBtnHostingSettings = view.findViewById(R.id.btn_hosting_settings);

        mRegistry = new AtermuxServiceRegistry();
        setupRecyclerView();
        
        if (mBtnWizardGoLive != null) {
            mBtnWizardGoLive.setOnClickListener(v -> startHostingWizard());
        }

        if (mBtnHostingSettings != null) {
            mBtnHostingSettings.setOnClickListener(v -> showProConfigDialog());
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

    private void refreshStatus() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null) return;
        TermuxService service = activity.getTermuxService();
        if (service == null) return;

        updateNativeSystemStats(activity);

        mRegistry.refreshStates(service, () -> {
            activity.runOnUiThread(() -> {
                mAdapter.notifyDataSetChanged();
                int sessionCount = service.getTermuxSessionsSize();
                mStatusText.setText("System Ready • " + sessionCount + " Active Sessions");
                
                updateHostingHubUi();
            });
        });
    }

    private void updateHostingHubUi() {
        ManagedService cfService = findService("Cloudflare Tunnel");
        if (cfService == null) return;

        if (cfService.getState() == ManagedService.ServiceState.RUNNING) {
            mBtnWizardGoLive.setText("STOP HOSTING");
            mBtnWizardGoLive.setOnClickListener(v -> stopService(cfService, ((TermuxActivity)getActivity()).getTermuxService()));
            mTvHostingDesc.setText("Your website is currently LIVE.");
            
            String url = cfService.getPublicUrl();
            
            // If we have a custom domain in prefs, use it instead of log-scraped one
            SharedPreferences prefs = getContext().getSharedPreferences("atermux_prefs", Context.MODE_PRIVATE);
            String customDomain = prefs.getString(PREF_CUSTOM_DOMAIN, "");
            if (!customDomain.isEmpty()) {
                url = "https://" + customDomain;
            }

            if (url != null && !url.isEmpty()) {
                mLayoutLiveUrl.setVisibility(View.VISIBLE);
                mTvLiveUrl.setText(url);
                final String finalUrl = url;
                mBtnCopyUrl.setOnClickListener(v -> copyToClipboard(finalUrl));
                mBtnOpenUrl.setOnClickListener(v -> openInBrowser(finalUrl));
            } else {
                mLayoutLiveUrl.setVisibility(View.VISIBLE);
                mTvLiveUrl.setText("Detecting tunnel URL...");
            }
        } else {
            mBtnWizardGoLive.setText("START HOSTING");
            mBtnWizardGoLive.setOnClickListener(v -> startHostingWizard());
            mTvHostingDesc.setText("Turn your phone into a live web server.");
            mLayoutLiveUrl.setVisibility(View.GONE);
        }
    }

    private void startHostingWizard() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null || activity.getTermuxService() == null) return;
        TermuxService service = activity.getTermuxService();

        ManagedService cf = findService("Cloudflare Tunnel");
        if (cf == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences("atermux_prefs", Context.MODE_PRIVATE);
        String repoUrl = prefs.getString(PREF_REPO_URL, "");
        String customDomain = prefs.getString(PREF_CUSTOM_DOMAIN, "");

        Toast.makeText(getContext(), "Launching Advanced Hosting...", Toast.LENGTH_SHORT).show();

        // 1. Repository Setup
        String repoCmd;
        if (!repoUrl.isEmpty()) {
            repoCmd = "mkdir -p ~/www && if [ ! -d ~/www/.git ]; then rm -rf ~/www/* && git clone " + repoUrl + " ~/www; else cd ~/www && git pull; fi";
        } else {
            repoCmd = "mkdir -p ~/www && [ ! -f ~/www/index.html ] && echo '<h1>Hosted by Atermux</h1><p>Your mobile server is live!</p>' > ~/www/index.html || true";
        }
        
        // 2. Start Python server
        String serverCmd = "cd ~/www && python3 -m http.server 8080 &";
        
        // 3. Cloudflare Tunnel Command
        String tunnelCmd;
        if (!customDomain.isEmpty()) {
            // PRO Path: Named Tunnel
            // We ensure the 'atermux' tunnel exists and route the domain.
            // Note: cloudflared tunnel create 'atermux' will fail if it exists, which is fine using || true.
            tunnelCmd = "mkdir -p ~/.atermux && (cloudflared tunnel create atermux || true) && " +
                        "(cloudflared tunnel route dns atermux " + customDomain + " || true) && " +
                        "cloudflared tunnel run --url http://localhost:8080 atermux > ~/.atermux/cloudflared.log 2>&1";
        } else {
            // QUICK Path: Random URL
            tunnelCmd = cf.getStartCmd();
        }

        String fullCmd = repoCmd + " && " + serverCmd + " && " + tunnelCmd;

        service.createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", fullCmd},
            null, null, false, "Atermux Advanced Hosting");
            
        cf.setState(ManagedService.ServiceState.ACTION_IN_PROGRESS);
        mAdapter.notifyDataSetChanged();
    }

    private void showProConfigDialog() {
        SharedPreferences prefs = getContext().getSharedPreferences("atermux_prefs", Context.MODE_PRIVATE);
        String repoUrl = prefs.getString(PREF_REPO_URL, "");
        String customDomain = prefs.getString(PREF_CUSTOM_DOMAIN, "");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputRepo = new EditText(getContext());
        inputRepo.setHint("Git Repository URL");
        inputRepo.setText(repoUrl);
        layout.addView(inputRepo);

        final EditText inputDomain = new EditText(getContext());
        inputDomain.setHint("Custom Domain (e.g. site.quvantix.com)");
        inputDomain.setText(customDomain);
        inputDomain.setPadding(0, 40, 0, 0);
        layout.addView(inputDomain);

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Advanced Hosting Config")
            .setMessage("Set your website source and custom domain.")
            .setView(layout)
            .setPositiveButton("SAVE CONFIG", (dialog, which) -> {
                prefs.edit()
                    .putString(PREF_REPO_URL, inputRepo.getText().toString().trim())
                    .putString(PREF_CUSTOM_DOMAIN, inputDomain.getText().toString().trim())
                    .apply();
                Toast.makeText(getContext(), "Hosting configuration updated!", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void showGitConfigDialog() {
        SharedPreferences prefs = getContext().getSharedPreferences("atermux_prefs", Context.MODE_PRIVATE);
        String currentToken = prefs.getString(PREF_GIT_TOKEN, "");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputToken = new EditText(getContext());
        inputToken.setHint("GitHub Classic Token (PAT)");
        inputToken.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        inputToken.setText(currentToken);
        layout.addView(inputToken);

        TextView hint = new TextView(getContext());
        hint.setText("Used for private repository pull/clone.");
        hint.setTextSize(10);
        hint.setPadding(0, 10, 0, 0);
        layout.addView(hint);

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Git Version Control Config")
            .setView(layout)
            .setPositiveButton("SAVE TOKEN", (dialog, which) -> {
                prefs.edit().putString(PREF_GIT_TOKEN, inputToken.getText().toString().trim()).apply();
                Toast.makeText(getContext(), "GitHub Token saved securely.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void syncAllGitRepos() {
        TermuxActivity activity = (TermuxActivity) getActivity();
        if (activity == null || activity.getTermuxService() == null) return;
        
        SharedPreferences prefs = getContext().getSharedPreferences("atermux_prefs", Context.MODE_PRIVATE);
        String token = prefs.getString(PREF_GIT_TOKEN, "");
        
        Toast.makeText(getContext(), "Syncing all repositories...", Toast.LENGTH_SHORT).show();
        
        // Command to find and pull all repos
        // If token exists, we use it for remotes
        String pullCmd = "find ~/www -name .git -type d | while read dir; do " +
                         "repo=$(dirname $dir); echo \"Updating $repo...\"; " +
                         "cd $repo && git pull; done";
        
        activity.getTermuxService().createTermuxSession(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            new String[]{"-c", pullCmd},
            null, null, false, "Git Sync All");
    }

    private ManagedService findService(String title) {
        for (ManagedService s : mRegistry.getServices()) {
            if (s.getTitle().equals(title)) return s;
        }
        return null;
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Atermux Site", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "URL Copied!", Toast.LENGTH_SHORT).show();
    }

    private void openInBrowser(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    private void updateNativeSystemStats(Context context) {
        // RAM Stats
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        
        long totalRam = mi.totalMem;
        long availRam = mi.availMem;
        long usedRam = totalRam - availRam;
        int ramPercent = (int) (usedRam * 100 / totalRam);

        // Storage Stats
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long availBlocks = stat.getAvailableBlocksLong();
        
        long totalStorage = totalBlocks * blockSize;
        long availStorage = availBlocks * blockSize;
        long usedStorage = totalStorage - availStorage;
        int storagePercent = (int) (usedStorage * 100 / totalStorage);

        // CPU Load (from loadavg)
        String loadAvg = getCpuLoad();
        float loadVal = 0.0f;
        try { loadVal = Float.parseFloat(loadAvg); } catch (Exception e) {}
        int numCores = Runtime.getRuntime().availableProcessors();
        int cpuPercent = (int) Math.min(100, (loadVal * 100 / numCores));

        if (context instanceof TermuxActivity) {
            String finalLoadAvg = loadAvg;
            ((TermuxActivity) context).runOnUiThread(() -> {
                if (mTvRamStats != null) mTvRamStats.setText(formatSize(usedRam) + " / " + formatSize(totalRam));
                if (mProgressRam != null) mProgressRam.setProgress(ramPercent, true);

                if (mTvStorageStats != null) mTvStorageStats.setText(formatSize(usedStorage) + " / " + formatSize(totalStorage));
                if (mProgressStorage != null) mProgressStorage.setProgress(storagePercent, true);

                if (mTvCpuStats != null) mTvCpuStats.setText(finalLoadAvg);
                if (mProgressCpu != null) mProgressCpu.setProgress(cpuPercent, true);
            });
        }
    }

    private String getCpuLoad() {
        try {
            Process process = Runtime.getRuntime().exec("uptime");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.contains("load average:")) {
                // Example: 03:48:10 up 1 day, 10:20, 0 users, load average: 0.12, 0.45, 0.32
                String loadPart = line.split("load average:")[1].trim();
                return loadPart.split(",")[0].trim(); // Get the 1-minute load
            }
            return "0.00";
        } catch (IOException ex) {
            return "0.00";
        }
    }

    private String formatSize(long size) {
        String suffix = "B";
        float fSize = size;
        if (fSize >= 1024) { fSize /= 1024; suffix = "KB"; }
        if (fSize >= 1024) { fSize /= 1024; suffix = "MB"; }
        if (fSize >= 1024) { fSize /= 1024; suffix = "GB"; }
        return String.format("%.1f %s", fSize, suffix);
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

            } else if (service.getTitle().contains("Git")) {
                holder.btnConfig.setVisibility(View.VISIBLE);
                holder.btnConfig.setOnClickListener(v -> ((ConsoleFragment)v.getContext()).showGitConfigDialog());
            } else {
                holder.btnConfig.setVisibility(View.GONE);
            }

            // Insights Integration
            if (!service.getAccountStats().isEmpty()) {
                holder.statsContainer.setVisibility(View.VISIBLE);
                holder.statsDivider.setVisibility(View.VISIBLE);
                holder.statsText.setText(service.getAccountStats());
                
                // Add Sync All button if it's Git
                if (service.getTitle().contains("Git")) {
                    holder.statsText.append("\n[ TAP TO SYNC ALL PROJECTS ]");
                    holder.statsContainer.setOnClickListener(v -> syncAllGitRepos());
                } else {
                    holder.statsContainer.setOnClickListener(null);
                }
            } else {
                holder.statsContainer.setVisibility(View.GONE);
                holder.statsDivider.setVisibility(View.GONE);
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
            TextView title, pkg, statsText;
            ImageView icon;
            Chip statusBadge;
            MaterialButton btnAction, btnConfig;
            ProgressBar progress;
            View statsContainer, statsDivider;

            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.service_title);
                pkg = view.findViewById(R.id.service_package);
                icon = view.findViewById(R.id.service_icon);
                statusBadge = view.findViewById(R.id.status_badge);
                btnAction = view.findViewById(R.id.btn_action);
                btnConfig = view.findViewById(R.id.btn_config);
                progress = view.findViewById(R.id.service_progress);
                statsContainer = view.findViewById(R.id.stats_container);
                statsDivider = view.findViewById(R.id.stats_divider);
                statsText = view.findViewById(R.id.service_stats);
            }
        }
    }
}
