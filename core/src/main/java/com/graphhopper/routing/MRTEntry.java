package com.graphhopper.routing;

public class MRTEntry extends SPTEntry {
    public int score;
    public MRTEntry(int edgeId, int adjNode, double weight) {
        super(edgeId, adjNode, weight);
    }

    public MRTEntry(int node, double weight) {
        super(node, weight);
    }

    public int getScore() {
        return score;
    }
}
