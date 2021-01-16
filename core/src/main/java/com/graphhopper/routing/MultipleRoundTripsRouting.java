package com.graphhopper.routing;

import com.google.common.collect.MinMaxPriorityQueue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.TrackType;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class MultipleRoundTripsRouting extends AbstractRoutingAlgorithm {
    protected List<MRTEntry> lastEdges;
    protected List<MRTEntry> allToBeCheckedEdges;
    protected MinMaxPriorityQueue<MRTEntry> toBeCheckedEdges;
    protected MRTEntry currEdge;
    private int visitedNodes;
    private int to = -1;
    private int from;
    double maxDistance;
    double minDistance;
    EncodingManager encodingManager;

    private int MAX_TRIP_NB = 50;
    private int MAX_EDGE_POOL_NB = 5000;


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

        return extractPath();
    }

    public List<Path> calcPaths(int from, int to) {

        checkAlreadyRun();
        this.to = to;
        currEdge = new MRTEntry(from, 0);

        return runAlgo();
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

    protected List<Path> runAlgo() {
        boolean done = false;
        double returnDistance = maxDistance / 2;
        List<MRTEntry> arrivedEdges = new LinkedList<>();
        while (!done) {
            visitedNodes++;
            if (isMaxVisitedNodesExceeded() || finished())
                break;

            int currNode = currEdge.adjNode;
            EdgeIterator iter = edgeExplorer.setBaseNode(currNode);
            while (iter.next()) {

                double nEdgeLength = iter.getDistance();
                MRTEntry nEdge = new MRTEntry(iter.getEdge(),
                        iter.getAdjNode(), currEdge.weight + nEdgeLength
                );

                nEdge.parent = currEdge;

                if (nEdge.adjNode == this.to) {
                    if (nEdge.weight > minDistance) {
                        arrivedEdges.add(nEdge);
                    }
                    continue;
                }

                if (repeatedEdge(nEdge)) {
                    continue;
                }

                if (repeatedVertex(nEdge)) {
                    continue;
                }

                if (nEdge.weight >= returnDistance) {
                    // the new edge is adjacent to the destination, add it to the lastEdges list
                    // no need to check edges adjacent to this edge any more
                    lastEdges.add(nEdge);
                    if (lastEdges.size() >= MAX_TRIP_NB) {
                        done = true;
                        break;
                    }
                    continue;
                }

                calculateEdgeScore(nEdge, iter);

                toBeCheckedEdges.add(nEdge);
            }

            if (toBeCheckedEdges.isEmpty()) {
                break;
            }

            currEdge = toBeCheckedEdges.poll();
            if (currEdge == null)
                throw new AssertionError("Empty edge cannot happen");

        }


        List<Path> paths = new LinkedList<>();
        for (MRTEntry edge : lastEdges) {
            // use Dijkstra to find shortest path from the last edges to the destination
            // then create the combined path
            Dijkstra dijkstra = new Dijkstra(graph, weighting, TraversalMode.EDGE_BASED);
            dijkstra.calcPath(edge.adjNode, this.to);
            SPTEntry firstEdgeOfDijkstraPath = getFirstAncestorEdge(dijkstra.currEdge);
            firstEdgeOfDijkstraPath.parent = edge;

            Path path = PathExtractor.extractPath(graph, weighting, dijkstra.currEdge);
            paths.add(path);
        }

        return paths;
    }

    private SPTEntry getFirstAncestorEdge(SPTEntry edge) {
        SPTEntry ancestor = edge;
        while (ancestor.parent.edge != EdgeIterator.NO_EDGE) {
            ancestor = ancestor.parent;
        }
        return ancestor;
    }

    private void calculateEdgeScore(MRTEntry nEdge, EdgeIterator iter) {
        double nEdgeLength = iter.getDistance();

        // calculate edge score
        EnumEncodedValue roadClassEnv = encodingManager.getEnumEncodedValue("road_class", Enum.class);
        Enum roadClass = iter.get(roadClassEnv);
        EnumEncodedValue trackTypeEnv = encodingManager.getEnumEncodedValue("track_type", Enum.class);
        Enum trackType = TrackType.MISSING;
        try {
            trackType = iter.get(trackTypeEnv);
        } catch (Exception e) {
            System.out.println("Exception tracktype" + e.toString());
        }
        int scoreCoeff = 0;
        if (RoadClass.MOTORWAY.equals(roadClass) || RoadClass.TRUNK.equals(roadClass) ||
                RoadClass.PRIMARY.equals(roadClass)) {
            scoreCoeff -= 10;
        } else if (RoadClass.SECONDARY.equals(roadClass) ||
                RoadClass.TERTIARY.equals(roadClass)) {
            scoreCoeff -= 5;
        } else if (RoadClass.FOOTWAY.equals(roadClass)) {
            scoreCoeff += 5;
        } else if (RoadClass.PATH.equals(roadClass)) {
            scoreCoeff += 10;
        } else if (RoadClass.PEDESTRIAN.equals(roadClass)) {
            scoreCoeff += 3;
        } else if (!TrackType.MISSING.equals(trackType)) {
            scoreCoeff += 5;
        } else {
            scoreCoeff += 1;
        }

        nEdge.score =
//                ((MRTEntry) nEdge.parent).score +
                scoreCoeff;
    }

    private void initCollections() {
        allToBeCheckedEdges = new LinkedList<>();
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


    private boolean checkRepeatedEdgeCondition(MRTEntry nEdge) {
        MRTEntry ancestor = nEdge;
        int repeated = 0;
        // forbid 180 degree turn if already too far
        // should allow this only around the furthest point of the trip
        if (((MRTEntry) nEdge.parent).edge == nEdge.edge && (nEdge.weight > maxDistance / 2 + 500 || nEdge.weight < maxDistance / 2 - 500)) {
            return true;
        }
        while (ancestor.parent != null) {
            ancestor = (MRTEntry) ancestor.parent;

            // forbid repeating an edge in the same direction
            if (ancestor.edge == nEdge.edge) {
                repeated++;
                if (ancestor.adjNode == nEdge.adjNode)
                    return true;

            }

            if (ancestor.weight > maxDistance / 2) {
                return true;
            }

            //
            // while repeating an edge in a different direction is allowed
            // repeating more than once is forbidden
            if (repeated > 1)
                return true;
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
                if (ancestor.weight > maxDistance / 2) {
                    return true;
                }
                repeated++;
                if (ancestor.weight > maxDistance / 2 + 500) {
                    return true;
                }

                // penalize edges that passes through a vertex already on the path
                // the penalized quantity depends on how deep the repeated vertex is
                // and the distance to the destination
                // We want to avoid immediate 180 degree turn and short subcycle
                //


                if (nEdge.weight < maxDistance / 2) {
                    nEdge.score -= (10 * (1 - nEdge.weight / maxDistance) * (1 - ancestorDepth / nEdge.indexOnPath));
                } else {
                    nEdge.score -= (5 * (1 - nEdge.weight / maxDistance) * (1 - ancestorDepth / nEdge.indexOnPath));
                }

                // we don't allow passing 3 times through the same vertex
                if (repeated > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean repeatedEdge(SPTEntry nEdge) {
        SPTEntry ancestor = nEdge;
        while (ancestor.parent != null) {
            ancestor = ancestor.parent;
            if (ancestor.edge == nEdge.edge) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatedVertex(SPTEntry nEdge) {
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
        for (Path path : paths) {
            if (true) {
                filteredPath.add(path);
            }
        }
        return filteredPath;
    }
}
