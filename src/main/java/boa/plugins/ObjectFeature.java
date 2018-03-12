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
package boa.plugins;

import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;

/**
 *
 * @author jollion
 */
public interface ObjectFeature extends Plugin {
    @Override public Parameter[] getParameters();
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation);
    /**
     * Performs a scalar measurement on a region
     * Region's landmark will be used, so if it is in relative landmark, the images used should be of the same dimension as the parent mask 
     * @param region
     * @return performed measurement. 
     */
    public double performMeasurement(Region region);
    public String getDefaultName();
}
