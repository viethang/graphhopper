package com.graphhopper.routing.ch;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.storage.ShortcutUnpacker;
import com.graphhopper.util.CHEdgeIteratorState;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

import static com.graphhopper.util.EdgeIterator.NO_EDGE;


public class EdgeBasedPathCH extends Path4CH {

    public EdgeBasedPathCH(Graph routingGraph, Graph baseGraph, final Weighting weighting) {
        super(routingGraph, baseGraph, weighting);
    }

    @Override
    protected ShortcutUnpacker getShortcutUnpacker(Graph routingGraph, final Weighting weighting) {
        return new ShortcutUnpacker(routingGraph, new ShortcutUnpacker.Visitor() {
            @Override
            public void visit(EdgeIteratorState edge, boolean reverse, int prevOrNextEdgeId) {
                distance += edge.getDistance();
                time += weighting.calcMillis(edge, reverse, prevOrNextEdgeId);
                addEdge(edge.getEdge());
            }
        }, true);
    }

    @Override
    protected int getIncEdge(SPTEntry entry) {
        return ((CHEntry) entry).incEdge;
    }
}
