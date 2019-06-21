package com.graphhopper.routing.weighting;

public interface TurnCostHandler {

    double calcTurnWeight(int inEdge, int viaNode, int outEdge);

    long calcTurnMillis(int inEdge, int viaNode, int outEdge);

}
