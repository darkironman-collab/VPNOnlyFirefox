package com.extremeos.vpnonlybrowser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
        vpnBadge.setText("● VPN protected");
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
        vpnBadge.setText("● VPN disconnected");
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
        root.setBackgroundColor(Color.rgb(18, 11, 36));

        vpnBadge = text("● Checking VPN…", 13, Color.LTGRAY);
        vpnBadge.setPadding(dp(14), dp(8), dp(14), dp(4));
        root.addView(vpnBadge);

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(8), dp(4), dp(8), dp(8));
        Button back = button("‹");
        Button forward = button("›");
        Button reload = button("↻");
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or enter address");
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.GRAY);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { navigate(); return true; }
            return false;
        });
        back.setOnClickListener(v -> { if (vpnReady && session != null) session.goBack(); });
        forward.setOnClickListener(v -> { if (vpnReady && session != null) session.goForward(); });
        reload.setOnClickListener(v -> { if (vpnReady && session != null) session.reload(); });
        bar.addView(back); bar.addView(forward);
        bar.addView(address, new LinearLayout.LayoutParams(0, dp(52), 1));
        bar.addView(reload);
        root.addView(bar);

        FrameLayout content = new FrameLayout(this);
        browserHost = new FrameLayout(this);
        geckoView = new GeckoView(this);
        browserHost.addView(geckoView, match());
        content.addView(browserHost, match());

        lockPanel = new LinearLayout(this);
        lockPanel.setOrientation(LinearLayout.VERTICAL);
        lockPanel.setGravity(Gravity.CENTER);
        lockPanel.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView title = text("VPN required", 28, Color.WHITE);
        TextView body = text("Internet access is locked. Connect any VPN, then this browser will unlock automatically.", 16, Color.LTGRAY);
        body.setGravity(Gravity.CENTER); body.setPadding(0, dp(14), 0, dp(20));
        Button chooseVpnButton = button("Choose VPN app");
        chooseVpnButton.setOnClickListener(v -> chooseVpnApp());
        Button settingsButton = button("Android VPN settings");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)));
        lockPanel.addView(title); lockPanel.addView(body);
        lockPanel.addView(chooseVpnButton); lockPanel.addView(settingsButton);
        content.addView(lockPanel, match());
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v;
    }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
