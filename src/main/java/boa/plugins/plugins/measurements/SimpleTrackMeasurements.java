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
package boa.plugins.plugins.measurements;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SimpleTrackMeasurements implements Measurement {
    protected StructureParameter structure = new StructureParameter("Structure", -1, false, false);
    protected Parameter[] parameters = new Parameter[]{structure};
    
    
    public SimpleTrackMeasurements(){}
    
    public SimpleTrackMeasurements(int structure){
        this.structure.setSelectedIndex(structure);
    }
    
    @Override public int getCallStructure() {
        return structure.getSelectedStructureIdx();
    }

    @Override public boolean callOnlyOnTrackHeads() {
        return true;
    }

    @Override public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = structure.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject("TrackHeadIndices", structureIdx));
        res.add(new MeasurementKeyObject("TrackLength", structureIdx));
        res.add(new MeasurementKeyObject("TrackObjectCount", structureIdx));

        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        String th = StructureObjectUtils.getIndices(object.getTrackHead());
        List<StructureObject> track = StructureObjectUtils.getTrack(object, false);
        int tl = track.get(track.size()-1).getFrame() - object.getFrame()+1;
        for (StructureObject o : track) {
            o.getMeasurements().setValue("TrackLength", tl);
            o.getMeasurements().setValue("TrackObjectCount", track.size());
            o.getMeasurements().setValue("TrackHeadIndices", th);
        }
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
        
}