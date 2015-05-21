package checkers;

import org.draughts.DraughtsPlugin;

public class CheckersBot extends DraughtsPlugin {
    public CheckersBot() {
        super(new ManhattanProject());
    }
}
