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
package boa.plugins.plugins.pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ScaleXYZParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import java.util.ArrayList;
import boa.plugins.Filter;
import boa.plugins.PreFilter;
import boa.plugins.TransformationTimeIndependent;
import boa.image.processing.Filters;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;

/**
 *
 * @author jollion
 */
public class Median implements PreFilter, Filter {
    ScaleXYZParameter radius = new ScaleXYZParameter("Radius", 2, 1, true).setToolTipText("Radius in pixel");
    Parameter[] parameters = new Parameter[]{radius};
    public Median() {}
    public Median(double radius) {
        this.radius.setScaleXY(radius);
        this.radius.setUseImageCalibration(true);
    }
    public Median(double radiusXY, double radiusZ) {
        this.radius.setScaleXY(radiusXY);
        this.radius.setScaleZ(radiusZ);
    }
    @Override
    public Image runPreFilter(Image input, ImageMask mask) {
        return filter(input, radius.getScaleXY(), radius.getScaleZ(input.getScaleXY(), input.getScaleZ()));
    }
    
    public static Image filter(Image input, double radiusXY, double radiusZ) {
        return Filters.median(input, null, Filters.getNeighborhood(radiusXY, radiusZ, input));
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) { }
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }
    @Override 
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return runPreFilter(image, null);
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}