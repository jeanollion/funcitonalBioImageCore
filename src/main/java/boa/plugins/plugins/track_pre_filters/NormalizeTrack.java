/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.processing.ImageOperations;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import boa.plugins.TrackPreFilter;
import java.util.ArrayList;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public class NormalizeTrack  implements TrackPreFilter {
    NumberParameter saturation = new BoundedNumberParameter("Saturation", 3, 0.99, 0, 1);
    BooleanParameter invert = new BooleanParameter("Invert", false);
    public NormalizeTrack() {}
    public NormalizeTrack(double saturation, boolean invert) {
        this.saturation.setValue(saturation);
        this.invert.setSelected(invert);
    }
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImage) {
        Histogram histo = Histogram.getHisto256(preFilteredImages.values(), null);
        double[] minAndMax = new double[2];
        minAndMax[0] = histo.minAndMax[0];
        if (saturation.getValue().doubleValue()<1) minAndMax[1] = histo.getQuantiles(saturation.getValue().doubleValue())[0];
        else minAndMax[1] = histo.minAndMax[1];
        double scale = 1 / (minAndMax[1] - minAndMax[0]);
        double offset = -minAndMax[0] * scale;
        if (invert.getSelected()) {
            scale = -scale;
            offset = 1 - offset;
        }
        logger.debug("normalization: range: [{}-{}] scale: {} off: {}", minAndMax[0], minAndMax[1], scale, offset);
        for (Entry<StructureObject, Image> e : preFilteredImages.entrySet()) {
            Image trans = ImageOperations.affineOperation(e.getValue(), canModifyImage?e.getValue():null, scale, offset);
            e.setValue(trans);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{saturation, invert};
    }
    
}
