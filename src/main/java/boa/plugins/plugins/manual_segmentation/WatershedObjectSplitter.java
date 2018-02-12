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
package boa.plugins.plugins.manual_segmentation;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import boa.plugins.ObjectSplitter;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.WatershedTransform;

/**
 *
 * @author jollion
 */
public class WatershedObjectSplitter implements ObjectSplitter {
    //BoundedNumberParameter numberOfObjects = new BoundedNumberParameter("Maximum growth rate", 2, 1.5, 1, 2);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth Scale (0=no smooth)", 1, 2, 0, null);
    BooleanParameter keepOnlyTwoSeeds = new BooleanParameter("Use only two best seeds", false);
    Parameter[] parameters = new Parameter[]{smoothScale, keepOnlyTwoSeeds};
    boolean splitVerbose;
    public void setSplitVerboseMode(boolean verbose) {
        this.splitVerbose=verbose;
    }
    
    public RegionPopulation splitObject(Image input, Region object) {
        double sScale = smoothScale.getValue().doubleValue();
        if (sScale>0) input = ImageFeatures.gaussianSmooth(input, sScale, false);
        return splitInTwo(input, object.getMask(), true, keepOnlyTwoSeeds.getSelected(), splitVerbose);
    }
    
    public static RegionPopulation splitInTwo(Image watershedMap, ImageMask mask, final boolean decreasingPropagation, boolean keepOnlyTwoSeeds, boolean verbose) {
        
        ImageByte localMax = Filters.localExtrema(watershedMap, null, decreasingPropagation, mask, Filters.getNeighborhood(1, 1, watershedMap)).setName("Split seeds");
        List<Region> seeds = Arrays.asList(ImageLabeller.labelImage(localMax));
        if (seeds.size()<2) {
            //logger.warn("Object splitter : less than 2 seeds found");
            //new IJImageDisplayer().showImage(smoothed.setName("smoothed"));
            //new IJImageDisplayer().showImage(localMax.setName("localMax"));
            return null;
        } else {
            if ((keepOnlyTwoSeeds && seeds.size()>2) || seeds.size()>4) { // keep half of the seeds... TODO find other algorithm to maximize distance? // si contrainte de taille, supprimer les seeds qui génère des objets trop petits
                for (Region o : seeds) {
                    for (Voxel v : o.getVoxels()) v.value = watershedMap.getPixel(v.x, v.y, v.z);
                }
                Comparator<Region> c = new Comparator<Region>() {
                    public int compare(Region o1, Region o2) {
                        return Double.compare(o1.getMeanVoxelValue(), o2.getMeanVoxelValue());
                    }
                };
                Collections.sort(seeds, c);
                
                if (keepOnlyTwoSeeds) {
                    if (decreasingPropagation) seeds = seeds.subList(seeds.size()-2, seeds.size());
                    else seeds = seeds.subList(0, 2);
                } else {
                    if (decreasingPropagation) seeds = seeds.subList(seeds.size()/2, seeds.size());
                    else seeds = seeds.subList(0, seeds.size()/2);
                }
            }
            RegionPopulation pop =  WatershedTransform.watershed(watershedMap, mask, seeds, decreasingPropagation, null, new WatershedTransform.NumberFusionCriterion(2), false);
            if (verbose) {
                new IJImageDisplayer().showImage(localMax);
                new IJImageDisplayer().showImage(watershedMap.setName("watershedMap"));
                new IJImageDisplayer().showImage(pop.getLabelMap());
            }
            return pop;
        }
    }
    
    public static RegionPopulation splitInTwo(Image watershedMap, ImageMask mask, final boolean decreasingPropagation, int minSize, boolean verbose) {
        
        ImageByte localMax = Filters.localExtrema(watershedMap, null, decreasingPropagation, mask, Filters.getNeighborhood(1, 1, watershedMap)).setName("Split seeds");
        List<Region> seeds = ImageLabeller.labelImageList(localMax);
        if (seeds.size()<2) {
            //logger.warn("Object splitter : less than 2 seeds found");
            //new IJImageDisplayer().showImage(smoothed.setName("smoothed"));
            //new IJImageDisplayer().showImage(localMax.setName("localMax"));
            return null;
        } else {
            RegionPopulation pop =  WatershedTransform.watershed(watershedMap, mask, WatershedTransform.duplicateSeeds(seeds), decreasingPropagation, null, new WatershedTransform.NumberFusionCriterion(2), false);
            List<Region> remove = new ArrayList<Region>();
            pop.filter(new RegionPopulation.Size().setMin(minSize), remove);
            if (verbose) logger.debug("seeds: {}, objects: {}, removed: {}", seeds.size(), pop.getRegions().size()+remove.size(), remove.size());
            while (!remove.isEmpty() && seeds.size()>=2) {
                remove.clear();
                boolean oneSeedRemoved = false;
                Iterator<Region> it = seeds.iterator();
                while (it.hasNext()) {
                    if (hasVoxelsOutsideMask(it.next(), pop.getLabelMap())) {
                        it.remove();
                        oneSeedRemoved=true;
                    }
                }
                if (!oneSeedRemoved) {
                    logger.error("Split spot error: no seed removed");
                    break;
                }
                pop =  WatershedTransform.watershed(watershedMap, mask, WatershedTransform.duplicateSeeds(seeds), decreasingPropagation, null, new WatershedTransform.NumberFusionCriterion(2), false);
                pop.filter(new RegionPopulation.Size().setMin(minSize), remove);
                if (verbose) logger.debug("seeds: {}, objects: {}, removed: {}", seeds.size(), pop.getRegions().size()+remove.size(), remove.size());
            }
            if (pop.getRegions().size()>2) pop.mergeWithConnected(pop.getRegions().subList(2, pop.getRegions().size())); // split only in 2
            
            if (verbose) {
                new IJImageDisplayer().showImage(localMax);
                new IJImageDisplayer().showImage(watershedMap.setName("watershedMap"));
                new IJImageDisplayer().showImage(pop.getLabelMap());
            }
            return pop;
        }
    }
    
    private static boolean hasVoxelsOutsideMask(Region o, ImageMask mask) {
        for (Voxel v : o.getVoxels()) {
            if (!mask.insideMask(v.x, v.y, v.z)) return true;
        }
        return false;
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
