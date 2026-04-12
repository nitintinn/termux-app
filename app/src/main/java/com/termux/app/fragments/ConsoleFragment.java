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
import android.view.LayoutInflater;
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

        mRegistry = new AtermuxServiceRegistry();
        setupRecyclerView();
        
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
            });
        });
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

        if (context instanceof TermuxActivity) {
            ((TermuxActivity) context).runOnUiThread(() -> {
                if (mTvRamStats != null) mTvRamStats.setText(formatSize(usedRam) + " / " + formatSize(totalRam));
                if (mProgressRam != null) mProgressRam.setProgress(ramPercent, true);

                if (mTvStorageStats != null) mTvStorageStats.setText(formatSize(usedStorage) + " / " + formatSize(totalStorage));
                if (mProgressStorage != null) mProgressStorage.setProgress(storagePercent, true);
            });
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
