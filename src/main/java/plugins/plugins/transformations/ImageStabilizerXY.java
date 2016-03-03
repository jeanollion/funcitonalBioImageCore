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
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.TimePointParameter;
import dataStructure.containers.InputImages;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import static plugins.Plugin.logger;
import plugins.Transformation;
import static plugins.plugins.transformations.ImageStabilizerCore.combine;
import static plugins.plugins.transformations.ImageStabilizerCore.gradient;
import processing.ImageTransformation;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author nasique
 */
public class ImageStabilizerXY implements Transformation {
    ChoiceParameter transformationType = new ChoiceParameter("Transformation", new String[]{"Translation"}, "Translation", false); //, "Affine"
    ChoiceParameter pyramidLevel = new ChoiceParameter("Pyramid Level", new String[]{"0", "1", "2", "3", "4"}, "0", false);
    BoundedNumberParameter alpha = new BoundedNumberParameter("Template Update Coefficient", 2, 1, 0, 1);
    BoundedNumberParameter maxIter = new BoundedNumberParameter("Maximum Iterations", 0, 600, 1, null);
    NumberParameter tol = new BoundedNumberParameter("Error Tolerance", 8, 1e-7, 0, null);
    Parameter[] parameters = new Parameter[]{maxIter, tol, pyramidLevel}; //alpha, pyramidLevel
    ArrayList<ArrayList<Double>> translationTXY = new ArrayList<ArrayList<Double>>();
    
    public ImageStabilizerXY(){}
    
    public ImageStabilizerXY(int transformationType, int pyramidLevel, double templateUpdateCoeff, int maxIterations, double tolerance) {
        this.transformationType.setSelectedIndex(transformationType);
        this.pyramidLevel.setSelectedIndex(pyramidLevel);
        this.alpha.setValue(templateUpdateCoeff);
        this.tol.setValue(tolerance);
        this.maxIter.setValue(maxIterations);
    }
    
    /*public void computeConfigurationData2(int channelIdx, InputImages inputImages) {
        Image imageRef = inputImages.getImage(channelIdx, 0);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        translationTXY = new ArrayList<Double[]>(inputImages.getMaxTimePoint());
        FloatProcessor ipRef = getFloatProcessor(imageRef, false);
        FloatProcessor currentIp;
        translationTXY.add(new Double[2]);
        for (int t = 1; t<inputImages.getMaxTimePoint(); ++t) {
            currentIp = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
            translationTXY[0][t] = performCorrection2(ipRef, currentIp, pyramids);
            ipRef = currentIp;
            logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}", t, translationTXY[0][t][0], translationTXY[0][t][1]);
        }
        for (int t = 2; t<inputImages.getMaxTimePoint(); ++t) {
            translationTXY[0][t][0]+=translationTXY[0][t-1][0];
            translationTXY[0][t][1]+=translationTXY[0][t-1][1];
        }
    }*/
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        long tStart = System.currentTimeMillis();
        final int tRef = inputImages.getDefaultTimePoint();
        //final int tRef=0;
        final int maxIterations = this.maxIter.getValue().intValue();
        final double tolerance = this.tol.getValue().doubleValue();
        
        //new IJImageDisplayer().showImage(imageRef.setName("ref image"));
        //if (true) return;
        final Double[][] translationTXYArray = new Double[inputImages.getTimePointNumber()][];
      
        ccdSegments(channelIdx, inputImages, 20, tRef, translationTXYArray, maxIterations, tolerance);
        
        translationTXY = new ArrayList<ArrayList<Double>>(translationTXYArray.length);
        for (Double[] d : translationTXYArray) translationTXY.add(new ArrayList<Double>(Arrays.asList(d)));
        long tEnd = System.currentTimeMillis();
        logger.debug("ImageStabilizerXY: total estimation time: {}, reference timePoint: {}", tEnd-tStart, tRef);
    }
    
    protected void ccdSegments(final int channelIdx, final InputImages inputImages, int segmentLength, int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        if (segmentLength<2) segmentLength = 2;
        int nSegments = (int)((double)  (0.5 + inputImages.getTimePointNumber()-1) / (double)segmentLength) ;
        int[][] segments = new int[nSegments][3]; // tStart, tEnd, tRef
        for (int i = 0; i<nSegments; ++i) {
            segments[i][0] = i==0 ? 0 : segments[i-1][1]+1;
            segments[i][1] = i==segments.length-1 ? inputImages.getTimePointNumber()-1 : segments[i][0]+segmentLength;
            segments[i][2] = i==0 ? Math.min(Math.max(0, tRef), segments[i][1]) : segments[i-1][1]; 
            logger.debug("segment: {}, {}", i, segments[i]);
        }
        // process each segment
        int nThreads = ThreadRunner.getMaxCPUs();
        final int nParalleleSegments, nThreadPerSegment;
        if (segmentLength<=nThreads*2) {
            nParalleleSegments = nThreads;
            nThreadPerSegment = 1;
        } else {
            nParalleleSegments = 1;
            nThreadPerSegment = nThreads;
        }
        logger.debug("Parallele seg: {}, thread per seg: {}", nParalleleSegments, nThreadPerSegment);
        ThreadRunner.execute(segments, false, nParalleleSegments, new ThreadAction<int[]>() {
            public void run(int[] seg, int idx) {
                ccdSegment(channelIdx, inputImages, seg[0], seg[1], seg[2], nThreadPerSegment, translationTXYArray, maxIterations, tolerance);
            }
        });
        for (int i = 1; i<segments.length; ++i) {
            Double[] ref = translationTXYArray[segments[i][2]];
            for (int t = segments[i][0]; t<=segments[i][1]; ++t) {
                translationTXYArray[t][0]+=ref[0];
                translationTXYArray[t][1]+=ref[1];
            }
            logger.debug("ref: {}, tp: {}, trans: {}", i,segments[i][2], ref);
        }
    }
    
    protected void ccdSegment(final int channelIdx, final InputImages inputImages, final int tStart, final int tEnd, final int tRef, final int nThreads, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        final Image imageRef = inputImages.getImage(channelIdx, tRef);
        final FloatProcessor ipFloatRef = getFloatProcessor(imageRef, false);
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber(), nThreads);
        final ImageProcessor[][][] pyramids = new ImageProcessor[tr.threads.length][][];
        for (int i = 0; i<tr.threads.length; ++i) {
            pyramids[i] = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
            gradient(pyramids[i][1][0], ipFloatRef);
        }
        for (int i = 0; i<tr.threads.length; i++) {
            final int trIdx = i;
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() {
                        for (int t = tr.ai.getAndIncrement(); t<tr.end; t = tr.ai.getAndIncrement()) {
                            if (t==tRef) translationTXYArray[t] = new Double[]{0d, 0d};
                            else translationTXYArray[t] = performCorrection(channelIdx, inputImages, t, pyramids[trIdx]);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        
    }
    
    protected void ccdSegmentTemplateUpdate(final int channelIdx, final InputImages inputImages, final int tStart, final int tEnd, final int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        
        final Image imageRef = inputImages.getImage(channelIdx, tRef);
        FloatProcessor ipFloatRef = getFloatProcessor(imageRef, true);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        FloatProcessor trans=null;
        double a = alpha.getValue().doubleValue();
        translationTXYArray[tRef] = new Double[]{0d, 0d};
        if (a<1) trans  = new FloatProcessor(imageRef.getSizeX(), imageRef.getSizeY());
        for (int t = tRef-1; t>=0; --t) translationTXYArray[t] = performCorrectionWithTemplateUpdate(channelIdx, inputImages, t, ipFloatRef, pyramids, trans, maxIterations, tolerance, a, translationTXYArray[t+1]);
        if (a<1 && tRef>0) ipFloatRef = getFloatProcessor(imageRef, true); // reset template
        for (int t = tRef+1; t<inputImages.getTimePointNumber(); ++t) translationTXYArray[t] = performCorrectionWithTemplateUpdate(channelIdx, inputImages, t, ipFloatRef, pyramids, trans, maxIterations, tolerance, a, translationTXYArray[t-1]);
        
    }
    
    protected void ccdTimeToTime(final int channelIdx, final InputImages inputImages, final int tRef, final Double[][] translationTXYArray, final int maxIterations, final double tolerance) {
        
        
        //new IJImageDisplayer().showImage(imageRef.setName("ref image"));
        //if (true) return;

        /*
        final Image imageRef = inputImages.getImage(channelIdx, 0);
        FloatProcessor ipFloatRef = getFloatProcessor(imageRef, false);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        translationTXYArray[0] = new Double[]{0d, 0d};
        ImageProcessor[] pyrRef = pyramids[1];
        ImageProcessor[] pyrCurrent = pyramids[0];
        ImageProcessor[] temp;
        gradient(pyrRef[0], ipFloatRef);
        for (int t = 1; t<inputImages.getTimePointNumber(); ++t) {
            FloatProcessor ipFloat = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
            double[][] wp = ImageStabilizerCore.estimateTranslation(ipFloat, null, pyrCurrent, pyrRef, false, maxIterations, tolerance, null);
            translationTXYArray[t] = new Double[]{wp[0][0]+translationTXYArray[t-1][0], wp[1][0]+translationTXYArray[t-1][1]};
            ipFloatRef = ipFloat;
            temp = pyrRef;
            pyrRef = pyrCurrent;
            pyrCurrent = temp;
            logger.debug("ImageStabilizerXY: time: {}, dX: {}, dT: {}, cumdX: {}, cumdY: {}", t, wp[0][0], wp[1][0], translationTXYArray[t][0], translationTXYArray[t][1]);
        }
        double[] shiftRef = new double[]{translationTXYArray[tRef][0], translationTXYArray[tRef][1]};
        for (int t = 0; t<inputImages.getTimePointNumber(); ++t) {
            translationTXYArray[t][0]-=shiftRef[0];
            translationTXYArray[t][1]-=shiftRef[1];
        }
        */
        
        
        // multithreaded version: 
        // cut in time segments
        int nThreads = ThreadRunner.getMaxCPUs();
        int length = (int)((double)inputImages.getTimePointNumber() / (double)nThreads) ;
        int[][] segments = new int[nThreads][2];
        for (int i = 0; i<nThreads; ++i) {
            segments[i][0] = i==0 ? 0 : segments[i-1][1]+1;
            segments[i][1] = i==segments.length-1 ? inputImages.getTimePointNumber()-1 : segments[i][0]+length;
            //logger.debug("segment: {}, {}", i, segments[i]);
        }
        ThreadRunner.execute(segments, false, new ThreadAction<int[]>() {
            public void run(int[] tp, int idx) {
                Image imageRef = inputImages.getImage(channelIdx, tp[0]);
                FloatProcessor ipFloatRef = getFloatProcessor(imageRef, false);
                ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
                translationTXYArray[tp[0]] = new Double[]{0d, 0d};
                ImageProcessor[] pyrRef = pyramids[1];
                ImageProcessor[] pyrCurrent = pyramids[0];
                ImageProcessor[] temp;
                gradient(pyrRef[0], ipFloatRef);
                for (int t = tp[0]+1; t<=tp[1]; ++t) {
                    FloatProcessor ipFloat = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
                    double[][] wp = ImageStabilizerCore.estimateTranslation(ipFloat, null, pyrCurrent, pyrRef, false, maxIterations, tolerance, null);
                    logger.debug("Stab: t1: {}, t2: {}, dX: {} dY: {}", t-1, t, wp[0][0], wp[1][0]);
                    translationTXYArray[t] = new Double[]{wp[0][0], wp[1][0]};
                    // exchange pyr -> in next timepoint, the ref will be the current
                    temp = pyrRef;
                    pyrRef = pyrCurrent;
                    pyrCurrent = temp;
                }
            }
        });
        // cumulative shift
        for (int t = 1; t<inputImages.getTimePointNumber(); ++t) {
            translationTXYArray[t][0]+=translationTXYArray[t-1][0];
            translationTXYArray[t][1]+=translationTXYArray[t-1][1];
        }
        double[] shiftRef = new double[]{translationTXYArray[tRef][0], translationTXYArray[tRef][1]};
        for (int t = 0; t<inputImages.getTimePointNumber(); ++t) {
            translationTXYArray[t][0]-=shiftRef[0];
            translationTXYArray[t][1]-=shiftRef[1];
        }
        
    }
    
    
    public static Image testTranslate(Image imageRef, Image imageToTranslate, int maxIterations, double maxTolerance, int pyramidLevel) {
        FloatProcessor ipFloat1 = getFloatProcessor(imageRef, true);
        FloatProcessor ipFloat2 = getFloatProcessor(imageToTranslate, true);
        
        ImageStabilizerCore.debug=true;
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel);
        double[][] wp = ImageStabilizerCore.estimateTranslation(ipFloat2, ipFloat1, pyramids[0], pyramids[1], true, maxIterations, maxTolerance, null);
        
        return ImageTransformation.translate(imageToTranslate, -wp[0][0], -wp[1][0], 0, ImageTransformation.InterpolationScheme.BSPLINE5);
    }
    
    private Double[] performCorrection(int channelIdx, InputImages inputImages, int t, ImageProcessor[][] pyramids) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, null, pyramids[0], pyramids[1], false, maxIter.getValue().intValue(), tol.getValue().doubleValue(), null);
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        //logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        return res;
    }
    
    private Double[] performCorrectionWithTemplateUpdate(int channelIdx, InputImages inputImages, int t, FloatProcessor ipFloatRef, ImageProcessor[][] pyramids,  FloatProcessor trans, int maxIterations, double tolerance, double alpha, Double[] estimateShift) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, ipFloatRef, pyramids[0], pyramids[1], true, maxIterations, tolerance, estimateShift);
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        //update template 
        if (alpha<1) {
            ImageStabilizerCore.warpTranslation(trans, currentTime, wp);
            ImageStabilizerCore.combine(ipFloatRef, trans, alpha);
        }
        return res;
    }
    
    private static FloatProcessor getFloatProcessor(Image image, boolean duplicate) {
        if (image.getSizeZ()>1) image = image.getZPlane((int)(image.getSizeZ()/2.0+0.5)); //select middle slice only
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        else if (duplicate) image = image.duplicate("");
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        return (FloatProcessor)impRef.getProcessor();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        ArrayList<Double> trans = translationTXY.get(timePoint);
        //logger.debug("stabilization time: {}, channel: {}, X:{}, Y:{}", timePoint, channelIdx, trans.get(0), trans.get(1));
        if (trans.get(0)==0 && trans.get(1)==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        /*ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        ImageStack is = impRef.getImageStack();
        float[][] outPixels = new float[image.getSizeZ()][];
        FloatProcessor temp = new FloatProcessor(image.getSizeX(), image.getSizeY());
        double[][] wp = new double[][]{{translationTXY[0][timePoint][0]}, {translationTXY[0][timePoint][1]}};
        for (int z = 0; z<image.getSizeZ(); ++z) {
            ImageStabilizerCore.warpTranslation(temp, is.getProcessor(z+1), wp);
            outPixels[z]=(float[])temp.getPixels();
            temp = (FloatProcessor)is.getProcessor(z+1);
        }
        return new ImageFloat(image.getName(), image.getSizeX(), outPixels);*/
        return ImageTransformation.translate(image, -trans.get(0), -trans.get(1), 0, ImageTransformation.InterpolationScheme.BSPLINE5);
    }

    public ArrayList getConfigurationData() {
        return this.translationTXY;
    }

    public boolean isTimeDependent() {
        return true;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    
}
