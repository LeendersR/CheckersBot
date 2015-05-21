package checkers;

import org.draughts.DraughtsState;
import org10x10.dam.game.Move;

/*
    Stores the best move for a certain state. 
    Also has inner classes that are used to store information about certain states.
*/
public class GameNode {
    private final DraughtsState state;
    private Move bestMove;


    public GameNode(DraughtsState state) {
        this.state = state;
    }

    public DraughtsState getGameState() {
        return state;
    }

    public void setBestMove(Move move) {
        bestMove = move;
    }

    public Move getBestMove() {
        return bestMove;
    }

    /*
        Contains the bounds (alpha, beta), what kind of bound it is and the depth of the bound.
    */
    public static class Bounds {
        private int lowerBound, upperBound, exactValue, depth;
        public enum Type {EXACT, LOWERBOUND, UPPERBOUND}
        private Type type;

        public Bounds(int lowerBound, int upperBound, int exactValue, int depth, Type type) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.depth = depth;
            this.type = type;
            this.exactValue = exactValue;
        }
        
        public Bounds(int lowerBound, int upperBound, int depth) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.depth = depth;
        }

        public void setLowerBound(int lowerBound) {
            this.lowerBound = lowerBound;
        }

        public void setUpperBound(int upperBound) {
            this.upperBound = upperBound;
        }
        
        public void setType(Type type) {
            this.type = type;
        }
        
        public void setExactValue(int exactValue) {
            this.exactValue = exactValue;
        }

        public int getLowerBound() {
            return lowerBound;
        }

        public int getUpperBound() {
            return upperBound;
        }
        
        public int getDepth() {
            return depth;
        }
        
        public Type getType() {
            return type;
        }
        
        public int getExactValue() {
            return exactValue;
        } 

    }

    /*
        Contains the heuristic value and whether it was calculated as if we were white.
    */
    public static class HeuristicStorage {
        private int heuristic = Integer.MIN_VALUE;
        private boolean weAreWhite;
        
        public HeuristicStorage(int heuristic, boolean weAreWhite) {
            this.heuristic = heuristic;
            this.weAreWhite = weAreWhite;
        }
        
        public void setHeuristic(int heuristic) {
            this.heuristic = heuristic;
        }

        public void setWeAreWhite(boolean weAreWhite) {
            this.weAreWhite = weAreWhite;
        }
        
        public int getHeuristic() {
            return heuristic;
        }
        
        public boolean areWeWhite() {
            return weAreWhite;
        }
    }
}
