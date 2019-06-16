package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;

public class DefaultTurnCostHandler implements TurnCostHandler {
    private final TurnCostExtension turnCostExtension;
    private final TurnCostEncoder turnCostEncoder;
    private double defaultUTurnCost = Double.POSITIVE_INFINITY;

    public DefaultTurnCostHandler(TurnCostExtension turnCostExtension, TurnCostEncoder turnCostEncoder) {
        this.turnCostExtension = turnCostExtension;
        this.turnCostEncoder = turnCostEncoder;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == EdgeIterator.NO_EDGE || outEdge == EdgeIterator.NO_EDGE) {
            return 0;
        }
        if (turnCostExtension.isUTurn(inEdge, outEdge)) {
            return defaultUTurnCost;
        }
        long turnFlags = turnCostExtension.getTurnCostFlags(inEdge, viaNode, outEdge);
        if (turnCostEncoder.isTurnRestricted(turnFlags))
            return Double.POSITIVE_INFINITY;
        return turnCostEncoder.getTurnCost(turnFlags);
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
        this.defaultUTurnCost = uTurnCosts;
    }
}
