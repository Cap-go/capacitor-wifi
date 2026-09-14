package fixture;

import com.getcapacitor.PluginCall;

public class HasOption {
    void demo(PluginCall call) {
        if (!call.hasOption("timeoutMs")) {
            return;
        }
    }
}
