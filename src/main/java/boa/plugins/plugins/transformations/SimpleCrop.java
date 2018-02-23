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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import java.util.ArrayList;
import boa.plugins.Cropper;
import boa.plugins.Transformation;

/**
 *
 * @author jollion
 */
public class SimpleCrop implements Transformation {
    NumberParameter xMin = new NumberParameter("X-Min", 0, 0);
    NumberParameter yMin = new NumberParameter("Y-Min", 0, 0);
    NumberParameter zMin = new NumberParameter("Z-Min", 0, 0);
    NumberParameter xLength = new NumberParameter("X-Length", 0, 0);
    NumberParameter yLength = new NumberParameter("Y-Length", 0, 0);
    NumberParameter zLength = new NumberParameter("Z-Length", 0, 0);
    Parameter[] parameters = new Parameter[]{xMin, xLength, yMin, yLength, zMin, zLength};
    MutableBoundingBox bounds;
    int[] configurationData;
    public SimpleCrop(){}
    public SimpleCrop(int x, int xL, int y, int yL, int z, int zL){
        xMin.setValue(x);
        xLength.setValue(xL);
        yMin.setValue(y);
        yLength.setValue(yL);
        zMin.setValue(z);
        zLength.setValue(zL);
    }
    public SimpleCrop(int... bounds){
        if (bounds.length>0) xMin.setValue(bounds[0]);
        if (bounds.length>1) xLength.setValue(bounds[1]);
        if (bounds.length>2) yMin.setValue(bounds[2]);
        if (bounds.length>3) yLength.setValue(bounds[3]);
        if (bounds.length>4) zMin.setValue(bounds[4]);
        if (bounds.length>5) zLength.setValue(bounds[5]);
    }
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image input = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        if (xLength.getValue().intValue()==0) xLength.setValue(input.sizeX()-xMin.getValue().intValue());
        if (yLength.getValue().intValue()==0) yLength.setValue(input.sizeY()-yMin.getValue().intValue());
        if (zLength.getValue().intValue()==0) zLength.setValue(input.sizeZ()-zMin.getValue().intValue());
        bounds = new MutableBoundingBox(xMin.getValue().intValue(), xMin.getValue().intValue()+xLength.getValue().intValue()-1, 
        yMin.getValue().intValue(), yMin.getValue().intValue()+yLength.getValue().intValue()-1, 
        zMin.getValue().intValue(), zMin.getValue().intValue()+zLength.getValue().intValue()-1);
        bounds.trim(input.getBoundingBox());
        configurationData = new int[6];
        configurationData[0]=bounds.xMin();
        configurationData[1]=bounds.xMax();
        configurationData[2]=bounds.yMin();
        configurationData[3]=bounds.yMax();
        configurationData[4]=bounds.zMin();
        configurationData[5]=bounds.zMax();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (bounds==null) bounds= new MutableBoundingBox(configurationData[0], configurationData[1], configurationData[2], configurationData[3], configurationData[4], configurationData[5]);
        return image.crop(bounds);
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
    public ArrayList getConfigurationData() {
        return null;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
