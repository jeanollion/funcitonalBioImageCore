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
import java.util.ArrayList;;
import java.util.List;
import processing.ImageFeatures;
import utils.ArrayUtil;
import utils.Utils;
import static utils.Utils.plotProfile;

/**
 *
 * @author jollion
 */
public class CropMicroChannelBF2D extends CropMicroChannels {
    public static boolean debug = false;
    NumberParameter microChannelWidth = new BoundedNumberParameter("Microchannel Width (pix)", 0, 26, 5, null);
    NumberParameter microChannelWidthError = new BoundedNumberParameter("Microchannel Width error proportion", 2, 0.15, 0, 1);
    Parameter[] parameters = new Parameter[]{channelHeight, cropMargin, margin, microChannelWidth, microChannelWidthError, xStart, xStop, yStart, yStop, number};
    
    public CropMicroChannelBF2D(int margin, int cropMargin, int microChannelWidth, double microChannelWidthError, int timePointNumber) {
        this.margin.setValue(margin);
        this.cropMargin.setValue(cropMargin);
        this.microChannelWidth.setValue(microChannelWidth);
        this.microChannelWidthError.setValue(microChannelWidthError);
        this.number.setValue(timePointNumber);
    }
    
    public CropMicroChannelBF2D() {
        
    }
    
    public CropMicroChannelBF2D setTimePointNumber(int timePointNumber) {
        this.number.setValue(timePointNumber);
        return this;
    }
    
    @Override public BoundingBox getBoundingBox(Image image) {
        return getBoundingBox(image, cropMargin.getValue().intValue(), margin.getValue().intValue(), channelHeight.getValue().intValue(), microChannelWidth.getValue().intValue(), microChannelWidthError.getValue().doubleValue(), xStart.getValue().intValue(), xStop.getValue().intValue(), yStart.getValue().intValue(), yStop.getValue().intValue());
    }
    
    public static BoundingBox getBoundingBox(Image image, int cropMargin, int margin, int channelHeight, int channelWidth, double channelWidthError, int xStart, int xStop, int yStart, int yStop) {
        Result r = segmentMicroChannels(image, true, margin, channelWidth, channelWidthError);
        int yMin = Math.max(yStart, r.yMin);
        int yMax = Math.min(r.yMin+channelHeight, r.yMax);
        yStop = Math.min(yStop, yMax);
        yStart = Math.max(yMin-cropMargin, yStart);
        
        xStart = Math.max(xStart, r.getXMin()-cropMargin);
        xStop = Math.min(xStop, r.getXMax() + cropMargin);
        if (debug) logger.debug("Xmin: {}, Xmax: {}", r.getXMin(), r.getXMax());
        return new BoundingBox(xStart, xStop, yStart, yStop, 0, image.getSizeZ()-1);
        
    }
    public static Result segmentMicroChannels(Image image, boolean opticalAberration, int margin, int channelWidth, double channelWidthError) {
        double derScale = 2;
        int xErode = Math.max(1, (int)(derScale/2d + 0.5+Double.MIN_VALUE));
        double widthMin = (1-channelWidthError) * channelWidth;
        double widthMax = (1+channelWidthError) * channelWidth;
        double localExtremaThld = 0.05d;
        /*
        1) search for optical aberation
        2) search for y-start of MC using Y-proj of d/dy image global max (projection from yE[0; y-aberation]
        3) search of xpositions of microchannels using X-projection (yE[y-start; y-aberration]) of d/dx & peak detection (detection of positive peak & negative peak @ distance of channel weight) 
        */
        
        int aberrationStart = opticalAberration ? searchYLimWithOpticalAberration(image, 0.25, 0.05) : image.getSizeY()-1;
        Image imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, 0, aberrationStart, 0, image.getSizeZ()-1));
        Image imDer = ImageFeatures.getDerivative(imCrop, derScale, 0, 1, 0, true);
        float[] yProj = ImageOperations.meanProjection(imDer, ImageOperations.Axis.Y, null);
        int channelStartIdx = ArrayUtil.max(yProj, 0, (int)(yProj.length*0.75)); // limit to 
        
        imCrop = image.crop(new BoundingBox(0, image.getSizeX()-1, channelStartIdx, aberrationStart, 0, image.getSizeZ()-1));
        float[] xProj = ImageOperations.meanProjection(imCrop, ImageOperations.Axis.X, null); 
        ArrayUtil.gaussianSmooth(xProj, derScale);
        imDer = ImageFeatures.getDerivative(imCrop, derScale, 1, 0, 0, true);
        float[] xProjDer = ImageOperations.meanProjection(imDer, ImageOperations.Axis.X, null);
        if (debug) {
            //plotProfile("XProjDer", xProjDer);
            //plotProfile("XProj smoothed", xProj);
            plotProfile("yProjCrop", yProj);
            float[] norm = new float[xProjDer.length];
            for (int i = 0; i<norm.length; ++i) norm[i] = xProjDer[i] / xProj[i];
            plotProfile("xProjNorm", norm);
        }
        int[] localMax = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), true);
        int[] localMin = ArrayUtil.getRegionalExtrema(xProjDer, (int)(derScale+0.5), false);
        if (debug) {
            logger.debug("{} max found, {} min found", localMax.length, localMin.length);
        }
        List<int[]> peaks = new ArrayList<int[]>();
        int lastMinIdx = -1;
        int leftMargin = image.getSizeX()-margin;
        MAX_LOOP : for (int maxIdx = 0; maxIdx<localMax.length; ++maxIdx) {
            if (isExtremaValid(localMax[maxIdx], localExtremaThld, xProjDer, xProj) && localMax[maxIdx]>margin && localMax[maxIdx]<leftMargin) {
                int minIdx = lastMinIdx;
                MIN_LOOP : while(minIdx<localMin.length-1) {
                    minIdx++;
                    if (localMin[minIdx]>leftMargin) break MAX_LOOP;
                    if (isExtremaValid(localMin[minIdx], localExtremaThld, xProjDer, xProj)) {
                        int d = localMin[minIdx] - localMax[maxIdx];
                        if (d>=widthMin && d<=widthMax) {
                            if (debug) {
                                int x1 = localMax[maxIdx];
                                int x2 = localMin[minIdx];
                                logger.debug("Peak found X: [{};{}], distance: {}, value: [{};{}], normedValue: [{};{}]", x1, x2, d, xProjDer[x1], xProjDer[x2], xProjDer[x1]/xProj[x1], xProjDer[x2]/xProj[x2]);
                            }
                            peaks.add(new int[]{localMax[maxIdx]+xErode, localMin[minIdx]-xErode});
                            lastMinIdx = minIdx;
                            break MIN_LOOP;
                        } else if (d>widthMax) break MIN_LOOP;
                    }
                }
            }
        }
        return new Result(peaks, channelStartIdx, aberrationStart);
        
    }
    
    private static boolean isExtremaValid(int extrema, double thld, float[] values, float[] normValues) {
        return Math.abs(values[extrema] / normValues[extrema]) > thld;
    }
    
    public static int searchYLimWithOpticalAberration(Image image, double peakProportion, double sigmaThreshold) {
        int slidingSigmaWindow = 20;
        // aberation is @ higher Y coord that microchannels
        /*
        1) Search for global max : yMax
        2) seach for  min after yMax -> get peak hight = h = I(yMax) - I(yMin)
        3) search for first y | y<yMax & I(y) < peakProportion * h & sliding variance < threshold
        */
        float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
        int maxIdx = ArrayUtil.max(yProj);
        int minIdx = ArrayUtil.min(yProj, maxIdx+1, yProj.length);
        double peakHeight = yProj[maxIdx] - yProj[minIdx];
        float thld = (float)(peakHeight * peakProportion + yProj[minIdx] );
        int endOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, maxIdx, 0, thld, true, true);
        
        float[] slidingSigma = new float[debug ? yProj.length : endOfPeakIdx]; // endOfPeakIdx
        double[] meanSigma = new double[2];
        for (int i = slidingSigmaWindow; i<slidingSigma.length; ++i) {
            ArrayUtil.meanSigma(yProj, i-slidingSigmaWindow, i, meanSigma);
            slidingSigma[i-1] = (float)(meanSigma[1] / meanSigma[0] );
        }
        int startOfMicroChannel = ArrayUtil.getFirstOccurence(slidingSigma, endOfPeakIdx-1, slidingSigmaWindow-1, (float)sigmaThreshold, true, true);
        if (debug) {
            new IJImageDisplayer().showImage(image);
            Utils.plotProfile("yProj", yProj);
            Utils.plotProfile("Sliding sigma", slidingSigma);
            logger.debug("minIdx: {}, maxIdx: {}, peakHeightThld: {}, enfOfPeak: {}, startofMc: {}", minIdx, maxIdx, thld, endOfPeakIdx, startOfMicroChannel);
        }
        return startOfMicroChannel;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
}
