package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIterator;

public class DefaultTurnCostHandler implements TurnCostHandler {

    public static double DEFAULT_FINITE_UTURN_COSTS = 40;

    private double uTurnCost;

    public DefaultTurnCostHandler() {
        this(Double.POSITIVE_INFINITY);
    }

    /**
     * @param uTurnCost the cost of a u-turn in seconds
     */
    public DefaultTurnCostHandler(double uTurnCost) {
        this.uTurnCost = uTurnCost;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == EdgeIterator.NO_EDGE || outEdge == EdgeIterator.NO_EDGE) {
            return 0;
        }
        return inEdge == outEdge ? uTurnCost : 0;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        double turnWeight = calcTurnWeight(inEdge, viaNode, outEdge);
        if (Double.isInfinite(turnWeight)) {
            return Long.MAX_VALUE;
        }
        return 1000 * (long) turnWeight;
    }

}
