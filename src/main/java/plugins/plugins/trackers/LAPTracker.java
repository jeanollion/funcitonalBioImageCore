/*
 * Copyright (C) 2016 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package plugins.plugins.trackers;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.GroupParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.StructureObjectUtils;
import fiji.plugin.trackmate.Spot;
import image.Image;
import image.ImageFloat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.apache.commons.lang.ArrayUtils;
import org.jgrapht.graph.DefaultWeightedEdge;
import plugins.MultiThreaded;
import plugins.ParameterSetup;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.Tracker;
import plugins.TrackerSegmenter;
import plugins.plugins.processingScheme.SegmentThenTrack;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.MutationSegmenterScaleSpace;
import plugins.plugins.trackers.trackMate.DistanceComputationParameters;
import plugins.plugins.trackers.trackMate.LAPTrackerCore;
import plugins.plugins.trackers.trackMate.SpotCompartiment;
import plugins.plugins.trackers.trackMate.postProcessing.MutationTrackPostProcessing;
import plugins.plugins.trackers.trackMate.SpotPopulation;
import plugins.plugins.trackers.trackMate.SpotWithinCompartment;
import plugins.plugins.trackers.trackMate.TrackMateInterface;
import plugins.plugins.trackers.trackMate.TrackMateInterface.SpotFactory;
import utils.ArrayFileWriter;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.SymetricalPair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class LAPTracker implements TrackerSegmenter, MultiThreaded {
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, new MutationSegmenterScaleSpace(), false);
    StructureParameter compartirmentStructure = new StructureParameter("Compartiment Structure", 1, false, false);
    NumberParameter spotQualityThreshold = new NumberParameter("Spot Quality Threshold", 3, 3.5);
    //NumberParameter spotQualityThreshold = new BoundedNumberParameter("Spot Quality Threshold", 3, 4, 0, null);
    NumberParameter maxGap = new BoundedNumberParameter("Maximum frame gap", 0, 2, 0, null);
    NumberParameter maxLinkingDistance = new BoundedNumberParameter("FTF Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter maxLinkingDistanceGC = new BoundedNumberParameter("Gap-closing Maximum Linking Distance (0=skip)", 2, 0.75, 0, null);
    NumberParameter gapPenalty = new BoundedNumberParameter("Gap Distance Penalty", 2, 0.25, 0, null);
    NumberParameter alternativeDistance = new BoundedNumberParameter("Alternative Distance (>maxLinkingDistance)", 2, 0.8, 0, null);
    //NumberParameter minimalDistanceForTrackSplittingPenalty = new BoundedNumberParameter("Minimal Distance For Track Splitting Penalty", 2, 0.2, 0, null);
    //NumberParameter maximalTrackSplittingPenalty = new BoundedNumberParameter("Maximal Track Splitting Penalty", 5, 0.00001, 0.01, 1);
    NumberParameter minimalTrackFrameNumber = new BoundedNumberParameter("Minimal Track Frame Number", 0, 6, 0, null);
    NumberParameter maximalTrackFrameNumber = new BoundedNumberParameter("Maximal Track Frame Number", 0, 15, 0, null);
    GroupParameter trackSplittingParameters = new GroupParameter("Track Post-Processing", minimalTrackFrameNumber, maximalTrackFrameNumber);
    Parameter[] parameters = new Parameter[]{segmenter, compartirmentStructure, maxLinkingDistance, maxLinkingDistanceGC, maxGap, gapPenalty, alternativeDistance, spotQualityThreshold, trackSplittingParameters};
    
    ExecutorService executor;
    @Override
    public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
    public LAPTracker setCompartimentStructure(int compartimentStructureIdx) {
        this.compartirmentStructure.setSelectedStructureIdx(compartimentStructureIdx);
        return this;
    }
    public LAPTracker setLinkingMaxDistance(double maxDist, double alternativeLinking) {
        maxLinkingDistance.setValue(maxDist);
        alternativeDistance.setValue(alternativeLinking);
        return this;
    }
    public LAPTracker setGapParameters(double maxDistGapClosing, double gapPenalty, int maxFrameGap) {
        this.maxLinkingDistanceGC.setValue(maxDistGapClosing);
        this.gapPenalty.setValue(gapPenalty);
        this.maxGap.setValue(maxFrameGap);
        return this;
    }
    public LAPTracker setSpotQualityThreshold(double threshold) {
        this.spotQualityThreshold.setValue(threshold);
        return this;
    }
    public LAPTracker setSegmenter(Segmenter s) {
        
        segmenter.setPlugin(s);
        return this;
    }
    public LAPTracker setTrackLength(double min, double max) {
        this.minimalTrackFrameNumber.setValue(min);
        this.maximalTrackFrameNumber.setValue(max);
        return this;
    }
    @Override public void segmentAndTrack(int structureIdx, List<StructureObject> parentTrack, PreFilterSequence preFilters, PostFilterSequence postFilters) {
        SegmentThenTrack stt = new SegmentThenTrack(segmenter.instanciatePlugin(), this).setPreFilters(preFilters).setPostFilters(postFilters);
        stt.segmentOnly(structureIdx, parentTrack, executor);
        track(structureIdx, parentTrack, true);
    }

    @Override public Segmenter getSegmenter() {
        return segmenter.instanciatePlugin();
    }
    @Override public void track(int structureIdx, List<StructureObject> parentTrack) {
        track(structureIdx, parentTrack, false);
    }
    
    public void track(int structureIdx, List<StructureObject> parentTrack, boolean LQSpots) {
        int compartirmentStructure=this.compartirmentStructure.getSelectedIndex();
        int maxGap = this.maxGap.getValue().intValue()+1; // parameter = count only the frames where the spot is missing
        double spotQualityThreshold = LQSpots ? this.spotQualityThreshold.getValue().doubleValue() : Double.NEGATIVE_INFINITY;
        double maxLinkingDistance = this.maxLinkingDistance.getValue().doubleValue();
        double maxLinkingDistanceGC = this.maxLinkingDistanceGC.getValue().doubleValue();
        double gapPenalty = this.gapPenalty.getValue().doubleValue();
        double alternativeDistance = this.alternativeDistance.getValue().doubleValue();
        DistanceComputationParameters distParams = new DistanceComputationParameters().setQualityThreshold(spotQualityThreshold).setGapDistancePenalty(gapPenalty).setAlternativeDistance(alternativeDistance).setAllowGCBetweenLQ(true);
        
        //SpotWithinCompartment s1 = (SpotWithinCompartment)spotCollection.getSpotCollection(true, false).iterator(1, false).next();
        //SpotWithinCompartment s2 = (SpotWithinCompartment)spotCollection.getSpotCollection(true, false).iterator(2, false).next();
        //if (s1!=null &&  s2!=null) logger.debug("distance 1-2: {}, scale 1: {}, 1 isAbsolute: {}, cent1: {}, cent2: {}", s1.squareDistanceTo(s2), s1.getObject().getScaleXY(), s1.getObject().isAbsoluteLandMark(), s1.getObject().getCenter(true), s2.getObject().getCenter(true));
        logger.debug("distanceFTF: {}, distance GC: {}, gapP: {}, atl: {}", maxLinkingDistance, maxLinkingDistanceGC, gapPenalty, alternativeDistance);
        
        final Map<Integer, StructureObject> parentsByF = StructureObjectUtils.splitByFrame(parentTrack);
        
        TrackMateInterface<SpotWithinCompartment> tmi = new TrackMateInterface<>(new SpotFactory<SpotWithinCompartment>() {
            HashMapGetCreate<StructureObject, SpotCompartiment> compartimentMap = new HashMapGetCreate<>((StructureObject s) -> new SpotCompartiment(s));
            @Override
            public SpotWithinCompartment toSpot(Object3D o, int frame) {
                StructureObject parent = parentsByF.get(frame);
                List<StructureObject> candidates = parent.getChildren(compartirmentStructure);
                StructureObject compartimentSO = StructureObjectUtils.getInclusionParent(o, candidates, null); 
                SpotCompartiment compartiment = compartimentMap.getAndCreateIfNecessary(compartimentSO);
                if (compartiment==null) return null;
                double[] center = o.getCenter();
                if (center==null) center = o.getGeomCenter(false);
                else center =center.clone(); // in order to avoid modifying original array
                center[0]*=o.getScaleXY();
                center[1]*=o.getScaleXY();
                if (center.length>2) center[2]*=o.getScaleZ();
                return new SpotWithinCompartment(o, frame, compartiment, center, distParams);
            }

            @Override
            public SpotWithinCompartment duplicate(SpotWithinCompartment s) {
                double[] center = new double[]{s.getFeature(Spot.POSITION_X), s.getFeature(Spot.POSITION_Y), s.getFeature(Spot.POSITION_Z)};
                return new SpotWithinCompartment(s.getObject(), s.frame, s.compartiment, center, distParams);
            }
        });
        
        Map<Integer, List<StructureObject>> objectsF = StructureObjectUtils.getChildrenMap(parentTrack, structureIdx);
        
        long t0 = System.currentTimeMillis();
        tmi.addObjects(objectsF);
        long t1 = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            int lQCount = 0;
            for (SpotWithinCompartment s : tmi.spotObjectMap.keySet()) if (s.lowQuality) ++lQCount;
            logger.debug("LAP Tracker: {}, spot HQ: {}, #spots LQ: {} (thld: {}), time: {}", parentTrack.get(0), tmi.spotObjectMap.size()-lQCount, lQCount, spotQualityThreshold, t1-t0);
        }
        
        if (LQSpots) { // run to remove LQ spots
            distParams.includeLQ=false;
            boolean ok = tmi.processFTF(maxLinkingDistance); //FTF only with HQ
            distParams.includeLQ=true;
            if (ok) ok = tmi.processFTF(maxLinkingDistance); // FTF HQ+LQ
            distParams.includeLQ=true;
            if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false); // GC HQ+LQ (dist param: no gap closing between LQ spots)
            if (ok) {
                tmi.setTrackLinks(objectsF);
                tmi.resetEdges();
                
                MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->{});
                postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
                removeUnlinkedLQSpots(parentTrack, structureIdx, tmi);
                
                objectsF = StructureObjectUtils.getChildrenMap(parentTrack, structureIdx);
            } else return;
            
        }
        long t2 = System.currentTimeMillis();
        boolean ok = true;//tmi.processFTF(maxLinkingDistance);
        if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false);
        if (ok) {
            switchCrossingLinksWithLQBranches(tmi, maxLinkingDistanceGC/Math.sqrt(2), maxLinkingDistanceGC, maxGap);
            trimLQExtremityWithGaps(tmi, 0, true, true);
        }
        if (ok) tmi.setTrackLinks(objectsF);
        
        
        //else for (StructureObject o : Utils.flattenMap(objectsF)) o.resetTrackLinks(true, true);
        // OR: second run with all spots at the same time?
        //boolean ok = tmi.processFTF(maxLinkingDistance);
        //if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false);
        //if (ok) tmi.removeCrossingLinksFromGraph(parentTrack.get(0).getScaleXY()*2);
        //if (ok) ok = tmi.processGC(maxLinkingDistanceGC, maxGap, false, false);
        //if (ok) tmi.setTrackLinks(objectsF);
        if (LQSpots) {
            tmi.resetEdges();
            removeUnlinkedLQSpots(parentTrack, structureIdx, tmi);
        }
        
        long t3 = System.currentTimeMillis();
        // post-processing
        //MutationTrackPostProcessing.RemoveObjectCallBact cb= o->tmi.removeObject(o.getObject(), o.getFrame()); // if tmi needs to be reused afterwards
        MutationTrackPostProcessing postProcessor = new MutationTrackPostProcessing(structureIdx, parentTrack, tmi.objectSpotMap, o->{});
        postProcessor.connectShortTracksByDeletingLQSpot(maxLinkingDistanceGC);
        long t4 = System.currentTimeMillis();
        //postProcessor.flagShortAndLongTracks(minimalTrackFrameNumber.getValue().intValue(), maximalTrackFrameNumber.getValue().intValue());
        
        // ETUDE DES DEPLACEMENTS EN Y
        /*
        // get distance distribution
        float[] dHQ= core.extractDistanceDistribution(true);
        float[] dHQLQ = core.extractDistanceDistribution(false);
        new ArrayFileWriter().addArray("HQ", dHQ).addArray("HQLQ", dHQLQ).writeToFile("/home/jollion/Documents/LJP/Analyse/SpotDistanceDistribution/SpotDistanceDistribution.csv");
        */
        
        // relabel
        for (StructureObject p: parentTrack) {
            Collections.sort(p.getChildren(structureIdx), (o1, o2) -> Double.compare(o1.getBounds().getYMean(), o2.getBounds().getYMean()));
            p.relabelChildren(structureIdx);
        }
        
        logger.debug("LAP Tracker: {}, total processing time: {}, create spots: {}, remove LQ: {}, link: {}", parentTrack.get(0), t4-t0, t1-t0, t2-t1, t3-t2, t4-t3);
    }
    private static void trimLQExtremityWithGaps(TrackMateInterface<SpotWithinCompartment> tmi, double gapTolerance, boolean start, boolean end) {
        long t0 = System.currentTimeMillis();
        gapTolerance+=1;
        Set<DefaultWeightedEdge> toRemove = new HashSet<>();
        for (DefaultWeightedEdge e : tmi.getEdges()) {
            SpotWithinCompartment s = tmi.getEdge(e, true);
            SpotWithinCompartment t = tmi.getEdge(e, false);
            if (s.frame+gapTolerance>=t.frame) continue; // no gap
            if (start && s.lowQuality) {
                if (tmi.getPrevious(s)==null) { // start of track -> remove edge
                    toRemove.add(e);
                    continue;
                } 
            }
            if (end && t.lowQuality) {
                if (tmi.getNext(t)==null) toRemove.add(e);
            }
        }
        tmi.removeFromGraph(toRemove, null);
        long t1 = System.currentTimeMillis();
        tmi.logGraphStatus("trim extremities ("+toRemove.size()+")", t1-t0);
    }
    private static void switchCrossingLinksWithLQBranches(TrackMateInterface<SpotWithinCompartment> tmi, double spatialTolerance, double distanceThld, int maxGap) {
        long t0 = System.currentTimeMillis();
        double distanceSqThld = distanceThld*distanceThld;
        Set<SymetricalPair<DefaultWeightedEdge>> crossingLinks = tmi.getCrossingLinks(spatialTolerance, null);
        HashMapGetCreate<DefaultWeightedEdge, List<SpotWithinCompartment>> trackBefore = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getEdge(e, true), true, false));
        HashMapGetCreate<DefaultWeightedEdge, List<SpotWithinCompartment>> trackAfter = new HashMapGetCreate<>(e -> tmi.getTrack(tmi.getEdge(e, false), false, true));
        Function<SymetricalPair<DefaultWeightedEdge>, Double> distance = p -> {
            boolean beforeLQ1 = isLowQ(trackBefore.getAndCreateIfNecessary(p.key));
            boolean afterLQ1 = isLowQ(trackAfter.getAndCreateIfNecessarySync(p.key));
            if (beforeLQ1!=afterLQ1) return Double.POSITIVE_INFINITY;
            boolean beforeLQ2 = isLowQ(trackBefore.getAndCreateIfNecessary(p.value));
            boolean afterLQ2 = isLowQ(trackAfter.getAndCreateIfNecessarySync(p.value));
            if (beforeLQ2!=afterLQ2 || beforeLQ1==beforeLQ2) return Double.POSITIVE_INFINITY;
            if (beforeLQ1) { // link before2 and after1
                return tmi.getEdge(p.value, true).squareDistanceTo(tmi.getEdge(p.key, false));
            } else { // link before1 and after2
                return tmi.getEdge(p.key, true).squareDistanceTo(tmi.getEdge(p.value, false));
            }
        };
        HashMapGetCreate<SymetricalPair<DefaultWeightedEdge>, Double> linkDistance = new HashMapGetCreate<>(p -> distance.apply(p));
        crossingLinks.removeIf(p -> linkDistance.getAndCreateIfNecessary(p)>distanceSqThld);
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> map = Pair.toMapSym(crossingLinks);
        Set<DefaultWeightedEdge> toDelete = new HashSet<>(); // to avoid concurent modification -> add there
        for (Entry<DefaultWeightedEdge, Set<DefaultWeightedEdge>> e : map.entrySet()) {
            if (toDelete.contains(e)) continue;
            DefaultWeightedEdge closestEdge = Collections.min(e.getValue(), (e1, e2)-> Double.compare(linkDistance.getAndCreateIfNecessary(new SymetricalPair<>(e.getKey(), e1)), linkDistance.getAndCreateIfNecessary(new SymetricalPair<>(e.getKey(), e2))));
            SpotWithinCompartment e1 = tmi.getEdge(e.getKey(), true);
            SpotWithinCompartment t1 = tmi.getEdge(e.getKey(), false);
            SpotWithinCompartment e2 = tmi.getEdge(closestEdge, true);
            SpotWithinCompartment t2 = tmi.getEdge(closestEdge, false);
            if (t2.frame>e1.frame && (t2.frame-e1.frame) <=maxGap && e1.squareDistanceTo(t2)<=distanceSqThld)  tmi.addEdge(e1, t2);
            if (t1.frame>e2.frame && (t1.frame-e2.frame) <=maxGap && e2.squareDistanceTo(t1)<=distanceSqThld)  tmi.addEdge(e2, t1);
            tmi.removeFromGraph(e.getKey());
            tmi.removeFromGraph(closestEdge);
        }
        long t1 = System.currentTimeMillis();
        tmi.logGraphStatus("switch LQ links", t1-t0);
    }
    
    private static boolean isLowQ(List<SpotWithinCompartment> track) {
        for (SpotWithinCompartment s : track) if (!s.lowQuality) return false;
        return true;
    }
    private static void removeUnlinkedLQSpots(List<StructureObject> parentTrack, int structureIdx, TrackMateInterface<SpotWithinCompartment> tmi) {
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        Set<StructureObject> parentsToRelabel = new HashSet<>();
        int eraseCount = 0;
        for (List<StructureObject> list : allTracks.values()) {
            boolean hQ = false;
            for (StructureObject o : list) {
                SpotWithinCompartment s = tmi.objectSpotMap.get(o.getObject());
                if (s!=null && !s.lowQuality) {
                    hQ = true;
                    break;
                }
            }
            if (!hQ) { // erase track
                for (StructureObject o : list) {
                    o.getParent().getChildren(structureIdx).remove(o);
                    tmi.removeObject(o.getObject(), o.getFrame());
                    parentsToRelabel.add(o.getParent());
                    eraseCount++;
                }
            }
        }
        for (StructureObject p : parentsToRelabel) p.relabelChildren(structureIdx);
        logger.debug("erased LQ spots: {}", eraseCount);
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    

    
    
}
