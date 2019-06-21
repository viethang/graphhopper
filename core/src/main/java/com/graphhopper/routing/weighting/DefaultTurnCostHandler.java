package com.graphhopper.routing.weighting;

public class DefaultTurnCostHandler implements TurnCostHandler {
    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    @Override
    public void setDefaultUTurnCost(double uTurnCosts) {
        throw new IllegalArgumentException("You cannot set default u-turn costs on a DefaultTurnCostHandler");
    }
}
