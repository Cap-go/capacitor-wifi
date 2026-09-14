package fixture;

public class FalsePositive {

    void demo(Object settings) {
        settings.hasOption("ignored");
        String msg = "call.hasOption(\"in-string\")";
        // call.hasOption("in-line-comment");
        /* call.getConfigValue("block"); */
    }
}
