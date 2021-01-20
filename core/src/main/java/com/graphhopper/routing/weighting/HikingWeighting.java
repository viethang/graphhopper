package com.graphhopper.routing.weighting;

import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.ev.TrackType;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

public class HikingWeighting implements Weighting {
    private EncodingManager encodingManager;
    private FlagEncoder flagEncoder;

    public HikingWeighting(Weighting weighting, EncodingManager encodingManager) {
        super();
        this.encodingManager = encodingManager;
        this.flagEncoder = weighting.getFlagEncoder();
    }
    @Override
    public double getMinWeight(double distance) {
        return 0;
    }

    @Override
    public double calcEdgeWeight(EdgeIteratorState edgeState, boolean reverse) {
        EnumEncodedValue roadClassEnv = encodingManager.getEnumEncodedValue(RoadClass.KEY, Enum.class);
        Enum roadClass = edgeState.get(roadClassEnv);
        EnumEncodedValue trackTypeEnv = encodingManager.getEnumEncodedValue(TrackType.KEY, Enum.class);
        Enum trackType = edgeState.get(trackTypeEnv);
        EnumEncodedValue roadEnviEnv = encodingManager.getEnumEncodedValue(RoadEnvironment.KEY, Enum.class);
        Enum roadEnv = edgeState.get(roadEnviEnv);

        int scoreCoeff = 1;
        if (roadEnv.equals(RoadEnvironment.FERRY)) {
            // should never use ferry
            scoreCoeff = Integer.MAX_VALUE;
        } else if (RoadClass.MOTORWAY.equals(roadClass) || RoadClass.TRUNK.equals(roadClass)) {
            scoreCoeff = Integer.MAX_VALUE;
        } else if (RoadClass.PRIMARY.equals(roadClass)) {
            scoreCoeff *= 2;
        } else if (RoadClass.SECONDARY.equals(roadClass) ||
                RoadClass.TERTIARY.equals(roadClass)) {
            scoreCoeff *= 1.5;
        } else if (RoadClass.FOOTWAY.equals(roadClass)) {
            scoreCoeff *= 0.5;
        } else if (RoadClass.PATH.equals(roadClass)) {
            scoreCoeff *= 0.5;
        } else if (RoadClass.PEDESTRIAN.equals(roadClass)) {
            scoreCoeff *= 0.5;
        } else if (!TrackType.MISSING.equals(trackType)) {
            scoreCoeff *= 0.5;
        } else {
            scoreCoeff = 1;
        }


        return scoreCoeff * edgeState.getDistance();
    }

    @Override
    public long calcEdgeMillis(EdgeIteratorState edgeState, boolean reverse) {
        return 0;
    }

    @Override
    public double calcTurnWeight(int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    @Override
    public long calcTurnMillis(int inEdge, int viaNode, int outEdge) {
        return 0;
    }

    @Override
    public boolean hasTurnCosts() {
        return false;
    }

    @Override
    public FlagEncoder getFlagEncoder() {
        return this.flagEncoder;
    }

    @Override
    public String getName() {
        return null;
    }
}
