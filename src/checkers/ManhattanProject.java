package checkers;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.draughts.DraughtsState;
import org.draughts.player.DraughtsPlayer;
import org10x10.dam.game.Move;

public class ManhattanProject extends DraughtsPlayer {
    private final static Logger LOG = Logger.getLogger(ManhattanProject.class.getName());
    private boolean stopped = false;
    private int lastHeuristic;
    protected boolean weAreWhite;
    private final int QUIESCENCE_SEARCH_DEPTH = 100;
    // Caches alpha and beta values for some state at some depth
    private HashMap<DraughtsState, GameNode.Bounds> transitionTable;
    // Caches the heuristic value of a state
    private HashMap<DraughtsState, GameNode.HeuristicStorage> heuristicsTable;

    /*
    Constructor sets our players image and initializes some constants to be used for calculating heuristics.
    */
    public ManhattanProject() {
        super(ManhattanProject.class.getResource("resources/manhattanproject.png"));
        initBitpatterns();
    }

    /*
    Signal that our computation to stop
    */
    @Override
    public void stop() {
        stopped = true;
    }

    /*
    Returns an estimate of how good the current situation is (our latest calculated alpha value)
    */
    @Override
    public Integer getValue() {
        return lastHeuristic;
    }

    /*
    Returns the move for a given state
    */
    @Override
    public Move getMove(DraughtsState state) {
        // Reset the cache tables so we do not store any information between states
        transitionTable = new HashMap<DraughtsState, GameNode.Bounds>();
        heuristicsTable = new HashMap<DraughtsState, GameNode.HeuristicStorage>();
        lastHeuristic = 0; // We do not use the heuristic to calculate but reset it anyway
        weAreWhite = state.isWhiteToMove(); // Are we white or black?

        Move bestMove = null;
        // We only get our best move for a max layer, not a min layer, so we increase the depth by 2
        for (int depth = 1; true; depth += 2) {
            try {
                bestMove = alphabeta(state, depth, bestMove);
            } catch (AIStoppedException ex) {
                LOG.log(Level.INFO, "ManhattanProject ran out of time at depth {0}", depth);
                break;
            }
        }
        // If we didn't have time to select a best move, set it to the first move,
        // otherwise we would lose the game for not returning a valid move.
        if (bestMove == null) {
            bestMove = state.getMoves().get(0);
        }
        return bestMove;
    }

    /* Here we have repeated maximized alphabeta for the following reasons:
        - We only sort the root node (sorting at every recursive call is too slow)
        - To return the best move we could use the class GameNode with setBestMove()
          but we only need to best move at the root, so again this is faster.

       We return the best move and set lastHeuristic to alpha to show how good our player feels about the current state.
    */
    protected Move alphabeta(final DraughtsState state, int depth, final Move prevBestMove) throws AIStoppedException {
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        // We want to search our previous best move as first since it will result in better alphas and betas
        // By sorting we first explore the previous best move and afterwards the states with the best heuristics
        // By doing this we prune more earlier and as a result our algorithm converges faster
        List<Move> moves = state.getMoves();
        Collections.sort(moves, new Comparator<Move>() {
            @Override
            public int compare(Move m1, Move m2) {
                if (prevBestMove != null) {
                    if (m1.equals(prevBestMove)) {
                        return 1;
                    }
                    if (m2.equals(prevBestMove)) {
                        return -1;
                    }
                }

                state.doMove(m1);
                Integer h1 = heuristic(state);
                state.undoMove(m1);

                state.doMove(m2);
                int h2 = heuristic(state);
                state.undoMove(m2);

                return h1.compareTo(h2);
            }
        });

        // Alphabeta search for the max layer
        Move bestMove = null;
        for (Move move : moves) {
            state.doMove(move);
            int newAlpha = alphabeta(state, alpha, beta, depth-1, false);
            state.undoMove(move);
            if (newAlpha > alpha) {
                alpha = newAlpha;
                bestMove = move;
            }
        }
        lastHeuristic = alpha;
        return bestMove;
    }

    /*
    Quiescence search is an alphabeta search but only searches moves where a piece is captured.
    his is to remedy the situation where a state might be considered bad because you will lose
    a piece but if you had searched a bit father you would have seen that you actually gained a piece.
    (Note that quiescence search has no StopExcetion since it is super fast.)
    */
    int quiescenceSearch(DraughtsState state, int alpha, int beta, int depth, boolean maximize) {
        if (depth == 0 || state.isEndState()) {
            return heuristic(state);
        }
        int bestGuess;
        List<Move> moves = state.getMoves();
        // We sort by capture count to speed things up (prune more)
        Collections.sort(moves, new Comparator<Move>() {
            @Override
            public int compare(Move m1, Move m2) {
                return new Integer(m1.getCaptureCount()).compareTo(m2.getCaptureCount());
            }
        });
        if (maximize) {
            bestGuess = Integer.MIN_VALUE;
            int a = alpha; // To save the original alpha value
            for (Move move : moves) {
                if (bestGuess >= beta) {
                    break;
                }
                // If there are no pieces to be captured we can break off the search since it's sorted by capture count
                if (move.getCaptureCount() == 0) {
                    break;
                }
                state.doMove(move);
                bestGuess = Math.max(bestGuess, quiescenceSearch(state, a, beta, depth - 1, false));
                state.undoMove(move);
                a = Math.max(a, bestGuess);
            }
        } else {
            bestGuess = Integer.MAX_VALUE;
            int b = beta; // To save the original beta value
            for (Move move : moves) {
                if (bestGuess <= alpha) {
                    break;
                }
                // If there are no pieces to be captured we can break off the search since it's sorted by capture count
                if (move.getCaptureCount() == 0) {
                    break;
                }
                state.doMove(move);
                bestGuess = Math.min(bestGuess, quiescenceSearch(state, alpha, b, depth - 1, true));
                state.undoMove(move);
                b = Math.min(b, bestGuess);
            }
        }
        // If no captures return heuristic, state is quiet
        if (bestGuess == Integer.MAX_VALUE || bestGuess == Integer.MIN_VALUE) {
            bestGuess = heuristic(state);
        }
        return bestGuess;
    }

    /*
    Alphabeta search
    */
    protected int alphabeta(final DraughtsState state, int alpha, int beta, int depth, boolean maximize) throws AIStoppedException {
        // If we are signalled to stop, stop immediately.
        if (stopped) {
            stopped = false;
            throw new AIStoppedException();
        }

        // If we already visited this state at this depth or some greater depth return those alphas and betas
        GameNode.Bounds bounds = transitionTable.get(state);
        if (bounds != null && bounds.getDepth() >= depth) {
            int lowerBound = bounds.getLowerBound();
            int upperBound = bounds.getUpperBound();
            if (lowerBound >= beta) {
                return lowerBound;
            }
            if (upperBound <= alpha) {
                return upperBound;
            }
            alpha = Math.max(alpha, lowerBound);
            beta = Math.min(beta, upperBound);
        }

        // If we are at the end state return the heuristic
        if (state.isEndState()) {
            return heuristic(state);
        } else if (depth == 0) {
            // If we are just at our search depth, return the value of the quiescence search.
            return quiescenceSearch(state, alpha, beta, QUIESCENCE_SEARCH_DEPTH, maximize);
        }

        int bestGuess;
        List<Move> moves = state.getMoves();
        if (maximize) {
            bestGuess = Integer.MIN_VALUE;
            int a = alpha; // To save the original alpha value
            for (Move move : moves) {
                if (bestGuess >= beta) {
                    break;
                }
                state.doMove(move);
                bestGuess = Math.max(bestGuess, alphabeta(state, a, beta, depth - 1, false));
                state.undoMove(move);
                a = Math.max(a, bestGuess);
            }
        } else {
            bestGuess = Integer.MAX_VALUE;
            int b = beta; // To save the original beta value
            for (Move move : moves) {
                if (bestGuess <= alpha) {
                    break;
                }
                state.doMove(move);
                bestGuess = Math.min(bestGuess, alphabeta(state, alpha, b, depth - 1, true));
                state.undoMove(move);
                b = Math.min(b, bestGuess);
            }
        }

        // If our solution is good save it for the future
        if (bestGuess <= alpha) {
            if (bounds == null) {
                transitionTable.put(state, new GameNode.Bounds(Integer.MIN_VALUE, bestGuess, depth));
            } else {
                bounds.setUpperBound(bestGuess);
            }
        } else if (bestGuess > alpha && bestGuess < beta) {
            if (bounds == null) {
                transitionTable.put(state, new GameNode.Bounds(bestGuess, bestGuess, depth));
            } else {
                bounds.setLowerBound(bestGuess);
                bounds.setUpperBound(bestGuess);
            }
        } else if (bestGuess >= beta) {
            if (bounds == null) {
                transitionTable.put(state, new GameNode.Bounds(bestGuess, Integer.MAX_VALUE, depth));
            } else {
                bounds.setLowerBound(bestGuess);
            }
        }
        return bestGuess;
    }

    /*
    The top left square is 1, bottom right is 50.
    Row 1 is top row
    Column 1 is most left column
    First left diagonal is bottom left to top right
    First right diagonal is from top left to bottom right

    What follows below seems scary but it is the success (among other things) for our player.
    We can store the board state in a long and by using bit manipulation calculate various new positions, this would be
    too slow if we calculated all board positions by actually executing the moves.

    So a long l contains a 1 at bit x (1<= x <= 50) if x is contained l. l can be for example row 1 and so bits
    1, 2, 3, 4 and 5 would be 1 while the rest would be 0.
    */
    void initBitpatterns() {
        for (int i = 1; i <= 10; i += 1) {
            COLUMN[i] = 0;
            ROW[i] = 0;
            for (int j = 0; j < 5; j += 1) {
                COLUMN[i] = COLUMN[i] | (1L << 5*(i%2) + 10*j);
                ROW[i] = ROW[i] | (1L << (5*(i-1) + (j+1)));
            }
        }

        LEFT_DIAGONAL[1] = (1L << 46);
        LEFT_DIAGONAL[2] = (1L << 36) | (1L << 41) | (1L << 47);
        LEFT_DIAGONAL[3] = (1L << 26) | (1L << 31) | (1L << 37) | (1L << 42) | (1L << 48);
        LEFT_DIAGONAL[4] = (1L << 16) | (1L << 21) | (1L << 27) | (1L << 32) | (1L << 38) | (1L << 43) | (1L << 49);
        LEFT_DIAGONAL[5] = (1L << 6) | (1L << 11) | (1L << 17) | (1L << 22) | (1L << 28) | (1L << 33) | (1L << 39) | (1L << 44) | (1L << 50);
        LEFT_DIAGONAL[6] = (1L << 1) | (1L << 7) | (1L << 12) | (1L << 18) | (1L << 23) | (1L << 29) | (1L << 34) | (1L << 40) | (1L << 45);
        LEFT_DIAGONAL[7] = (1L << 2) | (1L << 8) | (1L << 13) | (1L << 19) | (1L << 24) | (1L << 30) | (1L << 35);
        LEFT_DIAGONAL[8] = (1L << 3) | (1L << 9) | (1L << 14) | (1L << 20) | (1L << 25);
        LEFT_DIAGONAL[9] = (1L << 4) | (1L << 10) | (1L << 15);
        LEFT_DIAGONAL[10] = (1L << 5);

        RIGHT_DIAGONAL[1] = (1L << 6) | (1L << 1);
        RIGHT_DIAGONAL[2] = (1L << 16) | (1L << 11) | (1L << 7) | (1L << 2);
        RIGHT_DIAGONAL[3] = (1L << 26) | (1L << 21) | (1L << 17) | (1L << 12) | (1L << 8) | (1L << 3);
        RIGHT_DIAGONAL[4] = (1L << 36) | (1L << 31) | (1L << 27) | (1L << 22) | (1L << 18) | (1L << 13) | (1L << 9) | (1L << 4);
        RIGHT_DIAGONAL[5] = (1L << 46) | (1L << 41) | (1L << 37) | (1L << 32) | (1L << 28) | (1L << 23) | (1L << 19) | (1L << 14) | (1L << 10) | (1L << 5);
        RIGHT_DIAGONAL[6] = (1L << 47) | (1L << 42) | (1L << 38) | (1L << 33) | (1L << 29) | (1L << 24) | (1L << 20) | (1L << 15);
        RIGHT_DIAGONAL[7] = (1L << 48) | (1L << 43) | (1L << 39) | (1L << 34) | (1L << 30) | (1L << 25);
        RIGHT_DIAGONAL[8] = (1L << 49) | (1L << 44) | (1L << 40) | (1L << 35);
        RIGHT_DIAGONAL[9] = (1L << 50) | (1L << 45);

        for (int i = 4; i <= 7; i += 1) {
            middle = middle | ROW[i] | COLUMN[i];
        }
    }
    long[] COLUMN = new long[11];
    long[] ROW = new long[11];
    long[] LEFT_DIAGONAL = new long[11];
    long[] RIGHT_DIAGONAL = new long[11];


    // The 'middle' square of the board
    long middle = 0;

    // Strong/weak bridge
    final long WHITE_SB =  (1L << 46) | (1L << 48) | (1L << 50);
    final long WHITE_WB = (1L << 47) | (1L << 49);

    final long BLACK_SB =  (1L << 1) | (1L << 3) | (1L << 5);
    final long BLACK_WB = (1L << 2) | (1L << 4);

    // A strong triangle position
    final long WHITE_TRIANGLE = (1L << 47) | (1L << 48) | (1L << 49) | (1L << 50) | (1L << 42) | (1L << 43) | (1L << 44) | (1L << 38) | (1L << 39) | (1L << 33);
    final long BLACK_TRIANGLE = (1L << 1) | (1L << 2) | (1L << 3) | (1L << 4) | (1L << 7) | (1L << 8) | (1L << 9) | (1L << 12) | (1L << 13) | (1L << 18);

    // Left/right side of the board
    final long LEFT_SIDE = COLUMN[1] | COLUMN[2] | COLUMN[3] | COLUMN[4] | COLUMN[5];
    final long RIGHT_SIDE = COLUMN[6] | COLUMN[7] | COLUMN[8] | COLUMN[9] | COLUMN[10];

    // Strong/weak bridge exploits
    final long WHITE_SBE = WHITE_TRIANGLE;
    final long WHITE_WBE = (1L << 46) | (1L << 47) | (1L << 48) | (1L << 49) | (1L << 41) | (1L << 42) | (1L << 43) | (1L << 37) | (1L << 38) | (1L << 32);
    final long BLACK_SBE = BLACK_TRIANGLE;
    final long BLACK_WBE = (1L << 2) | (1L << 3) | (1L << 4) | (1L << 5) | (1L << 8) | (1L << 9) | (1L << 10) | (1L << 13) | (1L << 14) | (1L << 19);

    // Some functions to simplify the bit manipulations
    boolean in(long square, long mask) {
        return (square & mask) != 0;
    }

    boolean not_in(long square, long mask) {
            return (square & mask) == 0;
    }

    boolean form(long squares, long mask) {
            return (squares & mask) == mask;
    }

    // The penalties or bonuses per heuristic
    final int OPENING = 25;
    final int MIDDLE_GAME = 15;
    final int[] PIECE_WORTH = {100, 100, 100};
    final int[] KING_WORTH = {250, 250, 300};
    final int[] BONUS_BACK = {5, 5, 3};
    final int[] BONUS_TRIANGLE = {10, 0, 0};
    final int[] BONUS_BALANCE = {4, 6, 3};
    final int[] PEN_BALANCE = {-4, -6, -3};
    final int[] BONUS_RIVER = {0, 25, 50};
    final int[] PEN_BRIDGE = {0, -10, -15};
    final int[] BONUS_MIDDLE = {2, 1, 1};
    final int[] BONUS_RUNAWAY = {50, 50, 60};

    /*
    Some ideas of the heuristics are taken from Chinook (a computer player that solved 8x8 checkers).
    The biggest heuristc that we could add would be how mobile we are. We didn't implement this for two reasons,
    1) our bot already performed really well, 2) it would be a big task since we would need to calculate all valid moves
    */
    protected int heuristic(DraughtsState state) {
        // If we already calculated the heuristic for this state get the value from the cache.
        GameNode.HeuristicStorage storage = heuristicsTable.get(state);
        if (storage != null && storage.getHeuristic() != Integer.MIN_VALUE) {
            int score = storage.getHeuristic();
            if (storage.areWeWhite() != weAreWhite) {
                score = -score;
            }
            return score;
        }

        /*
        As a reminder, for example long whitePieces contains a 1 at bit x (1<= x <= 50) if at position x there is a
        white piece.

        Note that a piece is not a king, and a king is not a piece, the long white contains both pieces and kings.
        Below we set the states for white/black pieces and kings from which we calculate the other states.
        */
        long whitePieces = 0, blackPieces = 0, whiteKings = 0, blackKings = 0;
        int[] pieces = state.getPieces();
        for (int i = 1; i <= 50; i += 1) {
            switch (pieces[i]) {
                case DraughtsState.WHITEPIECE:
                    whitePieces = whitePieces | (1L << i);
                    break;
                case DraughtsState.WHITEKING:
                    whiteKings = whiteKings | (1L << i);
                    break;
                case DraughtsState.BLACKPIECE:
                    blackPieces = blackPieces | (1L << i);
                    break;
                case DraughtsState.BLACKKING:
                    blackKings = blackKings | (1L << i);
                    break;
            }
        }

        long white = whitePieces | whiteKings;
        long black = blackPieces | blackKings;
        long occupied = whitePieces | blackPieces | whiteKings | blackKings;
        long empty = ~occupied;

        int numWhitePieces = Long.bitCount(whitePieces);
        int numWhiteKings = Long.bitCount(whiteKings);
        int numBlackPieces = Long.bitCount(blackPieces);
        int numBlackKings = Long.bitCount(blackKings);
        int numPieces = Long.bitCount(occupied);

        int gamePhase = (numPieces >= MIDDLE_GAME ? (numPieces >= OPENING ? 0 : 1) : 2);

        // Material score
        int score = (int)((numWhitePieces - numBlackPieces) * PIECE_WORTH[gamePhase] + (numWhiteKings - numBlackKings) * KING_WORTH[gamePhase]);
        // Add scores for other heuristics
        score += backRank(white, black, whiteKings, blackKings, gamePhase);
        score += triangle(whitePieces, blackPieces, whiteKings, blackKings, gamePhase);
        score += balance(white, black, gamePhase);
        score += diagonalControl(whiteKings, blackKings, occupied, gamePhase);
        score += bridgeExploit(white, black, whiteKings, blackKings, gamePhase);
        score += runAway(whitePieces, blackPieces, empty, gamePhase);
        // We didn't feel the control middle heuristic really improved our bot
        //score += controlMiddle(white, black, gamePhase);

        // When we reached an end state we multiply the score by a factor 1000 so that we quicker end the game
        if (state.isEndState()) {
            score *= 1000;
        }

        // If we are black flip the score
        if (!weAreWhite) {
            score = -score;
        }

        // Store the score for the future
        if (storage == null) {
            heuristicsTable.put(state, new GameNode.HeuristicStorage(score, weAreWhite));
        } else if (storage.getHeuristic() == Integer.MIN_VALUE) {
            storage.setHeuristic(score);
            storage.setWeAreWhite(weAreWhite);
        }
        return score;
    }

    /*
    Return a bonus if we have a strong position on our last row.
    A strong position is that we have some pieces at our last row so that we can hit (almost) every square in the before
    last row. This makes sure the opponent cannot get any easy kings.
    */
    int backRank(long white, long black, long whiteKings, long blackKings, int gamePhase) {
        int whiteBonus = Long.bitCount(white & ROW[10]);
        int blackBonus = Long.bitCount(black & ROW[1]);

        // White
        if (whiteBonus == 3) {
            whiteBonus = (form(white, WHITE_SB) ? 3 : (form(white, WHITE_WB) ? 2 : 1));
        } else if (whiteBonus == 4 && not_in(white, (1L << 50))) {
            --whiteBonus;
        }

        if (blackKings != 0) {
            whiteBonus -= 2;
            if (whiteBonus < 0) {
                whiteBonus = 0;
            }
        }

        // Black
        if (blackBonus == 3) {
            blackBonus = (form(black, BLACK_SB) ? 3 : (form(black, BLACK_WB) ? 2 : 1));
        } else if (blackBonus == 4 && not_in(black, (1L << 1))) {
            --blackBonus;
        }

        if (whiteKings != 0) {
           blackBonus -= 2;
           if (blackBonus < 0) {
               blackBonus = 0;
           }
        }

        return (whiteBonus - blackBonus) * BONUS_BACK[gamePhase];
    }

    // Return a bonus if we are in triangle formation
    int triangle(long whitePieces, long blackPieces, long whiteKings, long blackKings, int gamePhase) {
        int whiteBonus = (int)(form(whitePieces, WHITE_TRIANGLE) && blackKings == 0 ? BONUS_TRIANGLE[gamePhase] : 0);
        int blackBonus = (int)(form(blackPieces, BLACK_TRIANGLE) && whiteKings == 0 ? BONUS_TRIANGLE[gamePhase] : 0);

        return whiteBonus - blackBonus;
    }

    /*
    Do we have the same amount of pieces on the left side as we have on the right side? Return a bonus if we do.
    Why? If we have all our pieces on the left side the enemy can sneak by on the right side to get a king.
    */
    int balance(long white, long black, int gamePhase) {
        int whiteBonus;
        int blackBonus;
        boolean whiteBalanced;
        boolean blackBalanced;

        whiteBonus = Long.bitCount(white & LEFT_SIDE) - Long.bitCount(white & RIGHT_SIDE);
        whiteBalanced = (whiteBonus <= 1 && whiteBonus >= -1);

        blackBonus = Long.bitCount(black & LEFT_SIDE) - Long.bitCount(black & RIGHT_SIDE);
        blackBalanced = (blackBonus <= 1 && blackBonus >= -1);

        if (whiteBalanced == true) {
            whiteBonus = BONUS_BALANCE[gamePhase];
        } else if (blackBalanced == true) {
            whiteBonus = PEN_BALANCE[gamePhase];
        } else {
            whiteBonus = 0;
        }

        if (blackBalanced == true) {
            blackBonus = BONUS_BALANCE[gamePhase];
        } else if (whiteBalanced == true) {
            blackBonus = PEN_BALANCE[gamePhase];
        } else {
            blackBonus = 0;
        }

        return whiteBonus - blackBonus;
    }

    // Return a bonus if we control the river
    int diagonalControl(long whitekings, long blackKings, long occupied, int gamePhase) {
	    int whiteBonus = 0;
        int blackBonus = 0;

        // river
        if (Long.bitCount(occupied & RIGHT_DIAGONAL[5]) == 1) {
            if (in(whitekings, (1L << 5) | (1L << 46))) {
                whiteBonus = BONUS_RIVER[gamePhase];
            } else if (in(blackKings, (1L << 5) | (1L << 46))) {
                blackBonus = BONUS_RIVER[gamePhase];
            }
        }

        return whiteBonus - blackBonus;
    }

    // Return a bonus if we can exploit an opponents bridge
    int bridgeExploit(long white, long black, long whiteKings, long blackKings, int gamePhase) {
        int whitePenalty = 0;
        int blackPenalty = 0;

        if (form(white, WHITE_SB)
                && not_in(white, WHITE_SBE & ~WHITE_SB)
                && (in(black, (1L << 39)) || in(black, (1L << 37)))
                && not_in(whiteKings, LEFT_DIAGONAL[3] | LEFT_DIAGONAL[5] | RIGHT_DIAGONAL[5] | RIGHT_DIAGONAL[7])) {
            ++whitePenalty;
        }

        if (form(white, WHITE_WB)
                && not_in(white, WHITE_WBE & ~WHITE_WB)
                && in(black, (1L << 38))
                && not_in(whiteKings, LEFT_DIAGONAL[4] | RIGHT_DIAGONAL[6])) {
            ++whitePenalty;
        }

        if (form(black, BLACK_SB)
                && not_in(black, BLACK_SBE & ~BLACK_SB)
                && (in(black, (1L << 12)) || in(black, (1L << 14)))
                && not_in(whiteKings, LEFT_DIAGONAL[6] | LEFT_DIAGONAL[8] | RIGHT_DIAGONAL[3] | RIGHT_DIAGONAL[5])) {
            ++blackPenalty;
        }

        if (form(black, BLACK_WB)
                && not_in(black, BLACK_WBE & ~BLACK_WB)
                && in(black, (1L << 13))
                && not_in(whiteKings, LEFT_DIAGONAL[7] | RIGHT_DIAGONAL[4])) {
            ++blackPenalty;
        }

        return (whitePenalty - blackPenalty) * PEN_BRIDGE[gamePhase];
    }

    // Return a bonus if we control the middle
    int controlMiddle(long white, long black, int gamePhase) {
        return (Long.bitCount(white & middle) - Long.bitCount(black & middle)) * BONUS_MIDDLE[gamePhase];
    }

    // Return a bonus if we have any pieces that can become a king on the next move
    int runAway(long whitePieces, long blackPieces, long empty, int gamePhase) {
        long pieces = whitePieces & ROW[2];
        long potentialWhiteKings = ((((pieces & ~COLUMN[1]) >> 6) & empty) << 6) | (((pieces >> 5) & empty) << 5);
        pieces = blackPieces & ROW[9];
        long potentialBlackKings = (((pieces << 5) & empty) >> 5) | ((((pieces & ~COLUMN[10]) << 6) & empty) >> 6);

        return (Long.bitCount(potentialWhiteKings) - Long.bitCount(potentialBlackKings)) * BONUS_RUNAWAY[gamePhase];
    }

    protected static class AIStoppedException extends Exception {
    }
}
