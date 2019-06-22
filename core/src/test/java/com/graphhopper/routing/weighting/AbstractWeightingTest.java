/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.TurnCostExtension;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.Before;
import org.junit.Test;

import static com.graphhopper.util.GHUtility.getEdge;
import static org.junit.Assert.*;

/**
 * @author Peter Karich
 * @author easbar
 */
public class AbstractWeightingTest {
    private Graph graph;
    private FlagEncoder encoder;
    private Weighting weighting;
    private TurnCostExtension turnCostExt;

    @Before
    public void setup() {
        encoder = new CarFlagEncoder(5, 5, 10);
        EncodingManager encodingManager = EncodingManager.create(encoder);
        graph = new GraphBuilder(encodingManager).create();
        weighting = new TestWeighting(encoder);
        turnCostExt = (TurnCostExtension) graph.getExtension();
    }

    @Test
    public void calcWeightAndTime_withTurnCosts() {
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        addTurnCost(0, 1, 2, 5);
        weighting.setTurnCostHandler(new JunctionWiseTurnCostHandler(turnCostExt, encoder));
        assertEquals(6 + 5, weighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, weighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_defaultUTurn() {
        // u-turns are forbidden by default
        EdgeIteratorState edge = graph.edge(0, 1, 100, true);
        JunctionWiseTurnCostHandler turnCostHandler = new JunctionWiseTurnCostHandler(turnCostExt, encoder);
        weighting.setTurnCostHandler(turnCostHandler);
        assertEquals(Double.POSITIVE_INFINITY, weighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals(Long.MAX_VALUE, weighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_setDefaultUTurn() {
        // if we set default costs for u-turns they get applied
        EdgeIteratorState edge = graph.edge(0, 1, 100, true);
        JunctionWiseTurnCostHandler turnCostHandler = new JunctionWiseTurnCostHandler(turnCostExt, encoder);
        turnCostHandler.setUTurnCost(40);
        weighting.setTurnCostHandler(turnCostHandler);
        assertEquals(6 + 40, weighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals((6 + 40) * 1000, weighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void calcWeightAndTime_withTurnCosts_shortest() {
        graph.edge(0, 1, 100, true);
        EdgeIteratorState edge = graph.edge(1, 2, 100, true);
        // turn costs are given in seconds
        addTurnCost(0, 1, 2, 5);
        Weighting weighting = new ShortestWeighting(encoder);
        weighting.setTurnCostHandler(new JunctionWiseTurnCostHandler(turnCostExt, encoder));
        // todo: for the shortest weighting turn costs cannot be interpreted as seconds ? at least when they are added
        // to the weight ? how much should they contribute ?
//        assertEquals(105, turnWeighting.calcWeight(edge, false, 0), 1.e-6);
        assertEquals(6000 + 5000, weighting.calcMillis(edge, false, 0), 1.e-6);
    }

    @Test
    public void testToString() {
        assertTrue(AbstractWeighting.isValidName("blup"));
        assertTrue(AbstractWeighting.isValidName("blup_a"));
        assertTrue(AbstractWeighting.isValidName("blup|a"));
        assertFalse(AbstractWeighting.isValidName("Blup"));
        assertFalse(AbstractWeighting.isValidName("Blup!"));
    }

    private void addTurnCost(int from, int via, int to, double turnCost) {
        long turnFlags = encoder.getTurnFlags(false, turnCost);
        turnCostExt.addTurnInfo(getEdge(graph, from, via).getEdge(), via, getEdge(graph, via, to).getEdge(), turnFlags);
    }

    private static class TestWeighting extends AbstractWeighting {
        TestWeighting(FlagEncoder encoder) {
            super(encoder);
        }

        @Override
        public double calcEdgeWeight(EdgeIteratorState edge, boolean reverse) {
            return edge.getDistance() * 0.06;
        }

        @Override
        public double getMinWeight(double distance) {
            return distance * 0.06;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
