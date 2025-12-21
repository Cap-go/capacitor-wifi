package ee.forgr.plugin.capacitor_wifi;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(
    name = "CapacitorWifi",
    permissions = {
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION })
    }
)
public class CapacitorWifiPlugin extends Plugin {

    private final String pluginVersion = "8.0.5";

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private BroadcastReceiver scanResultsReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network boundNetwork; // Store the network we bound to for unbinding

    @Override
    public void load() {
        wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @PluginMethod
    public void addNetwork(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addNetworkModern(call);
        } else {
            addNetworkLegacy(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void addNetworkModern(PluginCall call) {
        String ssid = call.getString("ssid");
        if (ssid == null || ssid.isEmpty()) {
            call.reject("SSID is required");
            return;
        }

        String password = call.getString("password");
        Boolean isHiddenSsid = call.getBoolean("isHiddenSsid", false);
        Integer securityType = call.getInt("securityType", 2); // Default to WPA2_PSK

        try {
            // Open system settings to add network
            Intent intent = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
            WifiNetworkSuggestion.Builder suggestionBuilder = new WifiNetworkSuggestion.Builder().setSsid(ssid);

            if (isHiddenSsid != null && isHiddenSsid) {
                suggestionBuilder.setIsHiddenSsid(true);
            }

            if (password != null && !password.isEmpty()) {
                // Determine security type based on parameter
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    switch (securityType) {
                        case 1: // WEP
                            suggestionBuilder.setWpa2Passphrase(password); // WEP not supported, fallback to WPA2
                            break;
                        case 2: // WPA2_PSK
                            suggestionBuilder.setWpa2Passphrase(password);
                            break;
                        case 3: // EAP
                            // Enterprise networks require more configuration
                            suggestionBuilder.setWpa2Passphrase(password);
                            break;
                        case 4: // SAE (WPA3)
                            suggestionBuilder.setWpa3Passphrase(password);
                            break;
                        default:
                            suggestionBuilder.setWpa2Passphrase(password);
                            break;
                    }
                } else {
                    suggestionBuilder.setWpa2Passphrase(password);
                }
            }

            ArrayList<WifiNetworkSuggestion> suggestionsList = new ArrayList<>();
            suggestionsList.add(suggestionBuilder.build());

            intent.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, suggestionsList);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to add network: " + e.getMessage(), e);
        }
    }

    private void addNetworkLegacy(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "addNetworkCallback");
            return;
        }

        String ssid = call.getString("ssid");
        if (ssid == null || ssid.isEmpty()) {
            call.reject("SSID is required");
            return;
        }

        String password = call.getString("password");
        Boolean isHiddenSsid = call.getBoolean("isHiddenSsid", false);
        Integer securityType = call.getInt("securityType", 2);

        try {
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = "\"" + ssid + "\"";

            if (isHiddenSsid != null && isHiddenSsid) {
                wifiConfig.hiddenSSID = true;
            }

            if (password != null && !password.isEmpty()) {
                if (securityType == 1) {
                    // WEP
                    wifiConfig.wepKeys[0] = "\"" + password + "\"";
                    wifiConfig.wepTxKeyIndex = 0;
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                } else {
                    // WPA/WPA2
                    wifiConfig.preSharedKey = "\"" + password + "\"";
                }
            } else {
                // Open network
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            int netId = wifiManager.addNetwork(wifiConfig);
            if (netId == -1) {
                call.reject("Failed to add network");
                return;
            }

            boolean enableResult = wifiManager.enableNetwork(netId, true);
            if (!enableResult) {
                call.reject("Failed to enable network");
                return;
            }

            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to add network: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectModern(call);
        } else {
            connectLegacy(call);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void connectModern(PluginCall call) {
        String ssid = call.getString("ssid");
        if (ssid == null || ssid.isEmpty()) {
            call.reject("SSID is required");
            return;
        }

        String password = call.getString("password");
        Boolean isHiddenSsid = call.getBoolean("isHiddenSsid", false);
        Boolean autoRouteTraffic = call.getBoolean("autoRouteTraffic", false);

        try {
            WifiNetworkSpecifier.Builder specifierBuilder = new WifiNetworkSpecifier.Builder().setSsid(ssid);

            if (isHiddenSsid != null && isHiddenSsid) {
                specifierBuilder.setIsHiddenSsid(true);
            }

            if (password != null && !password.isEmpty()) {
                specifierBuilder.setWpa2Passphrase(password);
            }

            NetworkSpecifier specifier = specifierBuilder.build();

            NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier);
            
            // Only remove internet capability if autoRouteTraffic is false
            // If autoRouteTraffic is true, we want Android to consider this network valid for routing
            if (autoRouteTraffic == null || !autoRouteTraffic) {
                requestBuilder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            
            NetworkRequest request = requestBuilder.build();

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    android.util.Log.d("CapacitorWifi", "Network available: " + network);
                    
                    // Don't resolve yet - wait for capabilities to be confirmed
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities);
                    android.util.Log.d("CapacitorWifi", "Network capabilities changed: " + network);
                    
                    // Bind process to network if autoRouteTraffic is enabled
                    // Do this when capabilities change to ensure network is fully ready
                    if (autoRouteTraffic != null && autoRouteTraffic) {
                        // Use a handler to bind after a short delay to ensure network is fully established
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        // Unbind from previous network if any
                                        if (boundNetwork != null) {
                                            connectivityManager.bindProcessToNetwork(null);
                                            android.util.Log.d("CapacitorWifi", "Unbound from previous network");
                                        }
                                        
                                        // Get the currently active network - this should be our WiFi network now
                                        Network activeNetwork = connectivityManager.getActiveNetwork();
                                        Network networkToBind = network;
                                        
                                        // Prefer the active network if it matches
                                        if (activeNetwork != null) {
                                            NetworkCapabilities activeCaps = connectivityManager.getNetworkCapabilities(activeNetwork);
                                            if (activeCaps != null && activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                                                networkToBind = activeNetwork;
                                                android.util.Log.d("CapacitorWifi", "Using active WiFi network for binding: " + networkToBind);
                                            }
                                        }
                                        
                                        // Bind to the network
                                        boolean bound = connectivityManager.bindProcessToNetwork(networkToBind);
                                        if (bound) {
                                            boundNetwork = networkToBind;
                                            android.util.Log.d("CapacitorWifi", "Successfully bound process to network: " + networkToBind);
                                            
                                            // Verify binding worked
                                            Network boundNet = connectivityManager.getBoundNetworkForProcess();
                                            if (boundNet != null && boundNet.equals(networkToBind)) {
                                                android.util.Log.d("CapacitorWifi", "Verified: Process is bound to network");
                                            } else {
                                                android.util.Log.w("CapacitorWifi", "Warning: Binding verification failed. Bound: " + boundNet + ", Expected: " + networkToBind);
                                            }
                                        } else {
                                            android.util.Log.e("CapacitorWifi", "Failed to bind process to network: connectivityManager returned false");
                                        }
                                    }
                                } catch (Exception e) {
                                    // Log error but don't fail the connection
                                    android.util.Log.e("CapacitorWifi", "Failed to bind process to network: " + e.getMessage(), e);
                                }
                            }
                        }, 500); // Wait 500ms for network to be fully established
                    } else {
                        // Ensure we're not bound if autoRouteTraffic is disabled
                        if (boundNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            connectivityManager.bindProcessToNetwork(null);
                            boundNetwork = null;
                        }
                    }
                    
                    // Resolve the call once capabilities are confirmed
                    call.resolve();
                }

                @Override
                public void onUnavailable() {
                    super.onUnavailable();
                    android.util.Log.e("CapacitorWifi", "Network unavailable");
                    call.reject("Failed to connect to network");
                }
            };

            connectivityManager.requestNetwork(request, networkCallback);
        } catch (Exception e) {
            call.reject("Failed to connect: " + e.getMessage(), e);
        }
    }

    private void connectLegacy(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "connectCallback");
            return;
        }

        String ssid = call.getString("ssid");
        if (ssid == null || ssid.isEmpty()) {
            call.reject("SSID is required");
            return;
        }

        String password = call.getString("password");
        Boolean isHiddenSsid = call.getBoolean("isHiddenSsid", false);
        Boolean autoRouteTraffic = call.getBoolean("autoRouteTraffic", false);

        try {
            WifiConfiguration wifiConfig = new WifiConfiguration();
            wifiConfig.SSID = "\"" + ssid + "\"";

            if (isHiddenSsid != null && isHiddenSsid) {
                wifiConfig.hiddenSSID = true;
            }

            if (password != null && !password.isEmpty()) {
                wifiConfig.preSharedKey = "\"" + password + "\"";
            } else {
                wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            int netId = wifiManager.addNetwork(wifiConfig);
            if (netId == -1) {
                call.reject("Failed to add network configuration");
                return;
            }

            boolean disconnectResult = wifiManager.disconnect();
            if (!disconnectResult) {
                call.reject("Failed to disconnect from current network");
                return;
            }

            boolean enableResult = wifiManager.enableNetwork(netId, true);
            if (!enableResult) {
                call.reject("Failed to enable network");
                return;
            }

            boolean reconnectResult = wifiManager.reconnect();
            if (!reconnectResult) {
                call.reject("Failed to reconnect");
                return;
            }

            // Bind process to network if autoRouteTraffic is enabled
            if (autoRouteTraffic != null && autoRouteTraffic) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        // Wait a bit for the connection to establish
                        Thread.sleep(1000); // Increased wait time
                        
                        // Unbind from previous network if any
                        if (boundNetwork != null) {
                            connectivityManager.bindProcessToNetwork(null);
                        }
                        
                        Network activeNetwork = connectivityManager.getActiveNetwork();
                        if (activeNetwork != null) {
                            boolean bound = connectivityManager.bindProcessToNetwork(activeNetwork);
                            if (bound) {
                                boundNetwork = activeNetwork;
                                android.util.Log.d("CapacitorWifi", "Successfully bound process to network (legacy): " + activeNetwork);
                            } else {
                                android.util.Log.e("CapacitorWifi", "Failed to bind process to network: connectivityManager returned false");
                            }
                        } else {
                            android.util.Log.w("CapacitorWifi", "No active network found to bind to");
                        }
                    }
                } catch (Exception e) {
                    // Log error but don't fail the connection
                    android.util.Log.e("CapacitorWifi", "Failed to bind process to network: " + e.getMessage());
                }
            } else {
                // Ensure we're not bound if autoRouteTraffic is disabled
                if (boundNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectivityManager.bindProcessToNetwork(null);
                    boundNetwork = null;
                }
            }

            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to connect: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        try {
            // Unbind from network if we were bound
            if (boundNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.bindProcessToNetwork(null);
                boundNetwork = null;
                android.util.Log.d("CapacitorWifi", "Unbound process from network");
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (networkCallback != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                    networkCallback = null;
                }
            } else {
                wifiManager.disconnect();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to disconnect: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getAvailableNetworks(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getAvailableNetworksCallback");
            return;
        }

        try {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            JSArray networks = new JSArray();

            for (ScanResult result : scanResults) {
                JSObject network = new JSObject();
                network.put("ssid", result.SSID);
                network.put("rssi", result.level);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    JSArray securityTypes = new JSArray();
                    int[] types = result.getSecurityTypes();
                    for (int type : types) {
                        securityTypes.put(type);
                    }
                    network.put("securityTypes", securityTypes);
                }

                networks.put(network);
            }

            JSObject ret = new JSObject();
            ret.put("networks", networks);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get available networks: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getIpAddress(PluginCall call) {
        try {
            String ipAddress = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        ipAddress = getWifiIpAddress();
                    }
                }
            } else {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    int ip = wifiInfo.getIpAddress();
                    ipAddress = String.format("%d.%d.%d.%d", (ip & 0xff), ((ip >> 8) & 0xff), ((ip >> 16) & 0xff), ((ip >> 24) & 0xff));
                }
            }

            if (ipAddress != null && !ipAddress.isEmpty()) {
                JSObject ret = new JSObject();
                ret.put("ipAddress", ipAddress);
                call.resolve(ret);
            } else {
                call.reject("No IP address found");
            }
        } catch (Exception e) {
            call.reject("Failed to get IP address: " + e.getMessage(), e);
        }
    }

    private String getWifiIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getName().contains("wlan")) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @PluginMethod
    public void getRssi(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getRssiCallback");
            return;
        }

        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                int rssi = wifiInfo.getRssi();
                JSObject ret = new JSObject();
                ret.put("rssi", rssi);
                call.resolve(ret);
            } else {
                call.reject("Failed to get WiFi info");
            }
        } catch (Exception e) {
            call.reject("Failed to get RSSI: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getSsid(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getSsidCallback");
            return;
        }

        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                String ssid = wifiInfo.getSSID();
                if (ssid != null) {
                    // Remove quotes if present
                    ssid = ssid.replace("\"", "");
                    JSObject ret = new JSObject();
                    ret.put("ssid", ssid);
                    call.resolve(ret);
                } else {
                    call.reject("No SSID found");
                }
            } else {
                call.reject("Failed to get WiFi info");
            }
        } catch (Exception e) {
            call.reject("Failed to get SSID: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void isEnabled(PluginCall call) {
        try {
            boolean enabled = wifiManager.isWifiEnabled();
            JSObject ret = new JSObject();
            ret.put("enabled", enabled);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to check WiFi status: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "startScanCallback");
            return;
        }

        try {
            // Register broadcast receiver for scan results
            if (scanResultsReceiver == null) {
                scanResultsReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                        if (success) {
                            notifyListeners("networksScanned", new JSObject());
                        }
                    }
                };

                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                getContext().registerReceiver(scanResultsReceiver, intentFilter);
            }

            boolean scanStarted = wifiManager.startScan();
            if (scanStarted) {
                call.resolve();
            } else {
                call.reject("Failed to start scan");
            }
        } catch (Exception e) {
            call.reject("Failed to start scan: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }

    @PermissionCallback
    private void addNetworkCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            addNetworkLegacy(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PermissionCallback
    private void connectCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            connectLegacy(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PermissionCallback
    private void getAvailableNetworksCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getAvailableNetworks(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PermissionCallback
    private void getRssiCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getRssi(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PermissionCallback
    private void getSsidCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getSsid(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @PermissionCallback
    private void startScanCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            startScan(call);
        } else {
            call.reject("Location permission is required");
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (scanResultsReceiver != null) {
            try {
                getContext().unregisterReceiver(scanResultsReceiver);
            } catch (Exception e) {
                // Receiver not registered
            }
            scanResultsReceiver = null;
        }

        if (networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                // Callback not registered
            }
            networkCallback = null;
        }
    }
}
