/*
 *  Desarrollo por Maximiliano Brignone � 2021
 *  https://github.com/mbrignone93
 *
 * */

package com.chess.engine.player.ai;

import com.chess.engine.board.Board;
import com.chess.engine.board.BoardUtils;
import com.chess.engine.board.Move;
import com.chess.engine.board.MoveTransition;
import com.google.common.collect.ComparisonChain;
import com.google.common.primitives.Ints;

import java.util.*;

import static com.chess.engine.board.Move.*;
import static com.google.common.collect.Ordering.from;

public class IterativeDeepening extends Observable implements MoveStrategy {

    private final BoardEvaluator evaluator;
    private final int searchDepth;
    private final MoveSorter moveSorter;
    private long boardsEvaluated;
    private long executionTime;
    private int cutOffsProduced;

    private enum MoveSorter {

        SORT {
            @Override
            Collection<Move> sort(final Collection<Move> moves) {
                return from(SMART_SORT).immutableSortedCopy(moves);
            }
        };

        public static Comparator<Move> SMART_SORT = new Comparator<Move>() {
            @Override
            public int compare(final Move move1, final Move move2) {
                return ComparisonChain.start()
                        .compareTrueFirst(BoardUtils.isThreatenedBoardImmediate(move1.getBoard()), BoardUtils.isThreatenedBoardImmediate(move2.getBoard()))
                        .compareTrueFirst(move1.isAttack(), move2.isAttack())
                        .compareTrueFirst(move1.isCastlingMove(), move2.isCastlingMove())
                        .compare(move2.getMovedPiece().getPieceValue(), move1.getMovedPiece().getPieceValue())
                        .result();
            }
        };

        abstract Collection<Move> sort(Collection<Move> moves);
    }

    public IterativeDeepening(final int searchDepth) {
        this.evaluator = StandardBoardEvaluator.get();
        this.searchDepth = searchDepth;
        this.moveSorter = MoveSorter.SORT;
        this.boardsEvaluated = 0;
        this.cutOffsProduced = 0;
    }

    @Override
    public String toString() {
        return "ID";
    }

    @Override
    public long getNumBoardsEvaluated() {
        return this.boardsEvaluated;
    }

    @Override
    public Move execute(final Board board) {

        final long startTime = System.currentTimeMillis();
        System.out.println(board.currentPlayer() + " AI Pensando con Profundidad " + this.searchDepth);

        MoveOrderingBuilder builder = new MoveOrderingBuilder();
        builder.setOrder(board.currentPlayer().getAlliance().isWhite() ? Ordering.DESC : Ordering.ASC);
        for(final Move move : board.currentPlayer().getLegalMoves()) {
            builder.addMoveOrderingRecord(move, 0);
        }

        Move bestMove = MoveFactory.getNullMove();
        int currentDepth = 1;

        int highestSeenValue = Integer.MIN_VALUE;
        int lowestSeenValue = Integer.MAX_VALUE;

        while (currentDepth <= this.searchDepth) {
            final long subTimeStart = System.currentTimeMillis();
            int currentValue;
            final List<MoveScoreRecord> records = builder.build();
            builder = new MoveOrderingBuilder();
            builder.setOrder(board.currentPlayer().getAlliance().isWhite() ? Ordering.DESC : Ordering.ASC);
            for (final MoveScoreRecord record : records) {
                final Move move = record.getMove();
                final MoveTransition moveTransition = board.currentPlayer().makeMove(move);
                if (moveTransition.getMoveStatus().isDone()) {
                    currentValue = board.currentPlayer().getAlliance().isWhite() ?
                            min(moveTransition.getToBoard(), currentDepth - 1, highestSeenValue, lowestSeenValue) :
                            max(moveTransition.getToBoard(), currentDepth - 1, highestSeenValue, lowestSeenValue);
                    builder.addMoveOrderingRecord(move, currentValue);
                    if (board.currentPlayer().getAlliance().isWhite() && currentValue > highestSeenValue) {
                        highestSeenValue = currentValue;
                        bestMove = move;
                    } else if (board.currentPlayer().getAlliance().isBlack() && currentValue < lowestSeenValue) {
                        lowestSeenValue = currentValue;
                        bestMove = move;
                    }
                }
            }
            final long subTime = System.currentTimeMillis()- subTimeStart;
            System.out.println("\t" +toString()+ " Mejor Movimiento = " +bestMove+ " Profundidad = " +currentDepth+ " Elijo " +(subTime) + " ms, Movimientos Ordenados : " +records);
            setChanged();
            notifyObservers(bestMove);
            currentDepth++;
        }
        this.executionTime = System.currentTimeMillis() - startTime;
        System.out.printf("%s Selecciona %s [#Tableros Evaluados = %d, Tiempo Tomado = %d ms, Tasa de Evaluacion = %.1f cutoffCount = %d Porcentaje de Poda = %.2f\n", board.currentPlayer(),
                bestMove, this.boardsEvaluated, this.executionTime, (1000 * ((double)this.boardsEvaluated/this.executionTime)), this.cutOffsProduced, 100 * ((double)this.cutOffsProduced/this.boardsEvaluated));
        return bestMove;
    }

    public int max(final Board board,
                   final int depth,
                   final int highest,
                   final int lowest) {
        if (depth == 0 || BoardUtils.isEndGame(board)) {
            this.boardsEvaluated++;
            return this.evaluator.evaluate(board, depth);
        }
        int currentHighest = highest;
        for (final Move move : this.moveSorter.sort((board.currentPlayer().getLegalMoves()))) {
            final MoveTransition moveTransition = board.currentPlayer().makeMove(move);
            if (moveTransition.getMoveStatus().isDone()) {
                currentHighest = Math.max(currentHighest, min(moveTransition.getToBoard(),
                        depth - 1, currentHighest, lowest));
                if (lowest <= currentHighest) {
                    this.cutOffsProduced++;
                    break;
                }
            }
        }
        return currentHighest;
    }

    public int min(final Board board,
                   final int depth,
                   final int highest,
                   final int lowest) {
        if (depth == 0 || BoardUtils.isEndGame(board)) {
            this.boardsEvaluated++;
            return this.evaluator.evaluate(board, depth);
        }
        int currentLowest = lowest;
        for (final Move move : this.moveSorter.sort((board.currentPlayer().getLegalMoves()))) {
            final MoveTransition moveTransition = board.currentPlayer().makeMove(move);
            if (moveTransition.getMoveStatus().isDone()) {
                currentLowest = Math.min(currentLowest, max(moveTransition.getToBoard(),
                        depth - 1, highest, currentLowest));
                if (currentLowest <= highest) {
                    this.cutOffsProduced++;
                    break;
                }
            }
        }
        return currentLowest;
    }

    private static class MoveScoreRecord implements Comparable<MoveScoreRecord> {
        final Move move;
        final int score;

        MoveScoreRecord(final Move move, final int score) {
            this.move = move;
            this.score = score;
        }

        Move getMove() {
            return this.move;
        }

        int getScore() {
            return this.score;
        }

        @Override
        public int compareTo(MoveScoreRecord o) {
            return Integer.compare(this.score, o.score);
        }

        @Override
        public String toString() {
            return this.move + " : " +this.score;
        }
    }

    enum Ordering {
        ASC {
            @Override
            List<MoveScoreRecord> order(final List<MoveScoreRecord> moveScoreRecords) {
                Collections.sort(moveScoreRecords, new Comparator<MoveScoreRecord>() {
                    @Override
                    public int compare(final MoveScoreRecord o1,
                                       final MoveScoreRecord o2) {
                        return Ints.compare(o1.getScore(), o2.getScore());
                    }
                });
                return moveScoreRecords;
            }
        },
        DESC {
            @Override
            List<MoveScoreRecord> order(final List<MoveScoreRecord> moveScoreRecords) {
                Collections.sort(moveScoreRecords, new Comparator<MoveScoreRecord>() {
                    @Override
                    public int compare(final MoveScoreRecord o1,
                                       final MoveScoreRecord o2) {
                        return Ints.compare(o2.getScore(), o1.getScore());
                    }
                });
                return moveScoreRecords;
            }
        };

        abstract List<MoveScoreRecord> order(final List<MoveScoreRecord> moveScoreRecords);
    }


    private static class MoveOrderingBuilder {
        List<MoveScoreRecord> moveScoreRecords;
        Ordering ordering;

        MoveOrderingBuilder() {
            this.moveScoreRecords = new ArrayList<>();
        }

        void addMoveOrderingRecord(final Move move,
                                   final int score) {
            this.moveScoreRecords.add(new MoveScoreRecord(move, score));
        }

        void setOrder(final Ordering order) {
            this.ordering = order;
        }

        List<MoveScoreRecord> build() {
            return this.ordering.order(moveScoreRecords);
        }
    }


}