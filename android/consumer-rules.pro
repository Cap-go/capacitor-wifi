# Consumer ProGuard rules for @capgo/capacitor-wifi (applied to apps that depend on this AAR).

# Keep this plugin class and members: Capacitor Bridge resolves @PluginMethod entry points and
# registers @PermissionCallback / @ActivityCallback handlers by reflection (PluginHandle, Plugin).
# @capacitor/android also ships related keeps; this line scopes protection to this plugin only.
-keep class ee.forgr.plugin.capacitor_wifi.CapacitorWifiPlugin { *; }
