package com.termux.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.termux.R;
import android.content.Intent;
import android.widget.Toast;
import com.termux.app.activities.SettingsActivity;

public class SettingsFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        view.findViewById(R.id.btn_native_settings).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btn_toggle_tunnel).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Cloudflare Tunnel logic integration pending...", Toast.LENGTH_SHORT).show();
        });
    }
}


