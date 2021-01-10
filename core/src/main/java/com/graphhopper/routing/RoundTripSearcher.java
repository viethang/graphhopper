package com.graphhopper.routing;

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

public class RoundTripSearcher extends AbstractRoutingAlgorithm {
    protected IntObjectMap<SPTEntry> collectedEdges;
    protected List<SPTEntry> lastEdges;
    Map<Integer, Integer> edgeOccurrence = new HashMap(); // store the number of occurrences of an edge in collectedEdges
    protected PriorityQueue<SPTEntry> toBeCheckedEdges;
    protected SPTEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private int from;
    double maxDistance;
    double minDistance;


    public RoundTripSearcher(Graph graph, Weighting weighting, TraversalMode tMode) {
        super(graph, weighting, TraversalMode.EDGE_BASED);
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 2000);
        initCollections(size);
    }

    public Path calcPath(int from, int to) {
        checkAlreadyRun();
        this.to = to;
        this.from = from;
        currEdge = new SPTEntry(from, 0);
        if (!traversalMode.isEdgeBased()) {
            collectedEdges.put(from, currEdge);
        }
        runAlgo();
        return extractPath();
    }

    public List<Path> calcPaths(int from, int to, double maxDistance, double minDistance) {
        if (minDistance > maxDistance) {
            throw new AssertionError("Min distance cannot be smaller than max distance");
        }
        this.maxDistance = maxDistance;
        this.minDistance = minDistance;
        checkAlreadyRun();
        this.to = to;
        currEdge = new SPTEntry(from, 0);
        if (!traversalMode.isEdgeBased()) {
            collectedEdges.put(from, currEdge);
        }
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
                int traversalId = createTraversalId(iter);

                double nEdgeLength = iter.getDistance();

                SPTEntry nEdge = new SPTEntry(iter.getEdge(),
                        iter.getAdjNode(),
                        currEdge.weight + nEdgeLength
                );

                nEdge.parent = currEdge;

                if (nEdge.weight > maxDistance) {
                    continue;
                }

                if (repeatedEdge(nEdge)) {
                    continue;
                }
                collectedEdges.put(traversalId, nEdge);

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


    private int createTraversalId(EdgeIterator iter) {
        // return a unique id for and edge based on it's occurrence in collectedEdges
        int occurrence = edgeOccurrence.get(iter.getEdge()) != null ? edgeOccurrence.get(iter.getEdge()) : 0;
        int edgeId = iter.getEdge();
        edgeId = edgeId << occurrence + 1;
        int nodeA = iter.getBaseNode();
        int nodeB = iter.getAdjNode();
        return nodeA > nodeB ? edgeId + 1 : edgeId;
    }

    private void initCollections(int size) {
        toBeCheckedEdges = new PriorityQueue<>(size);
        collectedEdges = new GHIntObjectHashMap<>(size);
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

    @Override
    public int getVisitedNodes() {
        return visitedNodes;
    }

    List<Path> filter(List<Path> paths) {
        List<Path> filteredPath = new LinkedList<>();
        // add valid paths here
        return filteredPath;
    }
}
