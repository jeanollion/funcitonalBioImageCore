/*
 * Copyright (C) 2018 jollion
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
package boa.plugins.plugins.segmenters;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.measurement.BasicMeasurements;
import boa.plugins.TrackParametrizable;
import java.util.Collection;
import java.util.Set;
import java.util.TreeMap;


/**
 * Bacteria segmentation within microchannels, for phas images
 * @author jollion
 */
public class BacteriaIntensityPhase extends BacteriaIntensity implements TrackParametrizable {
    public BacteriaIntensityPhase() {
        this.splitThreshold.setValue(0.2);
        this.minSize.setValue(50);
    }
    @Override public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        if (isVoid) return null;
        return super.runSegmenter(input, structureIdx, parent);
    }
    final private String toolTip = "<html>Bacteria segmentation within microchannels, for phase images normalized and inverted (foreground is bright)</ br>"
            + "Same algorithm as BacteriaIntensity with minor changes:<br />"
            + "Split/Merge criterion is absolute value of hessian at interface between to regions<br />"
            + "An optional procedure to merge head of first cell that tend to be cut in some cell-lines</html>";
    
    boolean normalizeEdgeValues = true;
    @Override public String getToolTipText() {return toolTip;}
    @Override public SplitAndMergeHessian initializeSplitAndMerge(Image input) {
        SplitAndMergeHessian sam = super.initializeSplitAndMerge(input);
        sam.setInterfaceValue(i-> {
            Collection<Voxel> voxels = i.getVoxels();
            if (voxels.isEmpty()) return Double.NaN;
            else {
                double hessSum = 0;
                for (Voxel v : voxels) hessSum+=sam.getHessian().getPixel(v.x, v.y, v.z);
                double val = hessSum/voxels.size();
                if (normalizeEdgeValues) {// normalize by mean intensity within 2 regions
                    double sum = BasicMeasurements.getSum(i.getE1(), sam.getIntensityMap(), false)+BasicMeasurements.getSum(i.getE2(), sam.getIntensityMap(), false);
                    sum /= ((double)(i.getE1().getSize()+i.getE2().getSize()));
                    val/=sum;
                }
                
                return val;
            }
        });
        return sam;
    }
    
    boolean isVoid = false;
    @Override
    public ApplyToSegmenter run(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages) {
        Set<StructureObject> voidMC = TrackParametrizable.getVoidMicrochannels(structureIdx, preFilteredImages, 0.4);
        return (p, s) -> {if (voidMC.contains(p)) ((BacteriaIntensityPhase)s).isVoid=true;};
    }
}
