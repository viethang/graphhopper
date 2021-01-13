package com.graphhopper.routing;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

public class MultipleRoundTripsRouting extends AbstractRoutingAlgorithm {
    protected List<SPTEntry> lastEdges;
    protected PriorityQueue<SPTEntry> toBeCheckedEdges;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private int from;
    double maxDistance;
    double minDistance;


    public MultipleRoundTripsRouting(Graph graph, Weighting weighting, TraversalMode tMode, double minDistance, double maxDistance) {
        super(graph, weighting, TraversalMode.EDGE_BASED);
        if (minDistance > maxDistance) {
            throw new AssertionError("Min distance cannot be smaller than max distance");
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    public Path calcPath(int from, int to) {
        checkAlreadyRun();
        this.to = to;
        this.from = from;
        currEdge = new SPTEntry(from, 0);

        runAlgo();
        return extractPath();
    }

    public List<Path> calcPaths(int from, int to) {

        checkAlreadyRun();
        this.to = to;
        currEdge = new SPTEntry(from, 0);

        runAlgo();

        return extractPaths();
    }

    @Override
    protected boolean finished() {
        return false;
    }

    @Override
    protected Path extractPath() {
        return createEmptyPath();
    }

    protected List<Path> extractPaths() {
        List<Path> paths = new ArrayList<>();

        if (!lastEdges.isEmpty()) {
            for (SPTEntry edge : lastEdges) {
                Path path = buildPath(edge);
                paths.add(path);
            }
        }

        return filter(paths);
    }

    protected Path buildPath(SPTEntry lastEdge) {
        // trace back from lastEdge to return the full path
        Path path = PathExtractor.extractPath(graph, weighting, lastEdge);
        return path;
    }

    protected void runAlgo() {
        while (true) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {

                if (currEdge.edge == iter.getEdge()) {
                    continue;
                }

                double nEdgeLength = iter.getDistance();

                SPTEntry nEdge = new SPTEntry(iter.getEdge(),
                        iter.getAdjNode(),
                        currEdge.weight + nEdgeLength
                );

                nEdge.parent = currEdge;

                if (nEdge.weight > maxDistance) {
                    continue;
                }

                // we don't allow repeated an edge in the same direction
                if (repeatedEdge(nEdge)) {
                    continue;
                }

                // don't allow cycle if still too close to the origin or the destination
                // this make sense especially when from = to
                if (repeatedVertext(nEdge)
                        && (nEdge.weight < 3 * maxDistance/8 || nEdge.weight > 5 * maxDistance/8)
                        && nEdge.adjNode != this.to) {
                    continue;
                }

                if (iter.getAdjNode() == this.to) {
                    if (nEdge.weight >= minDistance) {
                        // the new edge is adjacent to the destination, add it to the lastEdges list
                        // no need to check edges adjacent to this edge any more
                        lastEdges.add(nEdge);
                    }
                    continue;
                }

                if (iter.getAdjNode() == this.from) {
                    continue;
                }

                toBeCheckedEdges.add(nEdge);
            }

            if (toBeCheckedEdges.isEmpty()) {
                break;
            }

            currEdge = toBeCheckedEdges.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");

        }
    }



    private void initCollections(int size) {
        toBeCheckedEdges = new PriorityQueue<>(size);
        lastEdges = new LinkedList<>();


    }

    private boolean repeatedEdge(SPTEntry nEdge) {
        // check if the path repeats an edge in the same direction
        SPTEntry ancestor = nEdge;
        while (ancestor.parent != null) {
            ancestor = ancestor.parent;
            if (ancestor.edge == nEdge.edge && ancestor.adjNode == nEdge.adjNode) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatedVertext(SPTEntry nEdge) {
        SPTEntry ancestor = nEdge;
        while (ancestor.parent != null) {
            ancestor = ancestor.parent;
            if (ancestor.adjNode == nEdge.adjNode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    List<Path> filter(List<Path> paths) {
        List<Path> filteredPath = new LinkedList<>();
        // add valid paths here
        for (Path path: paths) {
            if (true) {
                filteredPath.add(path);
            }
        }
        return filteredPath;
    }
}
