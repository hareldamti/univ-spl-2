package bguspl.set;

public class Env {

    public final Config config;
    public final UserInterface ui;
    public final Util util;
    public final boolean DEBUG;
    public Env(Config config, UserInterface ui, Util util) {
        this.config = config;
        this.ui = ui;
        this.util = util;
        this.DEBUG = true;
    }
}
