package com.graphhopper.routing;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.ShortestWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.GHUtility;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MultipleRouteTest {
    private final EncodingManager encodingManager;
    private final FlagEncoder encoder;
    private Weighting defaultWeighting;

    public MultipleRouteTest() {
        encodingManager = EncodingManager.create("car");
        encoder = encodingManager.getEncoder("car");
        defaultWeighting = new ShortestWeighting(encoder);
    }

    //    @Test
    public void test0() {
        GraphHopperStorage graph = createGHStorage();
        GHUtility.setSpeed(60, 60, encoder,
                graph.edge(0, 1).setDistance(1)
        );
        MultipleRoundTripsRouting searcher = new MultipleRoundTripsRouting(graph, defaultWeighting, TraversalMode.EDGE_BASED, 1, 10);
        List<Path> paths = searcher.calcPaths(0, 1);
        assertEquals(paths.size(), 1);
        assertEquals(paths.get(0).getEdges().size(), 1);
    }

    @Test
    public void test1() {
        //      0----1
        //     /     |
        //    7--    |
        //   /   |   |
        //   6---5   |
        //   |   |   |
        //   4---3---2
        GraphHopperStorage graph = createGHStorage();
        GHUtility.setSpeed(60, 60, encoder,
                graph.edge(0, 1).setDistance(1),//0
                graph.edge(1, 2).setDistance(1),//1
                graph.edge(3, 2).setDistance(1),//2
                graph.edge(3, 5).setDistance(1),//3
                graph.edge(5, 7).setDistance(1),//4
                graph.edge(3, 4).setDistance(1),//5
                graph.edge(4, 6).setDistance(1),//6
                graph.edge(6, 7).setDistance(1),//7
                graph.edge(6, 5).setDistance(1),//8
                graph.edge(0, 7).setDistance(1) //9
        );

        MultipleRoundTripsRouting searcher = new MultipleRoundTripsRouting(graph, defaultWeighting, TraversalMode.EDGE_BASED, 1, 10);
        searcher.calcPaths(0, 2);

    }

    private GraphHopperStorage createGHStorage() {
        return new GraphBuilder(encodingManager).create();
    }
}
