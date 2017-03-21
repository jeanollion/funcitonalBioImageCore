/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.ObjectPopulation.MeanIntensity;
import dataStructure.objects.ObjectPopulation.Or;
import dataStructure.objects.ObjectPopulation.Overlap;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import image.ImageOperations;
import image.ImageShort;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jj2000.j2k.util.ArrayUtil;
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.Segmenter;
import plugins.Thresholder;
import plugins.UseMaps;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.thresholders.BackgroundFit;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.KappaSigma;
import plugins.plugins.thresholders.ObjectCountThresholder;
import processing.Filters;
import processing.gaussianFit.GaussianFit;
import processing.IJFFTBandPass;
import processing.ImageFeatures;
import processing.LoG;
import processing.SubPixelLocalizator;
import processing.WatershedTransform;
import processing.WatershedTransform.MonotonalPropagation;
import processing.WatershedTransform.MultiplePropagationCriteria;
import processing.WatershedTransform.SizeFusionCriterion;
import processing.WatershedTransform.ThresholdPropagation;
import processing.WatershedTransform.ThresholdPropagationOnWatershedMap;
import static processing.WatershedTransform.watershed;
import processing.neighborhood.EllipsoidalSubVoxNeighborhood;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class MutationSegmenter implements Segmenter, UseMaps, ManualSegmenter, ObjectSplitter {
    public List<Image> intermediateImages;
    public static boolean debug = false;
    public static boolean displayImages = false;
    NumberParameter scale = new BoundedNumberParameter("Scale", 1, 2.5, 1.5, 5);
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size (Voxels)", 0, 5, 1, null);
    NumberParameter thresholdHigh = new NumberParameter("Threshold for Seeds", 2, 0.6);
    //PluginParameter<Thresholder> thresholdLow = new PluginParameter<Thresholder>("Threshold for propagation", Thresholder.class, new ObjectCountThresholder(20), false);
    NumberParameter thresholdLow = new NumberParameter("Threshold for propagation", 2, 0.5);
    NumberParameter intensityThreshold = new NumberParameter("Intensity Threshold for Seeds", 2, 0.35);
    Parameter[] parameters = new Parameter[]{scale, minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold};
    
    public MutationSegmenter() {}
    
    public MutationSegmenter(double thresholdSeeds, double thresholdPropagation, double thresholdIntensity) {
        this.intensityThreshold.setValue(thresholdIntensity);
        this.thresholdHigh.setValue(thresholdSeeds);
        this.thresholdLow.setValue(thresholdPropagation);
    }
    
    public MutationSegmenter setThresholdSeeds(double threshold) {
        this.thresholdHigh.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setThresholdPropagation(double threshold) {
        //this.thresholdLow.setPlugin(new ConstantValue(threshold));
        this.thresholdLow.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setIntensityThreshold(double threshold) {
        this.intensityThreshold.setValue(threshold);
        return this;
    }
    
    public MutationSegmenter setScale(double scale) {
        this.scale.setValue(scale);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent, scale.getValue().doubleValue(), minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), intensityThreshold.getValue().doubleValue(), intermediateImages);
    }
    
    public ObjectPopulation run(Image input, StructureObjectProcessing parent, double scale, int minSpotSize, double thresholdHigh , double thresholdLow, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) {
            // tester sur average, max, ou plan par plan
            ArrayList<Image> planes = input.splitZPlanes();
            ArrayList<ObjectPopulation> populations = new ArrayList<ObjectPopulation>(planes.size());
            for (Image plane : planes) {
                ObjectPopulation obj = runPlane(plane, parent, scale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
                //if (true) return obj;
                if (obj!=null && !obj.getObjects().isEmpty()) populations.add(obj);
            }
            if (populations.isEmpty()) return new ObjectPopulation(new ArrayList<Object3D>(0), planes.get(0));
            // combine: 
            ObjectPopulation pop = populations.remove(populations.size()-1);
            pop.combine(populations);
            return pop;
        } else return runPlane(input, parent, scale, minSpotSize, thresholdHigh, thresholdLow, intensityThreshold, intermediateImages);
    }
    
    public ObjectPopulation runPlane(Image input, StructureObjectProcessing parent, double scale, int minSpotSize, double thresholdSeeds, double thresholdPropagation, double intensityThreshold, List<Image> intermediateImages) {
        if (input.getSizeZ()>1) throw new Error("MutationSegmenter: should be run on a 2D image");
        Image sub = input.duplicate();
        
        //ObjectCountThresholder.debug=true;
        //final double thld = new ObjectCountThresholder(20).runThresholder(sub, parent);
        //BackgroundFit.debug=debug;
        final double thld = BackgroundFit.backgroundFitHalf(sub, parent.getMask(), 2, null);
        //final double thld= Double.POSITIVE_INFINITY;
        
        double[] ms = ImageOperations.getMeanAndSigmaWithOffset(sub, parent.getMask(), v->v<=thld);
        if (ms[2]==0) ms = ImageOperations.getMeanAndSigmaWithOffset(sub, parent.getMask(), v -> true);
        if (debug) logger.debug("thld: {} mean & sigma: {}", thld, ms);
        ImageOperations.affineOperation2WithOffset(sub, sub, 1/ms[1], -ms[0]);
        
        // TODO: test is Use Scale is taken into acount.
        //Image smooth = ImageFeatures.gaussianSmooth(sub, scale, scale, false);
        //Image lap = ImageFeatures.getLaplacian(sub, scale, true, false).setName("laplacian: "+scale);
        
        Image smooth = getSmoothedMap(input);
        this.smooth=null; // avoid several scaling
        ImageOperations.affineOperation2WithOffset(smooth, smooth, 1/ms[1], -ms[0]);
        Image lap = getLaplacianMap(input);
        this.lap=null; // avoid several scaling
        ImageOperations.affineOperation2WithOffset(lap, lap, 1/ms[1], 0); // no additive coefficient
        
        
        if (intermediateImages!=null) {
            //intermediateImages.add((ImageInteger)parent.getMask());
            intermediateImages.add(smooth.setName("smooth"));
            intermediateImages.add(lap.setName("lap"));
            intermediateImages.add(sub.setName("sub"));
            //intermediateImages.add(lap);
        }
        ImageByte seeds = Filters.localExtrema(lap, null, true, thresholdSeeds, Filters.getNeighborhood(scale, scale, input));
        for (int z = 0; z<seeds.getSizeZ(); ++z) {
            for (int xy = 0; xy<seeds.getSizeXY(); ++xy) {
                if (seeds.insideMask(xy, z) && smooth.getPixel(xy, 0)<intensityThreshold) seeds.setPixel(xy, z, 0);
            }
        }
        ObjectPopulation seedPop = new ObjectPopulation(seeds, false);
        //seedPop.filter(new Overlap(seedsHess, 1.5));
        //seedPop.filter(new Or(new ObjectPopulation.GaussianFit(norm, 3, 3, 5, 0.2, 0.010, 6), new MeanIntensity(-0.2, false, hess)));
        ObjectPopulation pop =  watershed(lap, parent.getMask(), seedPop.getObjects(), true, new ThresholdPropagationOnWatershedMap(thresholdPropagation), new SizeFusionCriterion(minSpotSize), false);
        //for (Object3D o : pop.getObjects()) o.setQuality(Math.sqrt(o.getQuality() * BasicMeasurements.getMaxValue(o, smooth, false))); // multiply by max of smooth to get quality criterion
        
        
        SubPixelLocalizator.setSubPixelCenter(smooth, pop.getObjects(), true);
        for (Object3D o : pop.getObjects()) { // quality criterion : smooth * lap
            o.setQuality(Math.sqrt(o.getQuality() * lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)));
        }
        //logger.debug("quality: {}", Utils.toStringList(pop.getObjects(), o->""+o.getQuality()));
        pop.filter(new ObjectPopulation.RemoveFlatObjects(input));
        pop.filter(new ObjectPopulation.Size().setMin(minSpotSize));
        return pop;
    }
    
    public void printSubLoc(String name, Image locMap, Image smooth, Image lap, ObjectPopulation pop, BoundingBox globBound) {
        BoundingBox b = locMap.getBoundingBox().translate(globBound.reverseOffset());
        List<Object3D> objects = pop.getObjects();
        
        for(Object3D o : objects) o.setCenter(o.getMassCenter(locMap, false));
        pop.translate(b, false);
        logger.debug("mass center: centers: {}", Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        for(Object3D o : objects) o.setCenter(o.getGeomCenter(false));
        pop.translate(b, false);
        logger.debug("geom center {}: centers: {}", name, Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        
        SubPixelLocalizator.setSubPixelCenter(locMap, objects, true);
        pop.translate(b, false);
        logger.debug("locMap: {}, centers: {}", name, Utils.toStringList(objects, o -> Utils.toStringArray(o.getCenter())+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        logger.debug("smooth values: {}", Utils.toStringList(objects, o->""+smooth.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)) );
        logger.debug("lap values: {}", Utils.toStringList(objects, o->""+lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)) );
        
        
        
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    @Override
    public Image[] computeMaps(Image rawSource, Image filteredSource) {
        double scale = this.scale.getValue().doubleValue();
        Image smooth = ImageFeatures.gaussianSmooth(filteredSource, scale, scale, false).setName("gaussian: "+scale);
        Image lap = ImageFeatures.getLaplacian(filteredSource, scale, true, false).setName("laplacian: "+scale);
        return new Image[]{smooth, lap};
    }
    Image smooth, lap;
    @Override
    public void setMaps(Image[] maps) {
        if (maps==null) return;
        if (maps.length!=2) throw new IllegalArgumentException("Maps should be of length 2 and contain smooth & laplacian of gaussian");
        this.smooth=maps[0];
        this.lap=maps[1];
    }
    protected Image getSmoothedMap(Image source) {
        if (smooth==null) {
            double scale = this.scale.getValue().doubleValue();
            smooth = ImageFeatures.gaussianSmooth(source, scale, scale, false).setName("gaussian: "+scale);
        }
        return smooth;
    }
    protected Image getLaplacianMap(Image source) {
        if (lap==null) {
            double scale = this.scale.getValue().doubleValue();
            lap = ImageFeatures.getLaplacian(source, scale, true, false).setName("laplacian: "+scale);
        }
        return lap;
    }

    protected boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    @Override
    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        List<Object3D> seedObjects = ObjectFactory.createSeedObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        final double thld = BackgroundFit.backgroundFitHalf(input, parent.getMask(), 2, null);
        double[] ms = ImageOperations.getMeanAndSigmaWithOffset(input, parent.getMask(), v->v<=thld);
        if (ms[2]==0) ms = ImageOperations.getMeanAndSigmaWithOffset(input, parent.getMask(), v -> true);
        if (verboseManualSeg) logger.debug("thld: {} mean & sigma: {}", thld, ms);
        Image scaledInput = ImageOperations.affineOperation2WithOffset(input, null, 1/ms[1], -ms[0]);
        Image lap = ImageFeatures.getLaplacian(scaledInput, scale.getValue().doubleValue(), true, false).setName("laplacian: "+scale);
        Image smooth = ImageFeatures.gaussianSmooth(scaledInput, scale.getValue().doubleValue(), scale.getValue().doubleValue(), false).setName("gaussian: "+scale.getValue().doubleValue());
        ObjectPopulation pop =  watershed(lap, parent.getMask(), seedObjects, true, new ThresholdPropagationOnWatershedMap(this.thresholdLow.getValue().doubleValue()), new SizeFusionCriterion(minSpotSize.getValue().intValue()), false);
        SubPixelLocalizator.setSubPixelCenter(smooth, pop.getObjects(), true);
        for (Object3D o : pop.getObjects()) { // quality criterion : smooth * lap
            o.setQuality(Math.sqrt(o.getQuality() * lap.getPixel(o.getCenter()[0], o.getCenter()[1], o.getCenter().length>2?o.getCenter()[2]:0)));
        }
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.showImage(seedMap);
            ImageWindowManagerFactory.showImage(lap.setName("Laplacian (watershedMap). Scale: "+scale.getValue().doubleValue()));
            ImageWindowManagerFactory.showImage(smooth.setName("Smmothed Scale: "+scale.getValue().doubleValue()));
            ImageWindowManagerFactory.showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        return pop;
    }

    @Override
    public ObjectPopulation splitObject(Image input, Object3D object) {
        ImageFloat wsMap = ImageFeatures.getLaplacian(input, 1.5, false, false);
        return WatershedObjectSplitter.splitInTwo(wsMap, object.getMask(), true, true, manualSplitVerbose);
    }

    boolean manualSplitVerbose;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        manualSplitVerbose=verbose;
    }
}
