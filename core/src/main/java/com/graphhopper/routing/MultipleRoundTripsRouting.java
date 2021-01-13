package com.graphhopper.routing;

import com.google.common.collect.MinMaxPriorityQueue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;

import java.util.*;

public class MultipleRoundTripsRouting extends AbstractRoutingAlgorithm {
    protected List<MRTEntry> lastEdges;
    protected MinMaxPriorityQueue<MRTEntry> toBeCheckedEdges;
    protected MRTEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private int from;
    double maxDistance;
    double minDistance;

    private int MAX_TO_CHECK_EDGES_NB = 20;
    private int MAX_TRIP_NB = 5;


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
        currEdge = new MRTEntry(from, 0);

        runAlgo();
        return extractPath();
    }

    public List<Path> calcPaths(int from, int to) {

        checkAlreadyRun();
        this.to = to;
        currEdge = new MRTEntry(from, 0);

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
            for (MRTEntry edge : lastEdges) {
                Path path = buildPath(edge);
                paths.add(path);
            }
        }

        return filter(paths);
    }

    protected Path buildPath(MRTEntry lastEdge) {
        // trace back from lastEdge to return the full path
        Path path = PathExtractor.extractPath(graph, weighting, lastEdge);
        return path;
    }

    protected void runAlgo() {
        EncodingManager encodingManager = ((GraphHopperStorage) graph.getBaseGraph()).getEncodingManager();
// cf routing/ev/RoadClass RoadAccess
        boolean done = false;
        while (!done) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {

                if (currEdge.edge == iter.getEdge()) {
                    if (currEdge.weight < minDistance / 2) {
                        continue;
                    }
                }

                double nEdgeLength = iter.getDistance();

                MRTEntry nEdge = new MRTEntry(iter.getEdge(),
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

                if (repeatedVertex(nEdge)) {
                    continue;
                }

                if (iter.getAdjNode() == this.to) {
                    if (nEdge.weight >= minDistance) {
                        // the new edge is adjacent to the destination, add it to the lastEdges list
                        // no need to check edges adjacent to this edge any more
                        lastEdges.add(nEdge);
                        if (lastEdges.size() >= MAX_TRIP_NB) {
                            done = true;
                            break;
                        }
                    }
                    continue;
                }

                if (iter.getAdjNode() == this.from) {
                    continue;
                }


                // calculate edge score
                EnumEncodedValue roadClassEnv = encodingManager.getEnumEncodedValue("road_class", Enum.class);
                Enum roadClass = iter.get(roadClassEnv);
                if (RoadClass.MOTORWAY.equals(roadClass) || RoadClass.TRUNK.equals(roadClass) ||
                        RoadClass.PRIMARY.equals(roadClass) ||
                        RoadClass.SECONDARY.equals(roadClass) ||
                        RoadClass.SECONDARY.equals(roadClass)) {
                    nEdge.score -= 10;
                }

                if (RoadClass.SECONDARY.equals(roadClass) ||
                        RoadClass.TERTIARY.equals(roadClass)) {
                    nEdge.score -= 5;
                }

                if (RoadClass.RESIDENTIAL.equals(roadClass)) {
                    nEdge.score += 2;
                }

                if (RoadClass.FOOTWAY.equals(roadClass)) {
                    nEdge.score += 5;
                }
                if (RoadClass.PATH.equals(roadClass)) {
                    nEdge.score += 5;
                }
                if (RoadClass.PEDESTRIAN.equals(roadClass)) {
                    nEdge.score += 3;
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
        toBeCheckedEdges = MinMaxPriorityQueue
                .orderedBy(
                        new Comparator<MRTEntry>(
                        ) {
                            @Override
                            public int compare(MRTEntry t1, MRTEntry t2) {
                                return t2.score - t1.score;
                            }
                        })
                .maximumSize(1000)
                .create();


        lastEdges = new LinkedList<>();
    }

    private boolean repeatedEdge(MRTEntry nEdge) {
        // check if the path repeats an edge in the same direction
        MRTEntry ancestor = nEdge;
        while (ancestor.parent != null) {
            ancestor = (MRTEntry) ancestor.parent;
            if (ancestor.edge == nEdge.edge && ancestor.adjNode == nEdge.adjNode) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatedVertex(MRTEntry nEdge) {
        int repeated = 0;
        MRTEntry ancestor = nEdge;
        while (ancestor.parent != null) {
            ancestor = (MRTEntry) ancestor.parent;
            if (ancestor.adjNode == nEdge.adjNode) {
                repeated++;
                if (repeated > 1) {
                    return true;
                }
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
        for (Path path : paths) {
            if (true) {
                filteredPath.add(path);
            }
        }
        return filteredPath;
    }
}
