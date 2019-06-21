package com.graphhopper.routing.weighting;

import com.graphhopper.util.EdgeIterator;

public class DefaultTurnCostHandler implements TurnCostHandler {

    private double defaultUTurnCost = Double.POSITIVE_INFINITY;

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == EdgeIterator.NO_EDGE || outEdge == EdgeIterator.NO_EDGE) {
            return 0;
        }
        return inEdge == outEdge ? defaultUTurnCost : 0;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        double turnWeight = calcTurnWeight(inEdge, viaNode, outEdge);
        if (Double.isInfinite(turnWeight)) {
            return Long.MAX_VALUE;
        }
        return 1000 * (long) turnWeight;
    }

    @Override
    public void setDefaultUTurnCost(double uTurnCosts) {
        defaultUTurnCost = uTurnCosts;
    }
}
