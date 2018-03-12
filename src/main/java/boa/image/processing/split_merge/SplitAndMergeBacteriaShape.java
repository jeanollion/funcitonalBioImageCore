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
package boa.image.processing.split_merge;

import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.image.BoundingBox.getDistance;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import boa.image.processing.Curvature;
import boa.image.processing.EDT;
import boa.image.processing.Filters;
import boa.image.processing.ImageOperations;
import boa.image.processing.WatershedTransform;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.localthickness.LocalThickness;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.image.processing.neighborhood.Neighborhood;
import boa.image.processing.split_merge.SplitAndMergeBacteriaShape.InterfaceLocalShape;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import static boa.plugins.plugins.trackers.ObjectIdxTracker.getComparatorRegion;
import boa.utils.HashMapGetCreate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import net.imglib2.KDTree;
import net.imglib2.Point;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;

/**
 *
 * @author jollion
 */
public class SplitAndMergeBacteriaShape extends SplitAndMerge<InterfaceLocalShape> {
    protected final HashMap<Region, KDTree<Double>> curvatureMap = new HashMap<>();
    protected Image distanceMap;
    
    public boolean curvaturePerCluster = true;
    public int curvatureScale=6;
    //public double curvatureSearchScale=2.5;
    
    public int minSizeFusion=0;
    public int minInterfaceSize = -1;
    public boolean ignoreEndOfChannelRegionWhenMerginSmallRegions=true;
    
    public boolean curvCriterionOnBothSides = false;
    public double thresholdCurvMean=-0.03, thresholdCurvSides=-0.02; // uncalibrated
    
    public boolean useThicknessCriterion = true;
    public double relativeThicknessThreshold=0.8; // thickness@interface/thickness of adjacent bacteria < this value : split
    public double relativeThicknessMaxDistance=15; // in pixels
    
    
    
    private final static double maxMergeDistanceBB = 0; // distance in pixel for merging small objects during main process // was 3
    
    private double yLimLastObject = Double.NaN;
    public BiFunction<? super InterfaceLocalShape, ? super InterfaceLocalShape, Integer> compareMethod=null;
    
    public SplitAndMergeBacteriaShape(Image intensityMap) {
        super(intensityMap);
    }
    
    @Override
    public Image getWatershedMap() {
        return distanceMap;
    }

    @Override
    protected ClusterCollection.InterfaceFactory<Region, InterfaceLocalShape> createFactory() {
        return (Region e1, Region e2) -> new InterfaceLocalShape(e1, e2);
    }
    
    private Image getEDM() {
        return distanceMap;
    }
    private void setDistanceMap(ImageMask mask) {
        distanceMap = EDT.transform(mask, true, 1, mask.getScaleZ()/mask.getScaleXY(), 1);
    }
    
    @Override
    public RegionPopulation merge(RegionPopulation popWS, int objectMergeLimit) {
        if (distanceMap == null && (useThicknessCriterion || curvaturePerCluster)) setDistanceMap(popWS.getLabelMap());
        popWS.smoothRegions(2, true, null);
        RegionCluster<InterfaceLocalShape> c = new RegionCluster(popWS, false, true, getFactory());
        c.addForbidFusionPredicate(forbidFusion);
        RegionCluster.verbose=this.testMode;
        if (minSizeFusion>0) c.mergeSmallObjects(minSizeFusion, objectMergeLimit, null);
        if (ignoreEndOfChannelRegionWhenMerginSmallRegions && !popWS.getRegions().isEmpty()) yLimLastObject = Collections.max(popWS.getRegions(), (o1, o2)->Double.compare(o1.getBounds().yMax(), o2.getBounds().yMax())).getBounds().yMax();
        if (curvaturePerCluster) updateCurvature(c.getClusters(), popWS.getLabelMap());
        c.mergeSort(objectMergeLimit<=1, 0, objectMergeLimit);
        if (minSizeFusion>0) {
            BiFunction<Region, Set<Region>, Region> noInterfaceCase = (smallO, set) -> {
                if (set.isEmpty()) return null;
                Region closest = Collections.min(set, (o1, o2) -> Double.compare(getDistance(o1.getBounds(), smallO.getBounds()), getDistance(o2.getBounds(), smallO.getBounds())));
                double d = GeometricalMeasurements.getDistanceBB(closest, smallO, false);
                if (testMode) logger.debug("merge small objects with no interface: min distance: {} to {} = {}", smallO.getLabel(), closest.getLabel(), d);
                if (d<maxMergeDistanceBB) return closest;
                else return null;
            }; 
            c.mergeSmallObjects(minSizeFusion, objectMergeLimit, noInterfaceCase);
        }
        Collections.sort(popWS.getRegions(), getComparatorRegion(ObjectIdxTracker.IndexingOrder.YXZ)); // sort by increasing Y position
        popWS.relabel(true);
        distanceMap = null;
        return popWS;
    }
    @Override
    public RegionPopulation splitAndMerge(ImageMask segmentationMask, int minSizePropagation, int objectMergeLimit) {
        setDistanceMap(segmentationMask);
        WatershedTransform.SizeFusionCriterion sfc = minSizePropagation>1 ? new WatershedTransform.SizeFusionCriterion(minSizePropagation) : null;
        ImageByte seeds = Filters.localExtrema(getEDM(), null, true, segmentationMask, Filters.getNeighborhood(3, 3, getEDM())); // TODO seed radius -> parameter ? 
        ImageOperations.jitterIntegerValues(distanceMap, segmentationMask, 3);
        WatershedTransform wt = new WatershedTransform(distanceMap, segmentationMask, ImageLabeller.labelImageList(seeds), true, null, sfc);
        wt.setLowConnectivity(true);
        wt.runDirectSegmentation();
        RegionPopulation popWS = wt.getRegionPopulation();
        
        if (testMode) {
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            //ImageWindowManagerFactory.showImage(seeds);
            //ImageWindowManagerFactory.showImage(getWatershedMap());
            ImageWindowManagerFactory.showImage(popWS.getLabelMap().duplicate("Split by EDM: labels before merge"));
        }
        
        return merge(popWS, objectMergeLimit);
    }
    
    public void updateCurvature(List<Set<Region>> clusters, ImageProperties props) { // need to be called in order to use curvature in Interface is not per this
        curvatureMap.clear();
        ImageByte clusterMap = new ImageByte("cluster map", props).resetOffset(); // offset is added if getCurvature method
        clusterMap.setCalibration(1, 1);
        Iterator<Set<Region>> it = clusters.iterator();
        while(it.hasNext()) {
            Set<Region> clust = it.next();
            for (Region o : clust) o.draw(clusterMap, 1);
            //Filters.binaryOpen(clusterMap, clusterMap, Filters.getNeighborhood(1, 1, clusterMap)); // avoid funny values // done at segmentation step
            KDTree<Double> curv = Curvature.computeCurvature(clusterMap, curvatureScale);
            /*if (debug) {
                logger.debug("curvature map: {}", curv.size());
                try {
                    Image c = Curvature.getCurvatureMask(clusterMap, curv);
                    if (c!=null) ImageWindowManagerFactory.showImage(c);
                } catch(Exception e) {
                    logger.debug("error curv map show", e);
                }
            }*/
            for (Region o : clust) {
                curvatureMap.put(o, curv);
                if (it.hasNext()) o.draw(clusterMap, 0);
            }
        }
    }
    
    private static double[] getCenter(Collection<Voxel> vox) {
        double[] xy = new double[2];
        for (Voxel v : vox) {
            xy[0]+=v.x;
            xy[1]+=v.y;
        }
        xy[0]/=vox.size();
        xy[1]/=vox.size();
        return xy;
    }
    
    public class InterfaceLocalShape extends InterfaceRegionImpl<InterfaceLocalShape> implements RegionCluster.InterfaceVoxels<InterfaceLocalShape> {
        double maxDistance=Double.NEGATIVE_INFINITY;
        double curvatureValue=Double.POSITIVE_INFINITY;
        double curvL=Double.NaN, curvR=Double.NaN;
        double relativeThickNess = Double.NEGATIVE_INFINITY;
        Voxel maxVoxel=null;
        double value=Double.NaN;
        private final Set<Voxel> borderVoxels = new HashSet<>(), borderVoxels2 = new HashSet<>();
        private final Set<Voxel> voxels = new HashSet<>();
        private final Neighborhood borderNeigh = new EllipsoidalNeighborhood(1.5, true);
        private ImageInteger joinedMask;
        private KDTree<Double> localCurvatureMap;
        public InterfaceLocalShape(Region e1, Region e2) {
            super(e1, e2);
        }
        @Override public Collection<Voxel> getVoxels() {
            return voxels;
        }
        public double getCurvatureValue() {
            return curvatureValue;
        }
        private ImageInteger getJoinedMask() {
            if (joinedMask==null) {
                // getJoinedMask of 2 objects
                ImageInteger m1 = e1.getMaskAsImageInteger();
                ImageInteger m2 = e2.getMaskAsImageInteger();
                MutableBoundingBox joinBox = m1.getBoundingBox(); 
                joinBox.expand(m2.getBoundingBox());
                ImageByte mask = new ImageByte("joinedMask:"+e1.getLabel()+"+"+e2.getLabel(), joinBox.getBlankMask());//.setCalibration(m1);
                Image.pasteImage(m1, mask, m1.getBoundingBox().translate(mask.getBoundingBox().reverseOffset()));
                
                if (testMode) for (Voxel v : e2.getVoxels()) mask.setPixelWithOffset(v.x, v.y, v.z, 2);
                else ImageOperations.orWithOffset(m2, mask, mask);
                joinedMask = mask;
            }
            return joinedMask;
        }

        public KDTree<Double> getCurvatureMap() {
            //if (debug || ProcessingVariables.this.splitVerbose) logger.debug("interface: {}, contains curvature: {}", this, ProcessingVariables.this.curvatureMap.containsKey(e1));
            if (borderVoxels.isEmpty()) setBorderVoxels();
            if (curvaturePerCluster) return curvatureMap.get(e1);
            else {
                if (localCurvatureMap==null) {
                    localCurvatureMap = Curvature.computeCurvature(getJoinedMask(), curvatureScale);
                    if (testMode && false && ((e1.getLabel()==2 && e2.getLabel()==4))) {
                        ImageWindowManagerFactory.showImage(joinedMask);
                        ImageWindowManagerFactory.showImage(Curvature.getCurvatureMask(getJoinedMask(), localCurvatureMap));
                    }
                }
                return localCurvatureMap;
            }
        }

        @Override public void updateInterface() {
            joinedMask = null;
            localCurvatureMap = null;
            if (voxels.size()<=minInterfaceSize) curvatureValue=Double.NEGATIVE_INFINITY; // when border is too small curvature may not be computable, but objects should not be merged
            else if (getCurvatureMap()!=null) {
                curvatureValue = getMeanOfMinCurvature();
            } //else logger.debug("no curvature found for: {}", this);
            if (Double.isNaN(curvatureValue)) curvatureValue = Double.NEGATIVE_INFINITY; // curvature cannot be computed for objects too small
            //else logger.debug("curvature null");
        }
        @Override
        public void performFusion() {
            SplitAndMergeBacteriaShape.this.regionChanged(e1);
            SplitAndMergeBacteriaShape.this.regionChanged(e2);
            super.performFusion();
        }
        @Override 
        public void fusionInterface(InterfaceLocalShape otherInterface, Comparator<? super Region> elementComparator) {
            if (otherInterface.maxDistance>maxDistance) {
                this.maxDistance=otherInterface.maxDistance;
                this.maxVoxel=otherInterface.maxVoxel;
            }
            joinedMask=null;
            localCurvatureMap=null;
            voxels.addAll(otherInterface.voxels);
            setBorderVoxels();
        }

        @Override
        public boolean checkFusion() {
            if (maxVoxel==null) return false;
            if (this.voxels.isEmpty()) return false;
            // criterion on size
            if ((this.e1.size()<minSizeFusion && (Double.isNaN(yLimLastObject) || e1.getBounds().yMax()<yLimLastObject)) || (e2.size()<minSizeFusion&& (Double.isNaN(yLimLastObject) || e2.getBounds().yMax()<yLimLastObject))) return true; // fusion of small objects, except for last objects

            // criterion on curvature
            // curvature has been computed @ upadateSortValue
            if (testMode) logger.debug("check fusion interface: {}+{}, Mean curvature: {} ({} & {}), Threshold: {} & {}", e1.getLabel(), e2.getLabel(), curvatureValue, curvL, curvR, thresholdCurvMean, thresholdCurvSides);
            if (curvCriterionOnBothSides && ((Double.isNaN(curvL) || curvL<thresholdCurvSides || Double.isNaN(curvR) || curvR<thresholdCurvSides))) return false;
            if (!curvCriterionOnBothSides && (curvatureValue<thresholdCurvMean || (curvL<thresholdCurvSides && (Double.isNaN(curvR) || curvR<thresholdCurvSides)))) return false;
            if (!useThicknessCriterion) return true;
            
            
            double max1 = Double.NEGATIVE_INFINITY;
            double max2 = Double.NEGATIVE_INFINITY;
            for (Voxel v : e1.getVoxels()) if (getEDM().getPixel(v.x, v.y, v.z)>max1 && v.getDistance(maxVoxel)<relativeThicknessMaxDistance) max1 = getEDM().getPixel(v.x, v.y, v.z);
            for (Voxel v : e2.getVoxels()) if (getEDM().getPixel(v.x, v.y, v.z)>max2 && v.getDistance(maxVoxel)<relativeThicknessMaxDistance) max2 = getEDM().getPixel(v.x, v.y, v.z);

            double norm = Math.min(max1, max2);
            value = maxDistance/norm;
            if (testMode) logger.debug("Thickness criterioninterface: {}+{}, norm: {} maxInter: {}, criterion value: {} threshold: {} fusion: {}, scale: {}", e1.getLabel(), e2.getLabel(), norm, maxDistance,value, relativeThicknessThreshold, value>relativeThicknessThreshold, e1.getScaleXY() );
            return  value>relativeThicknessThreshold;
        }
        private void searchKDTree(RadiusNeighborSearchOnKDTree<Double> search, RealLocalizable r, double searchScale, Map<RealLocalizable, double[]> res) {
            search.search(r, searchScale, false);
            int n = search.numNeighbors();
            for (int i = 0; i<n; ++i) {
                res.put(search.getPosition(i), new double[]{search.getSquareDistance(i), search.getSampler(i).get()});
            }
        }
        private double dSq(RealLocalizable r1, RealLocalizable r2) {
            return Math.pow(r1.getDoublePosition(0)-r2.getDoublePosition(0), 2) + Math.pow(r1.getDoublePosition(1)-r2.getDoublePosition(1), 2);
        }
        private double[] getMinCurvature(Collection<Voxel> left, Collection<Voxel> right) {
            RealPoint l = new RealPoint(getCenter(left));
            RealPoint r = new RealPoint(getCenter(right));
            RadiusNeighborSearchOnKDTree<Double> search = new RadiusNeighborSearchOnKDTree(getCurvatureMap());
            Map<RealLocalizable, double[]> resL = new HashMap<>(); 
            Map<RealLocalizable, double[]> resR = new HashMap<>();
            double searchScale = 3;
            if (left.size()==2) searchKDTree(search, l, searchScale, resL);
            else for (Voxel v : left) searchKDTree(search, new Point(v.x, v.y), searchScale, resL);
            if (right.size()==2) searchKDTree(search, r, searchScale, resR);
            else for (Voxel v : right) searchKDTree(search, new Point(v.x, v.y), searchScale, resR);
            resL.entrySet().removeIf(p->dSq(r, p.getKey())<=p.getValue()[0]); // remove points closer to other border
            resR.entrySet().removeIf(p->dSq(l, p.getKey())<=p.getValue()[0]);
            // get closest point
            Comparator<double[]> comp = (d1, d2) -> {
                int c = Double.compare(d1[0], d2[0]); // min distance
                if (c!=0) return c;
                return Double.compare(d1[1], d2[1]); // min curvature
            };
            double[] res = new double[]{Double.NaN, Double.NaN};
            if (!resL.isEmpty()) res[0] = Collections.min(resL.values(), comp)[1];
            if (!resR.isEmpty()) res[1] = Collections.min(resR.values(), comp)[1];
            /*if (testMode && Double.isNaN(res[0])||Double.isNaN(res[1])) {
                ImageWindowManagerFactory.showImage(getJoinedMask());
                if (curvaturePerCluster) ImageWindowManagerFactory.showImage(Curvature.getCurvatureMask(getWatershedMap().getBoundingBox().translateToOrigin().getImageProperties(), curvatureMap.get(e1)));
            }*/
            return res;
        }
        private double getMinCurvature(Collection<Voxel> voxels) { // returns negative infinity if no border
            if (voxels.isEmpty()) return Double.NEGATIVE_INFINITY;
            //RadiusNeighborSearchOnKDTree<Double> search = new RadiusNeighborSearchOnKDTree(getCurvature());
            NearestNeighborSearchOnKDTree<Double> search = new NearestNeighborSearchOnKDTree(getCurvatureMap());
            
            double min = Double.POSITIVE_INFINITY;
            for (Voxel v : voxels) {
                search.search(new Point(new int[]{v.x, v.y}));
                double d = search.getSampler().get();
                if (d<min) min = d;
            }

            /*double searchScale = curvatureSearchScale;
            double searchScaleLim = 2 * curvatureSearchScale;
            while(Double.isInfinite(min) && searchScale<searchScaleLim) { // curvature is smoothed thus when there are angles the neerest value might be far away. progressively increment search scale in order not to reach the other side too easily
                for(Voxel v : voxels) {

                    search.search(new Point(new int[]{v.x, v.y}), searchScale, true);
                    if (search.numNeighbors()>=1) min=search.getSampler(0).get();
                    //for (int i = 0; i<search.numNeighbors(); ++i) {
                    //    Double d = search.getSampler(i).get();
                    //    if (min>d) min = d;
                    //}
                }
                ++searchScale;
            }*/
            if (Double.isInfinite(min)) return Double.NEGATIVE_INFINITY;
            return min;
        }
        public double getMeanOfMinCurvature() {
            curvL=Double.NaN;
            curvR=Double.NaN;
            if (!borderVoxels.isEmpty() && !borderVoxels2.isEmpty()) {
                double[] curv = getMinCurvature(borderVoxels, borderVoxels2);
                curvL = curv[0];
                curvR = curv[1];
            } else if (!borderVoxels.isEmpty()) curvL = getMinCurvature(borderVoxels);
            
            if (Double.isNaN(curvL)&&Double.isNaN(curvR)) {
                if (testMode) logger.debug("{} : NO BORDER VOXELS");
                return Double.NEGATIVE_INFINITY; // inclusion of the two regions
            } else {    
                if (borderVoxels2.isEmpty()) {
                    if (testMode) logger.debug("{}, GET CURV: {}, borderVoxels: {}", this, getMinCurvature(borderVoxels), borderVoxels.size());
                    return curvL;
                } else {
                    //logger.debug("mean of min: b1: {}, b2: {}", getMinCurvature(borderVoxels), getMinCurvature(borderVoxels2));
                    //return 0.5 * (getMinCurvature(borderVoxels)+ getMinCurvature(borderVoxels2));
                    double res;
                    if (Double.isNaN(curvL)) res = curvR;
                    else if (Double.isNaN(curvR)) res = curvL;
                    else if ((Math.abs(curvL-curvR)>2*Math.abs(thresholdCurvMean))) {  // when one side has a curvature very different from the other -> hole -> do not take into acount // TODO: check generality of criterion. put parameter? 
                        res = Math.max(curvL, curvR);
                    } else res = 0.5 * (curvL + curvR); 
                    if ( testMode) logger.debug("{}, GET CURV: {}&{} -> {} , borderVoxels: {}&{}", this, curvL, curvR, res, borderVoxels.size(), borderVoxels2.size());
                    return res;
                }
            }
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
            addVoxel(getEDM(), v1);
            addVoxel(getEDM(), v2);
        }
        private void addVoxel(Image image, Voxel v) {
            double pixVal =image!=null ? image.getPixel(v.x, v.y, v.z) : 0;
            if (pixVal>maxDistance) {
                maxDistance = pixVal;
                maxVoxel = v;
            }
            voxels.add(v);
            //v.value=(float)pixVal;
        }
        private void setBorderVoxels() {
            borderVoxels.clear();
            borderVoxels2.clear();
            if (voxels.isEmpty()) return;
            // add border voxel
            ImageInteger mask = getJoinedMask();
            Set<Voxel> allBorderVoxels = new HashSet<>();
            for (Voxel v : voxels) if (borderNeigh.hasNullValue(v.x-mask.xMin(), v.y-mask.yMin(), v.z-mask.zMin(), mask, true)) allBorderVoxels.add(v);
            //if ((testMode) && allBorderVoxels.isEmpty()) ImageWindowManagerFactory.showImage(mask.duplicate("joindedMask "+this));
            //logger.debug("all border voxels: {}", allBorderVoxels.size());
            populateBoderVoxel(allBorderVoxels);
        }

        private void populateBoderVoxel(Collection<Voxel> allBorderVoxels) {
            if (allBorderVoxels.isEmpty()) return;

            MutableBoundingBox b = new MutableBoundingBox();
            for (Voxel v : allBorderVoxels) b.expand(v);
            ImageByte mask = new ImageByte("", b.getBlankMask());
            for (Voxel v : allBorderVoxels) mask.setPixelWithOffset(v.x, v.y, v.z, 1);
            RegionPopulation pop = new RegionPopulation(mask, false);
            pop.translate(b, false);
            List<Region> l = pop.getRegions();
            if (l.isEmpty()) logger.error("interface: {}, no side found", this);
            else if (l.size()>=1) { // case of small borders -> only one distinct side
                borderVoxels.addAll(l.get(0).getVoxels());   
                if (l.size()==2) borderVoxels2.addAll(l.get(1).getVoxels());} 
            else {
                if (testMode) logger.error("interface: {}, #{} sides found!!", this, l.size());
            }
            if (testMode) {
                for (Voxel v : borderVoxels) joinedMask.setPixelWithOffset(v.x, v.y, v.z, 3);
                for (Voxel v : borderVoxels2) joinedMask.setPixelWithOffset(v.x, v.y, v.z, 4);
            }
        }

        @Override public int compareTo(InterfaceLocalShape t) { // decreasingOrder of curvature value
            int c = compareMethod!=null ? compareMethod.apply(this, t) : Double.compare(t.curvatureValue, curvatureValue); // convex interfaces first
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator); // consitency with equals method
            else return c;
        }

        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " curvature: "+curvatureValue;
        }
    }
    
    
}
