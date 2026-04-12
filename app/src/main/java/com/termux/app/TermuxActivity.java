package com.termux.app;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.fragments.TerminalFragment;
import com.termux.app.fragments.ConsoleFragment;
import com.termux.app.fragments.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.fragment.app.Fragment;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import java.util.Arrays;

/**
 * A terminal emulator activity.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG_TERMINAL = "TERMINAL_FRAGMENT";
    private static final String TAG_CONSOLE = "CONSOLE_FRAGMENT";
    private static final String TAG_SETTINGS = "SETTINGS_FRAGMENT";
    private String mCurrentFragmentTag;

    public static Intent newInstance(Context context) {
        return new Intent(context, TermuxActivity.class);
    }

    public static void startTermuxActivity(Context context) {
        context.startActivity(newInstance(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static void updateTermuxActivityStyling(Context context, boolean reloadFromProperties) {
        Intent intent = new Intent(com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    TermuxService mTermuxService;
    TerminalView mTerminalView;
    TermuxTerminalViewClient mTermuxTerminalViewClient;
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxAppSharedProperties mProperties;
    TermuxActivityRootView mTermuxActivityRootView;
    View mTermuxActivityBottomSpaceView;
    ExtraKeysView mExtraKeysView;
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    TermuxSessionsListViewController mTermuxSessionListViewController;
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    private DrawerLayout mDrawerLayout;
    Toast mLastToast;
    private boolean mIsVisible;
    private boolean mIsOnResumeAfterOnCreate = false;
    private boolean mIsActivityRecreated = false;
    private boolean mIsInvalidState;
    private boolean mIsInitialSessionCreated = false;
    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;
        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();
        setActivityTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux);
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            mIsInvalidState = true;
            return;
        }
        setMargins();
        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());
        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });
        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        try {
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this, getString(e.getMessage() != null && e.getMessage().contains("app is in background") ? R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general), true);
            mIsInvalidState = true;
            return;
        }
        TermuxUtils.sendTermuxOpenedBroadcast(this);
        setupBottomNavigation();
        setupKeyboardListener();
        if (savedInstanceState == null) {
            switchFragment(TAG_TERMINAL);
        } else {
            mCurrentFragmentTag = savedInstanceState.getString("current_fragment_tag", TAG_TERMINAL);
            // On recreation, child fragments might already exist in FM. 
            // The switchFragment logic handles showing them correctly.
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mIsInvalidState) return;
        mIsVisible = true;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStart();
        if (mPreferences.isTerminalMarginAdjustmentEnabled()) addTermuxActivityRootViewGlobalLayoutListener();
        registerTermuxActivityBroadcastReceiver();
        handleInitialSessionCreation();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsInvalidState) return;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onResume();
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mIsInvalidState) return;
        mIsVisible = false;
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onStop();
        removeTermuxActivityRootViewGlobalLayoutListener();
        unregisterTermuxActivityBroadcastReceiver();
        DrawerLayout drawer = getDrawer();
        if (drawer != null) drawer.closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mIsInvalidState) return;
        if (mTermuxService != null) {
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }
        try { unbindService(this); } catch (Exception e) {}
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("current_fragment_tag", mCurrentFragmentTag);
        super.onSaveInstanceState(outState);
        saveTerminalToolbarTextInput(outState);
        outState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        setTermuxSessionsListView();
        handleInitialSessionCreation();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        finishActivityIfNotFinishing();
    }

    private void handleInitialSessionCreation() {
        if (mTermuxService == null || mTermuxTerminalSessionActivityClient == null || !mIsVisible || mIsInitialSessionCreated) return;
        if (mTermuxService.isTermuxSessionsEmpty()) {
            mIsInitialSessionCreated = true;
            final Intent intent = getIntent();
            setIntent(null);
            TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                if (mTermuxService == null || mTermuxTerminalSessionActivityClient == null) return;
                try {
                    boolean launchFailsafe = (intent != null && intent.getExtras() != null) && intent.getExtras().getBoolean(com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                    mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
                    if (mTerminalView != null) { mTerminalView.requestFocus(); mTerminalView.bringToFront(); }
                } catch (WindowManager.BadTokenException e) {}
            });
        } else {
            mIsInitialSessionCreated = true;
            mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
            if (mTerminalView != null) { mTerminalView.requestFocus(); mTerminalView.bringToFront(); }
        }
    }

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityTheme() {
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        if (relativeLayout == null) return;
        int marginHorizontal = mProperties.getTerminalMarginHorizontal();
        int marginVertical = mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void onTerminalFragmentCreated(android.view.View view) {
        mDrawerLayout = view.findViewById(R.id.drawer_layout);
        setTermuxTerminalViewAndClients(view);
        if (mTerminalView != null) registerForContextMenu(mTerminalView);
        setTerminalToolbarView(null);
        setSettingsButtonView(view);
        setNewSessionButtonView(view);
        setToggleKeyboardView(view);
        setAtermuxDashboardView(view);
        setTermuxSessionsListView(view);
        handleInitialSessionCreation();
    }

    private void setTermuxTerminalViewAndClients(android.view.View fragmentView) {
        mTermuxTerminalSessionActivityClient = new com.termux.app.terminal.TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new com.termux.app.terminal.TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTerminalView = fragmentView.findViewById(R.id.terminal_view);
        if (mTerminalView != null) mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        if (mTermuxTerminalViewClient != null) mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null) mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() { setTermuxSessionsListView(null); }
    private void setTermuxSessionsListView(android.view.View view) {
        if (mTermuxService == null) return;
        ListView termuxSessionsListView = (view != null) ? view.findViewById(R.id.terminal_sessions_list) : findViewById(R.id.terminal_sessions_list);
        if (termuxSessionsListView == null) return;
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);
        final androidx.viewpager.widget.ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(android.view.View.VISIBLE);
        android.view.ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        setTerminalToolbarHeight();
        String savedTextInput = savedInstanceState != null ? savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT) : null;
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final androidx.viewpager.widget.ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
        android.view.ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight * (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) * mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final androidx.viewpager.widget.ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;
        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        com.termux.shared.logger.Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? android.view.View.VISIBLE : android.view.View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            android.view.View textInput = findViewById(R.id.terminal_toolbar_text_input);
            if (textInput != null) textInput.requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        final android.widget.EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView(android.view.View view) {
        android.widget.ImageButton settingsButton = view.findViewById(R.id.settings_button);
        if (settingsButton != null) settingsButton.setOnClickListener(v -> com.termux.shared.activity.ActivityUtils.startActivity(this, new android.content.Intent(this, com.termux.app.activities.SettingsActivity.class)));
    }

    private void setNewSessionButtonView(android.view.View view) {
        android.view.View newSessionButton = view.findViewById(R.id.new_session_button);
        if (newSessionButton != null) {
            newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
            newSessionButton.setOnLongClickListener(v -> {
                com.termux.shared.termux.interact.TextInputDialogUtils.textInput(this, R.string.title_create_named_session, null, R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text), R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text), -1, null, null);
                return true;
            });
        }
    }

    private void setToggleKeyboardView(android.view.View view) {
        android.view.View toggleButton = view.findViewById(R.id.toggle_keyboard_button);
        if (toggleButton != null) toggleButton.setOnClickListener(v -> { mTermuxTerminalViewClient.onToggleSoftKeyboardRequest(); if (getDrawer() != null) getDrawer().closeDrawers(); });
    }

    private void setAtermuxDashboardView(android.view.View view) {
        final android.view.View btnProot = view.findViewById(R.id.btn_install_proot);
        final android.view.View btnGit = view.findViewById(R.id.btn_install_git);
        final android.view.View btnSsh = view.findViewById(R.id.btn_install_ssh);
        final android.view.View btnCloudflared = view.findViewById(R.id.btn_install_cloudflared);
        android.view.View.OnClickListener dashClickListener = v -> {
            com.termux.terminal.TerminalSession currentSession = getCurrentSession();
            if (currentSession != null) {
                String cmd = "";
                if (v == btnProot) cmd = "pkg install -y proot-distro\r";
                else if (v == btnGit) cmd = "pkg install -y git\r";
                else if (v == btnSsh) cmd = "pkg install -y openssh\r";
                else if (v == btnCloudflared) cmd = "pkg install -y cloudflared\r";
                if (!cmd.isEmpty()) {
                    byte[] cmdBytes = cmd.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    currentSession.write(cmdBytes, 0, cmdBytes.length);
                }
            }
            if (getDrawer() != null) getDrawer().closeDrawers();
        };
        if (btnProot != null) btnProot.setOnClickListener(dashClickListener);
        if (btnGit != null) btnGit.setOnClickListener(dashClickListener);
        if (btnSsh != null) btnSsh.setOnClickListener(dashClickListener);
        if (btnCloudflared != null) btnCloudflared.setOnClickListener(dashClickListener);
    }

    public void switchToTerminal() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.navigation_terminal);
    }

    private void switchFragment(String tag) {
        if (tag == null || tag.equals(mCurrentFragmentTag)) return;
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.FragmentTransaction ft = fm.beginTransaction();
        if (mCurrentFragmentTag != null) {
            Fragment current = fm.findFragmentByTag(mCurrentFragmentTag);
            if (current != null) ft.hide(current);
        }
        Fragment next = fm.findFragmentByTag(tag);
        if (next == null) {
            if (TAG_TERMINAL.equals(tag)) next = new TerminalFragment();
            else if (TAG_CONSOLE.equals(tag)) next = new ConsoleFragment();
            else if (TAG_SETTINGS.equals(tag)) next = new SettingsFragment();
            if (next != null) ft.add(R.id.fragment_container, next, tag);
        } else { ft.show(next); }
        ft.commit();
        mCurrentFragmentTag = tag;
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) return;
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_terminal) { switchFragment(TAG_TERMINAL); return true; }
            else if (itemId == R.id.navigation_console) { switchFragment(TAG_CONSOLE); return true; }
            else if (itemId == R.id.navigation_settings) { switchFragment(TAG_SETTINGS); return true; }
            return false;
        });
        updateTerminalBadge();
    }

    private void setupKeyboardListener() {
        final View rootView = findViewById(R.id.activity_termux_root_view);
        final View navContainer = findViewById(R.id.floating_nav_container);
        if (rootView == null || navContainer == null) return;

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            // If keyboard is visible, hide the floating nav bar
            if (keypadHeight > screenHeight * 0.15) {
                if (navContainer.getVisibility() != View.GONE) {
                    navContainer.setVisibility(View.GONE);
                }
            } else {
                if (navContainer.getVisibility() != View.VISIBLE) {
                    navContainer.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void updateTerminalBadge() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null || mTermuxService == null) return;

        int sessionCount = mTermuxService.getTermuxSessionsSize();
        if (sessionCount > 0) {
            com.google.android.material.badge.BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.navigation_terminal);
            badge.setVisible(true);
            badge.setNumber(sessionCount);
            badge.setBackgroundColor(getColor(R.color.atermux_cyan));
            badge.setBadgeTextColor(getColor(R.color.black));
        } else {
            bottomNav.removeBadge(R.id.navigation_terminal);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = getDrawer();
        if (drawer != null && (drawer.isDrawerOpen(Gravity.LEFT) || drawer.isDrawerOpen(Gravity.RIGHT) || drawer.isDrawerOpen(Gravity.END))) {
            drawer.closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        if (!TermuxActivity.this.isFinishing()) finish();
    }

    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;
        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();
        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText())) menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled) menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { mTerminalView.showContextMenu(); return false; }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();
        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID: mTermuxTerminalViewClient.showUrlSelection(); return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID: mTermuxTerminalViewClient.shareSessionTranscript(); return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT: mTermuxTerminalViewClient.shareSelectedText(); return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME: mTerminalView.requestAutoFillUsername(); return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD: mTerminalView.requestAutoFillPassword(); return true;
            case CONTEXT_MENU_HELP_ID: ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class)); return true;
            case CONTEXT_MENU_SETTINGS_ID: ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)); return true;
            default: return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) { super.onContextMenuClosed(menu); }

    private void onResetTerminalSession(TerminalSession session) { if (session != null) { session.reset(); showToast("Terminal reset", false); } }
    private void showKillSessionDialog(TerminalSession session) { if (session == null) return; new AlertDialog.Builder(this).setTitle("Kill process").setMessage("Are you sure?").setPositiveButton(android.R.string.yes, (dialog, which) -> { session.finishIfRunning(); }).setNegativeButton(android.R.string.no, null).show(); }
    private void showStylingDialog() { showToast("Styling dialog not available in this fork", true); }
    private void toggleKeepScreenOn() { boolean newValue = !mPreferences.shouldKeepScreenOn(); mPreferences.setKeepScreenOn(newValue); if (newValue) { getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); showToast("Enabling keep screen on", true); } else { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); showToast("Disabling keep screen on", true); } }
    public void showTermuxSessionsList() { DrawerLayout drawer = getDrawer(); if (drawer != null) drawer.openDrawer(Gravity.LEFT); }
    public void reloadActivity() { recreate(); }
    public DrawerLayout getDrawer() { return mDrawerLayout; }
    public TermuxService getTermuxService() { return mTermuxService; }
    public TerminalView getTerminalView() { return mTerminalView; }
    public TermuxTerminalViewClient getTermuxTerminalViewClient() { return mTermuxTerminalViewClient; }
    public TerminalSession getCurrentSession() { if (mTerminalView != null) return mTerminalView.getCurrentSession(); return null; }
    public TermuxActivityRootView getTermuxActivityRootView() { return mTermuxActivityRootView; }
    public View getTermuxActivityBottomSpaceView() { return mTermuxActivityBottomSpaceView; }
    public boolean isTerminalToolbarTextInputViewSelected() { ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager(); return terminalToolbarViewPager != null && terminalToolbarViewPager.getCurrentItem() == 1; }
    public ViewPager getTerminalToolbarViewPager() { return findViewById(R.id.terminal_toolbar_view_pager); }
    public TermuxAppSharedProperties getProperties() { return mProperties; }
    public TermuxAppSharedPreferences getPreferences() { return mPreferences; }
    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() { return mTermuxTerminalExtraKeys; }
    public float getTerminalToolbarDefaultHeight() { return mTerminalToolbarDefaultHeight; }
    public void setExtraKeysView(ExtraKeysView extraKeysView) { mExtraKeysView = extraKeysView; }
    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() { return mTermuxTerminalSessionActivityClient; }
    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionActivityClient() { return mTermuxTerminalSessionActivityClient; }
    public boolean isVisible() { return mIsVisible; }
    public boolean isActivityRecreated() { return mIsActivityRecreated; }
    public boolean isOnResumeAfterOnCreate() { return mIsOnResumeAfterOnCreate; }
    public int getNavBarHeight() { return mNavBarHeight; }
    public ExtraKeysView getExtraKeysView() { return mExtraKeysView; }
    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null) mTermuxSessionListViewController.notifyDataSetChanged();
        updateTerminalBadge();
    }
    public boolean isTerminalViewSelected() { return TAG_TERMINAL.equals(mCurrentFragmentTag); }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (DataUtils.isNullOrEmpty(action)) return;
            switch (action) {
                case com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE: reloadProperties(); reloadActivity(); break;
            }
        }
    }
    
    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        try { unregisterReceiver(mTermuxActivityBroadcastReceiver); } catch (Exception e) {}
    }
}
