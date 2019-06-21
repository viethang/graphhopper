package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.TurnCostEncoder;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIterator;

public class JunctionWiseTurnCostHandler implements TurnCostHandler {
    private final TurnCostExtension turnCostExtension;
    private final TurnCostEncoder turnCostEncoder;
    private double defaultUTurnCost = Double.POSITIVE_INFINITY;

    public JunctionWiseTurnCostHandler(TurnCostExtension turnCostExtension, TurnCostEncoder turnCostEncoder) {
        this.turnCostExtension = turnCostExtension;
        // todonow: I wonder do we even need a flexible/configurable turn cost encoder or can this be done entirely inside
        // turn cost extension ?
        this.turnCostEncoder = turnCostEncoder;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        if (inEdge == EdgeIterator.NO_EDGE || outEdge == EdgeIterator.NO_EDGE) {
            return 0;
        }
        if (turnCostExtension.isUTurn(inEdge, outEdge)) {
            // todonow: right now we do not allow setting/changing the u-turn costs for a specific junction!
            // instead we should only apply the default u-turn costs in case no turn costs have been set
            // explicitly, but this might have performance implications, because we would *always* have to check
            // for manually set u-turn costs!!
            // the same problem will apply for default left-turn costs etc.
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

    /**
     * Set the default cost for a u-turn in seconds.
     */
    public void setDefaultUTurnCost(double uTurnCosts) {
        defaultUTurnCost = uTurnCosts;
    }
}
