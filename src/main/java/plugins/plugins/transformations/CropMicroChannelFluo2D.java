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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.Object3D;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageOperations;
import static image.ImageOperations.threshold;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import plugins.TransformationTimeIndependent;
import plugins.plugins.thresholders.IJAutoThresholder;
import processing.Filters;
import processing.ImageFeatures;
import processing.RadonProjection;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.ArrayUtil;
import utils.HashMapGetCreate;
import utils.Utils;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannelFluo2D implements TransformationTimeIndependent {
    public static boolean debug = false;
    ArrayList<Integer> configurationData=new ArrayList<Integer>(4); // xMin/xMax/yMin/yMax
    NumberParameter xStart = new BoundedNumberParameter("X start", 0, 0, 0, null);
    NumberParameter xStop = new BoundedNumberParameter("X stop (0 for image width)", 0, 0, 0, null);
    NumberParameter yStart = new BoundedNumberParameter("Y start", 0, 0, 0, null);
    NumberParameter yStop = new BoundedNumberParameter("Y stop (0 for image heigth)", 0, 0, 0, null);
    NumberParameter margin = new BoundedNumberParameter("X-Margin", 0, 30, 0, null);
    NumberParameter channelHeight = new BoundedNumberParameter("Channel Height", 0, 355, 0, null);
    NumberParameter cropMargin = new BoundedNumberParameter("Crop Margin", 0, 45, 0, null);
    NumberParameter minObjectSize = new BoundedNumberParameter("Object Size Filter", 0, 200, 1, null);
    NumberParameter fillingProportion = new BoundedNumberParameter("Filling proportion of Microchannel", 2, 0.6, 0.05, 1);
    NumberParameter number = new BoundedNumberParameter("Number of TimePoints", 0, 5, 1, null);
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, minObjectSize, fillingProportion, xStart, xStop, yStart, yStop, number};
    
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    public CropMicroChannelFluo2D(int margin, int cropMargin, int minObjectSize, double fillingProportion, int timePointNumber) {
        this.margin.setValue(margin);
        this.cropMargin.setValue(cropMargin);
        this.minObjectSize.setValue(minObjectSize);
        this.fillingProportion.setValue(fillingProportion);
        this.number.setValue(timePointNumber);
    }
    
    public CropMicroChannelFluo2D() {
        
    }
    
    public CropMicroChannelFluo2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        int tp = inputImages.getDefaultTimePoint();
        Image image = inputImages.getImage(channelIdx, tp);
        // check configuration validity
        if (xStop.getValue().intValue()==0 || xStop.getValue().intValue()>=image.getSizeX()) xStop.setValue(image.getSizeX()-1);
        if (xStart.getValue().intValue()>=xStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: xStart>=xStop, set to default values");
            xStart.setValue(0);
            xStop.setValue(image.getSizeX()-1);
        }
        if (yStop.getValue().intValue()==0 || yStop.getValue().intValue()>=image.getSizeY()) yStop.setValue(image.getSizeY()-1);
        if (yStart.getValue().intValue()>=yStop.getValue().intValue()) {
            logger.warn("CropMicroChannels2D: illegal configuration: yStart>=yStop, set to default values");
            yStart.setValue(0);
            yStop.setValue(image.getSizeY()-1);
        }
        
        if (channelHeight.getValue().intValue()>image.getSizeY()) throw new IllegalArgumentException("channel height > image height");
        BoundingBox b=null;
        int numb = Math.min(number.getValue().intValue(), inputImages.getTimePointNumber()-2);
        if (numb>1) {
            double delta = (double)inputImages.getTimePointNumber() / (double)(numb+2);
            for (int i = 1; i<=numb; ++i) {
                int time = (int)(i * delta);
                image = inputImages.getImage(channelIdx, time);
                BoundingBox bb = getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
                if (b==null) b = bb;
                else b.expand(bb);
                if (debug) logger.debug("time: {}, bounds: {}, max bounds: {}", time, bb, b);
            }
        } else {
            b = getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
        }
        
        
        
        
        /*if (b==null) {
            int delta = Math.max(1, inputImages.getTimePointNumber() / 100);
            int n = 1;
            int sign = 1;
            boolean minReached = false, maxReached = false;
            while (b==null) {
                int newTp = tp + sign * n * delta;
                if (newTp<0) {
                    minReached = true;
                    if (maxReached) throw new Error("No microchannel found ");
                    continue;
                }
                if (newTp >=  inputImages.getTimePointNumber()) {
                    maxReached = true;
                    if (minReached) throw new Error("No microchannel found ");
                    continue;
                }
                image = inputImages.getImage(channelIdx, newTp);
                z = image.getSizeZ()/2;
                b = getBoundingBox(image, z, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), fillingProportion.getValue().doubleValue(), minObjectSize.getValue().intValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
                if (sign==-1) ++n;
                sign *=-1;
            }
        }*/

        logger.debug("Crop Microp Channel: image: {} timepoint: {} boundingBox: {}", image.getName(), inputImages.getDefaultTimePoint(), b);
        configurationData=new ArrayList<Integer>(4);
        configurationData.add(b.getxMin());
        configurationData.add(b.getxMax());
        configurationData.add(b.getyMin());
        configurationData.add(b.getyMax());
    }
    public static BoundingBox getBoundingBox(Image image, int cropMargin, int margin, int channelHeight, double fillingProportion, int minObjectSize, int xStart, int xStop, int yStart, int yStop) {
        Result r = segmentMicroChannels(image, margin, channelHeight, fillingProportion, minObjectSize);
        int yMin = Math.max(yStart, r.yMin);
        yStop = Math.min(yStop, yMin+channelHeight);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        xStart = Math.max(xStart, r.getXMin()-cropMargin);
        xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (debug) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    public static Result segmentMicroChannels(Image image, int margin, int channelHeight, double fillingProportion, int minObjectSize) {
        double thldX = channelHeight * fillingProportion; // only take into account roughly filled channels
        thldX /= (double) (image.getSizeY() * image.getSizeZ() ); // mean X projection
        /*
        1) rough segmentation of cells with autothreshold
        2) selection of filled channels using X-projection & threshold on length
        3) computation of Y start using the minimal Y of objects within the selected channels from step 2 (median value of yMins)
        */
        
        double thld = IJAutoThresholder.runThresholder(image, null, AutoThresholder.Method.Triangle); // OTSU / TRIANGLE / YEN 
        ImageByte mask = ImageOperations.threshold(image, thld, true, true);
        //mask = Filters.binaryClose(mask, new ImageByte("segmentation mask::closed", mask), Filters.getNeighborhood(4, 4, mask));
        float[] xProj = ImageOperations.meanProjection(mask, ImageOperations.Axis.X, null);
        ImageFloat imProjX = new ImageFloat("proj(X)", mask.getSizeX(), new float[][]{xProj});
        ImageByte projXThlded = ImageOperations.threshold(imProjX, thldX, true, false).setName("proj(X) thlded: "+thldX);
        if (debug) {
            new IJImageDisplayer().showImage(mask);
            Utils.plotProfile(imProjX, 0, 0, true);
            Utils.plotProfile(projXThlded, 0, 0, true);
        }
        List<Object3D> xObjectList = new ArrayList<Object3D>(ImageLabeller.labelImageList(projXThlded));
        Iterator<Object3D> it = xObjectList.iterator();
        int rightLimit = image.getSizeX() - margin;
        while(it.hasNext()) {
            BoundingBox b = it.next().getBounds();
            if (b.getxMin()<margin || b.getxMax()>rightLimit) it.remove();
        }
        Object3D[] xObjects = xObjectList.toArray(new Object3D[xObjectList.size()]);
        if (xObjects.length==0) return null;
        Object3D[] objects = ImageLabeller.labelImage(mask);
        if (objects.length==0) return null;
        int[] yMins = new int[xObjects.length];
        Arrays.fill(yMins, Integer.MAX_VALUE);
        for (Object3D o : objects) {
            BoundingBox b = o.getBounds();
            if (o.getSize()<minObjectSize) continue;
            X_SEARCH : for (int i = 0; i<xObjects.length; ++i) {
                BoundingBox inter = b.getIntersection(xObjects[i].getBounds());
                if (inter.getSizeX() >= xObjects[i].getBounds().getSizeX() / 2 ) {
                    if (b.getyMin()<yMins[i]) yMins[i] = b.getyMin();
                    break X_SEARCH;
                }
            }
        }
        // get median value of yMins
        List<Integer> yMinsList = new ArrayList<Integer>(yMins.length);
        for (int yMin : yMins) if (yMin!=Integer.MAX_VALUE) yMinsList.add(yMin);
        if (yMinsList.isEmpty()) return null;
        Collections.sort(yMinsList);
        int s = yMinsList.size();
        int yMin =  (s%2 == 0) ? (int) (0.5d + (double)(yMinsList.get(s/2-1)+yMinsList.get(s/2)) /2d) : yMinsList.get(s/2);
        if (debug) logger.debug("Ymin: {}, among: {} values : {}", yMin, yMinsList.size(), yMins);
        
        return new Result(xObjects, yMin);
        
    }
    

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        BoundingBox bounds = new BoundingBox(configurationData.get(0), configurationData.get(1), configurationData.get(2), configurationData.get(3), 0, image.getSizeZ()-1);
        return image.crop(bounds);
    }

    public ArrayList getConfigurationData() {
        return configurationData;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    public static class Result {
        public final int[] xMax;
        public final int[] xMin;
        public final int yMin;
        public Result(Object3D[] xObjects, int yMin) {
            this.yMin = yMin;
            this.xMax= new int[xObjects.length];
            this.xMin=new int[xObjects.length];
            for (int i = 0; i<xObjects.length; ++i) {
                xMax[i] = xObjects[i].getBounds().getxMax();
                xMin[i] = xObjects[i].getBounds().getxMin();
            }
        }
        public int getXMin() {
            return xMin[ArrayUtil.min(xMin)];
        }
        public int getXMax() {
            return xMax[ArrayUtil.max(xMax)];
        }
        public double getXMean(int idx) {
            return (xMax[idx]+xMin[idx]) / 2d ;
        }
    }
}