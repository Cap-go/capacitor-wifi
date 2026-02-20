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

    private final String pluginVersion = "8.1.8";

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private BroadcastReceiver scanResultsReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network boundNetwork; // Store the network we bound to for unbinding
    private final Object boundNetworkLock = new Object(); // Lock for thread-safe access to boundNetwork

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

                    // Bind process to network if autoRouteTraffic is enabled
                    if (autoRouteTraffic != null && autoRouteTraffic) {
                        try {
                            synchronized (boundNetworkLock) {
                                // Unbind from previous network if any
                                if (boundNetwork != null) {
                                    connectivityManager.bindProcessToNetwork(null);
                                }

                                // Bind to the new network
                                boolean bound = connectivityManager.bindProcessToNetwork(network);
                                if (bound) {
                                    boundNetwork = network;
                                }
                            }
                        } catch (Exception e) {
                            // Log error but don't fail the connection
                            android.util.Log.e("CapacitorWifi", "Failed to bind process to network: " + e.getMessage());
                        }
                    }

                    call.resolve();
                }

                @Override
                public void onUnavailable() {
                    super.onUnavailable();
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
                // Use handler to bind asynchronously after connection is established
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> {
                        try {
                            synchronized (boundNetworkLock) {
                                // Unbind from previous network if any
                                if (boundNetwork != null) {
                                    connectivityManager.bindProcessToNetwork(null);
                                }

                                Network activeNetwork = connectivityManager.getActiveNetwork();
                                if (activeNetwork != null) {
                                    boolean bound = connectivityManager.bindProcessToNetwork(activeNetwork);
                                    if (bound) {
                                        boundNetwork = activeNetwork;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Log error but don't fail the connection
                            android.util.Log.e("CapacitorWifi", "Failed to bind process to network: " + e.getMessage());
                        }
                    },
                    500
                );
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
            synchronized (boundNetworkLock) {
                if (boundNetwork != null) {
                    connectivityManager.bindProcessToNetwork(null);
                    boundNetwork = null;
                    android.util.Log.d("CapacitorWifi", "Unbound process from network");
                }
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

            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    ipAddress = getWifiIpAddress();
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
    public void getWifiInfo(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "getWifiInfoCallback");
            return;
        }

        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                call.reject("Failed to get WiFi info");
                return;
            }

            JSObject ret = new JSObject();

            // Get SSID
            String ssid = wifiInfo.getSSID();
            if (ssid != null) {
                ssid = ssid.replace("\"", "");
                ret.put("ssid", ssid);
            } else {
                call.reject("No SSID found");
                return;
            }

            // Get BSSID (MAC address of access point)
            String bssid = wifiInfo.getBSSID();
            if (bssid != null) {
                ret.put("bssid", bssid);
            }

            // Get IP Address
            String ipAddress = getWifiIpAddress();
            if (ipAddress != null && !ipAddress.isEmpty()) {
                ret.put("ip", ipAddress);
            } else {
                call.reject("No IP address found");
                return;
            }

            // Get Frequency (API 21+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int frequency = wifiInfo.getFrequency();
                ret.put("frequency", frequency);
            }

            // Get Link Speed
            int linkSpeed = wifiInfo.getLinkSpeed();
            ret.put("linkSpeed", linkSpeed);

            // Get Signal Strength (0-100)
            int rssi = wifiInfo.getRssi();
            int signalStrength = calculateSignalStrength(rssi);
            ret.put("signalStrength", signalStrength);

            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to get WiFi info: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate signal strength percentage (0-100) from RSSI
     * RSSI typically ranges from -100 (weak) to -50 (strong)
     */
    private int calculateSignalStrength(int rssi) {
        if (rssi <= -100) {
            return 0;
        } else if (rssi >= -50) {
            return 100;
        } else {
            return 2 * (rssi + 100);
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
    private void getWifiInfoCallback(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            getWifiInfo(call);
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
