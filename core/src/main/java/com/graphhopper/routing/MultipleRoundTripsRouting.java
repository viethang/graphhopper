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
    EncodingManager encodingManager;

    private int MAX_TRIP_NB = 20;
    private int MAX_EDGE_POOL_NB = 10000;


    public MultipleRoundTripsRouting(Graph graph, Weighting weighting, TraversalMode tMode, double minDistance, double maxDistance) {
        super(graph, weighting, TraversalMode.EDGE_BASED);
        if (minDistance > maxDistance) {
            throw new AssertionError("Min distance cannot be smaller than max distance");
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        encodingManager = ((GraphHopperStorage) graph.getBaseGraph()).getEncodingManager();

        initCollections();
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
// cf routing/ev/RoadClass RoadAccess
        boolean done = false;
        int nbEdges = 0;
        int nbEdgesAdded2Pool = 0;
        while (!done) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {
                nbEdges++;

                if (currEdge.edge == iter.getEdge()) {
                    if (currEdge.weight < minDistance / 2) {
                        continue;
                    }
                }

                double nEdgeLength = iter.getDistance();
                MRTEntry nEdge = new MRTEntry(iter.getEdge(),
                        iter.getAdjNode(), currEdge.weight + nEdgeLength
                );

                nEdge.parent = currEdge;

                nEdge.indexOnPath = currEdge.indexOnPath + 1;

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

                if (nEdge.weight > maxDistance) {
                    continue;
                }


                calculateEdgeScore(nEdge, iter);

                // we don't allow repeated an edge in the same direction
                if (repeatedEdge(nEdge)) {
                    continue;
                }

                if (checkAndPenalizeRepeatedVertex(nEdge, nEdgeLength)) {
                    continue;
                }



                if (iter.getAdjNode() == this.from) {
                    continue;
                }


                toBeCheckedEdges.add(nEdge);
                nbEdgesAdded2Pool++;
            }

            if (toBeCheckedEdges.isEmpty()) {
                break;
            }

            currEdge = toBeCheckedEdges.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");

        }

        System.out.println(String.format("Finished. nbEdges: %d, nbEdgesAdded2Pool: %d, lastEdges: %d", nbEdges, nbEdgesAdded2Pool, lastEdges.size()));
    }

    private void calculateEdgeScore(MRTEntry nEdge, EdgeIterator iter) {
        double nEdgeLength = iter.getDistance();

        // calculate edge score
        EnumEncodedValue roadClassEnv = encodingManager.getEnumEncodedValue("road_class", Enum.class);
        Enum roadClass = iter.get(roadClassEnv);
        int scoreCoeff = 0;
        if (RoadClass.MOTORWAY.equals(roadClass) || RoadClass.TRUNK.equals(roadClass) ||
                RoadClass.PRIMARY.equals(roadClass)) {
            scoreCoeff -= 10;
        }


        if (RoadClass.SECONDARY.equals(roadClass) ||
                RoadClass.TERTIARY.equals(roadClass)) {
            scoreCoeff -= 5;
        }

//                if (RoadClass.RESIDENTIAL.equals(roadClass)) {
//                    scoreCoeff += 2;
//                }

        if (RoadClass.FOOTWAY.equals(roadClass)) {
            scoreCoeff += 5;
        }
        if (RoadClass.PATH.equals(roadClass)) {
            scoreCoeff += 10;
        }
        if (RoadClass.PEDESTRIAN.equals(roadClass)) {
            scoreCoeff += 3;
        }
        nEdge.score = ((MRTEntry) nEdge.parent).score + scoreCoeff * nEdgeLength;
    }

    private void initCollections() {
        toBeCheckedEdges = MinMaxPriorityQueue
                .orderedBy(
                        new Comparator<MRTEntry>(
                        ) {
                            @Override
                            public int compare(MRTEntry t1, MRTEntry t2) {
                                if (t2.score - t1.score > 0) return 1;
                                if (t2.score - t1.score < 0) return -1;
                                return 0;
                            }
                        })
                .maximumSize(MAX_EDGE_POOL_NB)
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

    private boolean checkAndPenalizeRepeatedVertex(MRTEntry nEdge, double nEdgeLength) {
        int repeated = 0;
        MRTEntry ancestor = nEdge;
        int ancestorDepth = 0;
        while (ancestor.parent != null) {
            ancestor = (MRTEntry) ancestor.parent;
            ancestorDepth++;
            if (ancestor.adjNode == nEdge.adjNode) {
                repeated++;
                // penalize edges that passes through a vertex already on the path
                // the penalized quantity depends on how deep the repeated vertex is
                // and the distance to the destination
                // We want to avoid immediate 180 degree turn and short subcycle
                //

//                if (nEdge.weight < maxDistance/2) {
                    nEdge.score -= nEdgeLength * (5 * (1 - nEdge.weight / maxDistance) * (1 - ancestorDepth / nEdge.indexOnPath));
//                } else {
//                    nEdge.score -= nEdgeLength * ((1 - nEdge.weight / maxDistance) * (1 - ancestorDepth / nEdge.indexOnPath));
//                }

                // we don't allow passing 3 times through the same vertex
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
