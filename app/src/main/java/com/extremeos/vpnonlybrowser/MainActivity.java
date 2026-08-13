package com.extremeos.vpnonlybrowser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends AppCompatActivity {
    private static GeckoRuntime runtime;

    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback networkCallback;
    private GeckoView geckoView;
    private GeckoSession session;
    private FrameLayout browserHost;
    private LinearLayout lockPanel;
    private TextView vpnBadge;
    private EditText address;
    private boolean vpnReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        buildUi();
        if (runtime == null) runtime = GeckoRuntime.create(getApplicationContext());
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(@NonNull Network network,
                                                        @NonNull NetworkCapabilities caps) {
                updateVpnState(isUsableVpn(caps));
            }
            @Override public void onLost(@NonNull Network network) { updateVpnState(false); }
        };
    }

    @Override protected void onStart() {
        super.onStart();
        updateVpnState(currentVpnState());
        connectivity.registerDefaultNetworkCallback(networkCallback);
    }

    @Override protected void onStop() {
        try { connectivity.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) {}
        // Closing on background prevents an unseen session retaining sockets.
        lockBrowser();
        super.onStop();
    }

    private boolean currentVpnState() {
        Network active = connectivity.getActiveNetwork();
        return active != null && isUsableVpn(connectivity.getNetworkCapabilities(active));
    }

    private boolean isUsableVpn(NetworkCapabilities caps) {
        return caps != null
                && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateVpnState(boolean ready) {
        runOnUiThread(() -> {
            if (ready == vpnReady && ((ready && session != null) || (!ready && session == null))) return;
            vpnReady = ready;
            if (ready) unlockBrowser(); else lockBrowser();
        });
    }

    private void unlockBrowser() {
        vpnBadge.setText("●");
        vpnBadge.setTextColor(Color.rgb(74, 222, 128));
        lockPanel.setVisibility(View.GONE);
        browserHost.setVisibility(View.VISIBLE);
        if (session == null) {
            session = new GeckoSession();
            session.setContentDelegate(new GeckoSession.ContentDelegate() {});
            session.open(runtime);
            geckoView.setSession(session);
        }
    }

    private void lockBrowser() {
        vpnReady = false;
        if (session != null) {
            session.stop();
            session.close();
            session = null;
        }
        geckoView.releaseSession();
        browserHost.setVisibility(View.GONE);
        lockPanel.setVisibility(View.VISIBLE);
        vpnBadge.setText("●");
        vpnBadge.setTextColor(Color.rgb(248, 113, 113));
    }

    private void navigate() {
        if (!vpnReady || session == null) { lockBrowser(); return; }
        String value = address.getText().toString().trim();
        if (value.isEmpty()) return;
        String uri = value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")
                ? value : "https://" + value;
        session.loadUri(uri);
    }

    private void chooseVpnApp() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(VpnService.SERVICE_INTERFACE);
        List<ResolveInfo> services = pm.queryIntentServices(query, PackageManager.MATCH_ALL);
        Map<String, String> apps = new LinkedHashMap<>();
        for (ResolveInfo info : services) {
            if (info.serviceInfo == null) continue;
            String packageName = info.serviceInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            apps.put(packageName, label == null ? packageName : label.toString());
        }

        if (apps.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No VPN apps found")
                    .setMessage("Install a VPN app, or configure Android's built-in VPN settings.")
                    .setPositiveButton("Open VPN settings", (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        List<String> packages = new ArrayList<>(apps.keySet());
        String[] labels = packages.stream().map(apps::get).toArray(String[]::new);
        new AlertDialog.Builder(this)
                .setTitle("Choose VPN app")
                .setItems(labels, (dialog, which) -> {
                    Intent launch = pm.getLaunchIntentForPackage(packages.get(which));
                    if (launch != null) startActivity(launch);
                    else startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
                })
                .setNeutralButton("VPN settings", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(28, 27, 34));

        FrameLayout content = new FrameLayout(this);
        browserHost = new FrameLayout(this);
        geckoView = new GeckoView(this);
        browserHost.addView(geckoView, match());
        content.addView(browserHost, match());

        lockPanel = new LinearLayout(this);
        lockPanel.setOrientation(LinearLayout.VERTICAL);
        lockPanel.setGravity(Gravity.CENTER);
        lockPanel.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView title = text("Connect to a VPN", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView body = text("VPN Only Firefox keeps browsing locked until Android confirms a protected VPN connection.", 16, Color.rgb(201, 200, 207));
        body.setGravity(Gravity.CENTER); body.setPadding(0, dp(14), 0, dp(22));
        Button chooseVpnButton = button("Choose VPN app");
        chooseVpnButton.setOnClickListener(v -> chooseVpnApp());
        Button settingsButton = button("VPN settings");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)));
        lockPanel.addView(title); lockPanel.addView(body);
        lockPanel.addView(chooseVpnButton); lockPanel.addView(settingsButton);
        content.addView(lockPanel, match());
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        toolbar.setBackgroundColor(Color.rgb(43, 42, 51));
        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        vpnBadge = icon("●", 15);
        vpnBadge.setTextColor(Color.rgb(46, 213, 115));
        vpnBadge.setContentDescription("VPN status");
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or enter address");
        address.setTextSize(16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(183, 181, 191));
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(dp(8), 0, dp(6), 0);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { navigate(); return true; }
            return false;
        });
        TextView reload = icon("↻", 24);
        reload.setOnClickListener(v -> { if (vpnReady && session != null) session.reload(); });
        addressRow.addView(vpnBadge, new LinearLayout.LayoutParams(dp(36), dp(48)));
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(48), 1));
        addressRow.addView(reload, new LinearLayout.LayoutParams(dp(42), dp(48)));
        addressRow.setBackground(roundRect(Color.rgb(56, 55, 64), 24));
        toolbar.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        TextView back = icon("‹", 31), forward = icon("›", 31);
        TextView home = icon("⌂", 24), tabs = icon("▢", 22), menu = icon("⋮", 28);
        back.setOnClickListener(v -> { if (vpnReady && session != null) session.goBack(); });
        forward.setOnClickListener(v -> { if (vpnReady && session != null) session.goForward(); });
        home.setOnClickListener(v -> { address.setText("about:blank"); navigate(); });
        menu.setOnClickListener(v -> chooseVpnApp());
        nav.addView(back, weighted()); nav.addView(forward, weighted());
        nav.addView(home, weighted()); nav.addView(tabs, weighted()); nav.addView(menu, weighted());
        toolbar.addView(nav, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(toolbar);
        setContentView(root);
    }

    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v;
    }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private TextView icon(String value, int sp) {
        TextView v = text(value, sp, Color.WHITE); v.setGravity(Gravity.CENTER); return v;
    }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, dp(48), 1); }
    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d;
    }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
