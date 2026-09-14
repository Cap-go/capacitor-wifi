package fixture;

import com.getcapacitor.PluginCall;

public class HasOptionSplit {

    void demo(PluginCall call) {
        if (!call.hasOption("timeoutMs")) {
            return;
        }
    }
}
