/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements.objectFeatures;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.ObjectFeature;

/**
 *
 * @author jollion
 */
public class Thickness implements ObjectFeature {
    ChoiceParameter scaled = new ChoiceParameter("Scale", new String[]{"Pixel", "Unit"}, "Pixel", false);
    public Thickness setScale(boolean unit) {
        this.scaled.setSelectedIndex(unit?1:0);
        return this;
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }

    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region object) {
        double res = GeometricalMeasurements.localThickness(object);
        if (scaled.getSelectedIndex()==1) res*=object.getScaleXY();
        return res;
    }

    @Override
    public String getDefaultName() {
        return "Thickness";
    }
    
}
