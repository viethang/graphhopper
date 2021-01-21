package com.graphhopper.routing;

import com.google.common.collect.MinMaxPriorityQueue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.TrackType;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.HikingWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;

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

    private int MAX_TRIP_NB = 100;
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

    protected Path buildPath(MRTEntry lastEdge) {
        // trace back from lastEdge to return the full path
        Path path = PathExtractor.extractPath(graph, weighting, lastEdge);
        return path;
    }

    protected List<Path> runAlgo() {
        boolean done = false;
        double maxReturnDistance = maxDistance / 2 + 2000;
        double minReturnDistance = maxDistance / 2 - 2000;
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

                double score = calculateEdgeScore(nEdge, iter);
                if (score == Integer.MIN_VALUE) {
                    continue;
                }

                if (nEdge.weight >= minReturnDistance) {
                    // the new edge is adjacent to the destination, add it to the lastEdges list
                    // no need to check edges adjacent to this edge any more
                    lastEdges.add(nEdge);
                    if (lastEdges.size() >= MAX_TRIP_NB) {
                        done = true;
                        break;
                    }
                }

                if (nEdge.weight > maxReturnDistance) {
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


        List<Path> paths = new LinkedList<>();
        for (MRTEntry edge : lastEdges) {
            // use Dijkstra to find shortest path from the last edges to the destination
            // then create the combined path
            Weighting hikingWeighting = new HikingWeighting(weighting, encodingManager);
            Dijkstra dijkstra = new Dijkstra(graph, hikingWeighting, TraversalMode.EDGE_BASED);
            dijkstra.calcPath(edge.adjNode, this.to);
            SPTEntry firstEdgeOfDijkstraPath = getFirstAncestorEdge(dijkstra.currEdge);
            firstEdgeOfDijkstraPath.parent = edge;

            Path path = PathExtractor.extractPath(graph, weighting, dijkstra.currEdge);
//            Path path = PathExtractor.extractPath(graph, weighting, edge);
            paths.add(path);
        }

        return filter(paths);
    }

    private SPTEntry getFirstAncestorEdge(SPTEntry edge) {
        SPTEntry ancestor = edge;
        while (ancestor.parent.edge != EdgeIterator.NO_EDGE) {
            ancestor = ancestor.parent;
        }
        return ancestor;
    }

    private double calculateEdgeScore(MRTEntry nEdge, EdgeIterator iter) {
        // calculate edge score
        EnumEncodedValue roadClassEnv = encodingManager.getEnumEncodedValue(RoadClass.KEY, Enum.class);
        Enum roadClass = iter.get(roadClassEnv);
        EnumEncodedValue trackTypeEnv = encodingManager.getEnumEncodedValue(TrackType.KEY, Enum.class);
        Enum trackType = iter.get(trackTypeEnv);
        EnumEncodedValue roadEnviEnv = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, Enum.class);
        Enum roadEnv = iter.get(roadEnviEnv);
        int scoreCoeff = 0;
        if (roadEnv.equals(RoadEnvironment.FERRY)) {
            // should never use ferry
            scoreCoeff = Integer.MIN_VALUE;
        } else if (RoadClass.MOTORWAY.equals(roadClass) || RoadClass.TRUNK.equals(roadClass) ||
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

        nEdge.score = scoreCoeff;
        return scoreCoeff;
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
        // filter valid paths
        for (Path path : paths) {
            if (path.getDistance() < 1.2 * maxDistance || path.getDistance() < minDistance / 1.1) {
                filteredPath.add(path);
            }
        }
        filteredPath.sort(new Comparator<Path>() {
            @Override
            public int compare(Path p1, Path p2) {
                double eleScore1 = calculateElevationScore(p1);
                double eleScore2 = calculateElevationScore(p2);
                if (eleScore1 > eleScore2) {
                    return 1;
                } else if (eleScore1 < eleScore2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
        List<Path> retainedPaths = new LinkedList<>();
        for (Path path1 : filteredPath) {
            boolean retained = true;
            for (Path path2 : retainedPaths) {
                if (calculateSimilarity(path1, path2) > 1.7) {
                    retained = false;
                    break;
                }
            }
            if (retained) {
                retainedPaths.add(path1);
            }
        }

        return retainedPaths;
    }

    private double[] calcAscendDescend(final PointList pointList) {
        double ascendMeters = 0;
        double descendMeters = 0;
        double lastEle = pointList.getEle(0);
        for (int i = 1; i < pointList.size(); ++i) {
            double ele = pointList.getEle(i);
            double diff = Math.abs(ele - lastEle);

            if (ele > lastEle)
                ascendMeters += diff;
            else
                descendMeters += diff;

            lastEle = ele;

        }

        return new double[]{ascendMeters, descendMeters};
    }

    private double calculateElevationScore(Path path) {
        PointList points = new PointList();
        final double[] elevations = new double[]{Double.MIN_VALUE, Double.MIN_VALUE, 0};
        //store elevation of the origin, elevation of the highest points and accumulated ascend
        path.forEveryEdge(new Path.EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState eb, int index, int prevEdgeId) {
                PointList pl = eb.fetchWayGeometry(FetchMode.PILLAR_AND_ADJ);
                if (index == 0) {
                    elevations[0] = pl.getEle(0);
                    elevations[1] = pl.getEle(0);
                }
                for (int j = 0; j < pl.getSize(); j++) {
                    points.add(pl, j);
                    if (pl.getEle(j) > elevations[1]) {
                        elevations[1] = pl.getEle(j);
                    }
                }
                elevations[2] += calcAscendDescend(pl)[0];
            }

            @Override
            public void finish() {

            }
        });

        return 1 - (elevations[1] - elevations[0]) / elevations[2];


    }

    private double calculateSimilarity(final Path path1, final Path path2) {
        double[] sharedEdgesLength = new double[]{0};
        path1.forEveryEdge(new Path.EdgeVisitor() {
            @Override
            public void next(EdgeIteratorState edge1, int index, int prevEdgeId) {
                path2.forEveryEdge(new Path.EdgeVisitor() {
                    @Override
                    public void next(EdgeIteratorState edge2, int index, int prevEdgeId) {
                        if (edge2.getEdge() == edge1.getEdge()) {
                            sharedEdgesLength[0] += edge1.getDistance();
                        }
                    }

                    @Override
                    public void finish() {

                    }
                });
            }

            @Override
            public void finish() {

            }
        });
        return sharedEdgesLength[0] / path1.getDistance() + sharedEdgesLength[0] / path2.getDistance();

    }
}

