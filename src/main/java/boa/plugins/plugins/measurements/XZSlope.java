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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.plugins.plugins.transformations.SelectBestFocusPlane;


/**
 *
 * @author jollion
 */
public class XZSlope implements Measurement {
    protected StructureParameter microchannel = new StructureParameter("Microchannel Structure", 0, false, false);
    @Override
    public int getCallStructure() {
        return -1;
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        return new ArrayList<MeasurementKey>(){{add(new MeasurementKeyObject("XZSlope", 0));add(new MeasurementKeyObject("FocusPlane", 0));}};
    }

    @Override
    public void performMeasurement(StructureObject object) {
        List<StructureObject> mcs = object.getChildren(0);
        if (mcs.size()<2) return;
        Collections.sort(mcs, (o1, o2)->Integer.compare(o1.getBounds().xMin(), o2.getBounds().xMin()));
        StructureObject oLeft = mcs.get(0);
        StructureObject oRight = mcs.get(mcs.size()-1);
        int left = SelectBestFocusPlane.getBestFocusPlane(oLeft.getRawImage(oLeft.getStructureIdx()).splitZPlanes(), 3, null, null);
        int right = SelectBestFocusPlane.getBestFocusPlane(oRight.getRawImage(oLeft.getStructureIdx()).splitZPlanes(), 3, null, null);
        double value = (right-left) * object.getScaleZ() / ((oRight.getBounds().xMean()-oLeft.getBounds().xMean()) * object.getScaleXY());
        logger.debug("focus plane left: {} right: {} value: {} (scale XY: {}, Z: {})", left, right, value, object.getScaleXY(), object.getScaleZ());
        oLeft.getMeasurements().setValue("XZSlope", value);
        oRight.getMeasurements().setValue("XZSlope", value);
        oLeft.getMeasurements().setValue("FocusPlane", left);
        oRight.getMeasurements().setValue("FocusPlane", right);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{microchannel};
    }
    
}
