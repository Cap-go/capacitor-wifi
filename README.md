# @capgo/capacitor-wifi
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_wifi"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_wifi"> Missing a feature? We'll build the plugin for you 💪</a></h2>
</div>

Manage WiFi connectivity for your Capacitor app

## Why Capacitor WiFi?

A free and powerful WiFi management plugin with modern platform support:

- **Network management** - Connect, disconnect, and add WiFi networks programmatically
- **Network scanning** - Discover available WiFi networks (Android only)
- **Network info** - Get SSID, IP address, and signal strength (RSSI)
- **Modern APIs** - Uses NetworkExtension (iOS) and handles Android 10+ restrictions
- **Cross-platform** - Consistent API across iOS and Android

Perfect for IoT apps, network diagnostic tools, and smart home applications.

## Documentation

The most complete doc is available here: https://capgo.app/docs/plugins/wifi/

## Install

```bash
npm install @capgo/capacitor-wifi
npx cap sync
```

## Requirements

- **iOS**: Requires location permission (`NSLocationWhenInUseUsageDescription` in Info.plist) to access WiFi information. Uses NetworkExtension framework.
- **Android**: Requires location permissions. Network scanning and RSSI available on Android only. Android 10+ uses system dialogs for adding networks.

## API

<docgen-index>

* [`addNetwork(...)`](#addnetwork)
* [`connect(...)`](#connect)
* [`disconnect(...)`](#disconnect)
* [`getAvailableNetworks()`](#getavailablenetworks)
* [`getIpAddress()`](#getipaddress)
* [`getRssi()`](#getrssi)
* [`getSsid()`](#getssid)
* [`isEnabled()`](#isenabled)
* [`startScan()`](#startscan)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions(...)`](#requestpermissions)
* [`addListener('networksScanned', ...)`](#addlistenernetworksscanned-)
* [`removeAllListeners()`](#removealllisteners)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

WiFi plugin for managing device WiFi connectivity

### addNetwork(...)

```typescript
addNetwork(options: AddNetworkOptions) => Promise<void>
```

Show a system dialog to add a Wi-Fi network to the device.
On Android SDK 30+, this opens the system Wi-Fi settings with the network pre-filled.
On iOS, this connects to the network directly.

| Param         | Type                                                            | Description                                            |
| ------------- | --------------------------------------------------------------- | ------------------------------------------------------ |
| **`options`** | <code><a href="#addnetworkoptions">AddNetworkOptions</a></code> | - <a href="#network">Network</a> configuration options |

**Since:** 7.0.0

--------------------


### connect(...)

```typescript
connect(options: ConnectOptions) => Promise<void>
```

Connect to a Wi-Fi network.
On Android, this creates a temporary connection that doesn't route traffic through the network by default.
Set autoRouteTraffic to true to bind app traffic to the connected network (useful for local/device-hosted APs).
For a persistent connection on Android, use addNetwork() instead.
On iOS, this creates a persistent connection.

| Param         | Type                                                      | Description          |
| ------------- | --------------------------------------------------------- | -------------------- |
| **`options`** | <code><a href="#connectoptions">ConnectOptions</a></code> | - Connection options |

**Since:** 7.0.0

--------------------


### disconnect(...)

```typescript
disconnect(options?: DisconnectOptions | undefined) => Promise<void>
```

Disconnect from the current Wi-Fi network.
On iOS, only disconnects from networks that were added via this plugin.

| Param         | Type                                                            | Description                   |
| ------------- | --------------------------------------------------------------- | ----------------------------- |
| **`options`** | <code><a href="#disconnectoptions">DisconnectOptions</a></code> | - Optional disconnect options |

**Since:** 7.0.0

--------------------


### getAvailableNetworks()

```typescript
getAvailableNetworks() => Promise<GetAvailableNetworksResult>
```

Get a list of available Wi-Fi networks from the last scan.
Only available on Android.

**Returns:** <code>Promise&lt;<a href="#getavailablenetworksresult">GetAvailableNetworksResult</a>&gt;</code>

**Since:** 7.0.0

--------------------


### getIpAddress()

```typescript
getIpAddress() => Promise<GetIpAddressResult>
```

Get the device's current IP address.
Available on both Android and iOS.

**Returns:** <code>Promise&lt;<a href="#getipaddressresult">GetIpAddressResult</a>&gt;</code>

**Since:** 7.0.0

--------------------


### getRssi()

```typescript
getRssi() => Promise<GetRssiResult>
```

Get the received signal strength indicator (RSSI) of the current network in dBm.
Only available on Android.

**Returns:** <code>Promise&lt;<a href="#getrssiresult">GetRssiResult</a>&gt;</code>

**Since:** 7.0.0

--------------------


### getSsid()

```typescript
getSsid() => Promise<GetSsidResult>
```

Get the service set identifier (SSID) of the current network.
Available on both Android and iOS.

**Returns:** <code>Promise&lt;<a href="#getssidresult">GetSsidResult</a>&gt;</code>

**Since:** 7.0.0

--------------------


### isEnabled()

```typescript
isEnabled() => Promise<IsEnabledResult>
```

Check if Wi-Fi is enabled on the device.
Only available on Android.

**Returns:** <code>Promise&lt;<a href="#isenabledresult">IsEnabledResult</a>&gt;</code>

**Since:** 7.0.0

--------------------


### startScan()

```typescript
startScan() => Promise<void>
```

Start scanning for Wi-Fi networks.
Only available on Android.
Results are delivered via the 'networksScanned' event listener.
Note: May fail due to system throttling or hardware issues.

**Since:** 7.0.0

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus>
```

Check the current permission status for location access.
Location permission is required for Wi-Fi operations on both platforms.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

**Since:** 7.0.0

--------------------


### requestPermissions(...)

```typescript
requestPermissions(options?: RequestPermissionsOptions | undefined) => Promise<PermissionStatus>
```

Request location permissions from the user.
Location permission is required for Wi-Fi operations on both platforms.

| Param         | Type                                                                            | Description                           |
| ------------- | ------------------------------------------------------------------------------- | ------------------------------------- |
| **`options`** | <code><a href="#requestpermissionsoptions">RequestPermissionsOptions</a></code> | - Optional permission request options |

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

**Since:** 7.0.0

--------------------


### addListener('networksScanned', ...)

```typescript
addListener(eventName: 'networksScanned', listenerFunc: () => void) => Promise<PluginListenerHandle>
```

Add a listener for the 'networksScanned' event.
Only available on Android.
This event is fired when Wi-Fi scan results are available.

| Param              | Type                           | Description                          |
| ------------------ | ------------------------------ | ------------------------------------ |
| **`eventName`**    | <code>'networksScanned'</code> | - The event name ('networksScanned') |
| **`listenerFunc`** | <code>() =&gt; void</code>     | - The callback function to execute   |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 7.0.0

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners for this plugin.

**Since:** 7.0.0

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<{ version: string; }>
```

Get the native plugin version.

**Returns:** <code>Promise&lt;{ version: string; }&gt;</code>

**Since:** 7.0.0

--------------------


### Interfaces


#### AddNetworkOptions

Options for adding a network

| Prop               | Type                                                                | Description                                               | Default                                   | Since |
| ------------------ | ------------------------------------------------------------------- | --------------------------------------------------------- | ----------------------------------------- | ----- |
| **`ssid`**         | <code>string</code>                                                 | The SSID of the network to add                            |                                           | 7.0.0 |
| **`password`**     | <code>string</code>                                                 | The password for the network (optional for open networks) |                                           | 7.0.0 |
| **`isHiddenSsid`** | <code>boolean</code>                                                | Whether the network is hidden (Android only)              | <code>false</code>                        | 7.0.0 |
| **`securityType`** | <code><a href="#networksecuritytype">NetworkSecurityType</a></code> | The security type of the network (Android only)           | <code>NetworkSecurityType.WPA2_PSK</code> | 7.0.0 |


#### ConnectOptions

Options for connecting to a network

| Prop                   | Type                 | Description                                                                                                                                                                                                                                                                                                                         | Default            | Since |
| ---------------------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ | ----- |
| **`ssid`**             | <code>string</code>  | The SSID of the network to connect to                                                                                                                                                                                                                                                                                               |                    | 7.0.0 |
| **`password`**         | <code>string</code>  | The password for the network (optional for open networks)                                                                                                                                                                                                                                                                           |                    | 7.0.0 |
| **`isHiddenSsid`**     | <code>boolean</code> | Whether the network is hidden (Android only)                                                                                                                                                                                                                                                                                        | <code>false</code> | 7.0.0 |
| **`autoRouteTraffic`** | <code>boolean</code> | Whether to automatically route app traffic through the connected Wi-Fi network (Android only) When enabled, it binds the app process to the connected network using ConnectivityManager.bindProcessToNetwork() This is useful for connecting to local/device-hosted APs (e.g., ESP32, IoT devices) that don't have internet access. | <code>false</code> | 7.0.0 |


#### DisconnectOptions

Options for disconnecting from a network

| Prop       | Type                | Description                                           | Since |
| ---------- | ------------------- | ----------------------------------------------------- | ----- |
| **`ssid`** | <code>string</code> | The SSID of the network to disconnect from (optional) | 7.0.0 |


#### GetAvailableNetworksResult

Result from getAvailableNetworks()

| Prop           | Type                   | Description                | Since |
| -------------- | ---------------------- | -------------------------- | ----- |
| **`networks`** | <code>Network[]</code> | List of available networks | 7.0.0 |


#### Network

Represents a Wi-Fi network

| Prop                | Type                               | Description                                                         | Since |
| ------------------- | ---------------------------------- | ------------------------------------------------------------------- | ----- |
| **`ssid`**          | <code>string</code>                | The SSID of the network                                             | 7.0.0 |
| **`rssi`**          | <code>number</code>                | The signal strength in dBm                                          | 7.0.0 |
| **`securityTypes`** | <code>NetworkSecurityType[]</code> | The security types supported by this network (Android SDK 33+ only) | 7.0.0 |


#### GetIpAddressResult

Result from getIpAddress()

| Prop            | Type                | Description             | Since |
| --------------- | ------------------- | ----------------------- | ----- |
| **`ipAddress`** | <code>string</code> | The device's IP address | 7.0.0 |


#### GetRssiResult

Result from getRssi()

| Prop       | Type                | Description                | Since |
| ---------- | ------------------- | -------------------------- | ----- |
| **`rssi`** | <code>number</code> | The signal strength in dBm | 7.0.0 |


#### GetSsidResult

Result from getSsid()

| Prop       | Type                | Description                     | Since |
| ---------- | ------------------- | ------------------------------- | ----- |
| **`ssid`** | <code>string</code> | The SSID of the current network | 7.0.0 |


#### IsEnabledResult

Result from isEnabled()

| Prop          | Type                 | Description              | Since |
| ------------- | -------------------- | ------------------------ | ----- |
| **`enabled`** | <code>boolean</code> | Whether Wi-Fi is enabled | 7.0.0 |


#### PermissionStatus

Permission status

| Prop           | Type                                                        | Description               | Since |
| -------------- | ----------------------------------------------------------- | ------------------------- | ----- |
| **`location`** | <code><a href="#permissionstate">PermissionState</a></code> | Location permission state | 7.0.0 |


#### RequestPermissionsOptions

Options for requesting permissions

| Prop              | Type                      | Description            | Since |
| ----------------- | ------------------------- | ---------------------- | ----- |
| **`permissions`** | <code>'location'[]</code> | Permissions to request | 7.0.0 |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


### Enums


#### NetworkSecurityType

| Members                       | Value           | Description                    | Since |
| ----------------------------- | --------------- | ------------------------------ | ----- |
| **`OPEN`**                    | <code>0</code>  | Open network with no security  | 7.0.0 |
| **`WEP`**                     | <code>1</code>  | WEP security                   | 7.0.0 |
| **`WPA2_PSK`**                | <code>2</code>  | WPA/WPA2 Personal (PSK)        | 7.0.0 |
| **`EAP`**                     | <code>3</code>  | WPA/WPA2/WPA3 Enterprise (EAP) | 7.0.0 |
| **`SAE`**                     | <code>4</code>  | WPA3 Personal (SAE)            | 7.0.0 |
| **`WPA3_ENTERPRISE`**         | <code>5</code>  | WPA3 Enterprise                | 7.0.0 |
| **`WPA3_ENTERPRISE_192_BIT`** | <code>6</code>  | WPA3 Enterprise 192-bit mode   | 7.0.0 |
| **`PASSPOINT`**               | <code>7</code>  | Passpoint network              | 7.0.0 |
| **`OWE`**                     | <code>8</code>  | Enhanced Open (OWE)            | 7.0.0 |
| **`WAPI_PSK`**                | <code>9</code>  | WAPI PSK                       | 7.0.0 |
| **`WAPI_CERT`**               | <code>10</code> | WAPI Certificate               | 7.0.0 |

</docgen-api>
