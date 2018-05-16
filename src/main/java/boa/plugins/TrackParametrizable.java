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

import boa.data_structure.StructureObject;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMaskX;
import boa.image.BlankMask;
import boa.image.Histogram;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import boa.image.processing.ImageOperations;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;
import ij.process.AutoThresholder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 * @param <P> segmenter type
 */
public interface TrackParametrizable<P extends Plugin> {
    /**
     * Interface Allowing to parametrize a plugin using information from whole parent track
     * @param <P> type of plugin to be parametrized
     */
    @FunctionalInterface public static interface TrackParametrizer<P> { 
        /**
         * Parametrizes the {@param segmenter}
         * This method may be called asynchronously with different pairs of {@param parent}/{@param segmenter}
         * @param parent parent object from the parent track used to create the {@link boa.plugins.TrackParametrizable.TrackParametrizer apply to segmenter object} See: {@link #getTrackParametrizer(int, java.util.List, boa.plugins.Segmenter, java.util.concurrent.ExecutorService) }. This is not necessary the segmentation parent that will be used as argument in {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         * @param plugin Segmenter instance that will be parametrized, prior to call the method {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
         */
        public void apply(StructureObject parent, P plugin);
    }
    /**
     * 
     * @param structureIdx index of the structure to be segmented via call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     * @param parentTrack parent track (elements are parent of structure {@param structureIdx}
     * @return ApplyToSegmenter object that will parametrize Segmenter instances before call to {@link boa.plugins.Segmenter#runSegmenter(boa.image.Image, int, boa.data_structure.StructureObjectProcessing) }
     */
    public TrackParametrizer run(int structureIdx, List<StructureObject> parentTrack);
    
    // + static helpers methods
    public static <P extends Plugin> TrackParametrizer<P> getTrackParametrizer(int structureIdx, List<StructureObject> parentTrack, P plugin) {
        if (plugin instanceof TrackParametrizable) {
            TrackParametrizable tp = (TrackParametrizable)plugin;
            if (tp instanceof MultiThreaded) ((MultiThreaded)tp).setMultithread(true);
            return tp.run(structureIdx, parentTrack);
        }
        return null;
    }
    
    
    public static double getGlobalThreshold(int structureIdx, List<StructureObject> parentTrack, SimpleThresholder thlder) {
        Map<Image, ImageMask> maskMap = parentTrack.stream().collect(Collectors.toMap(p->p.getPreFilteredImage(structureIdx), p->p.getMask()));
        if (thlder instanceof ThresholderHisto) {
            Histogram hist = Histogram.getHisto256(maskMap, null, true);
            return ((ThresholderHisto)thlder).runThresholderHisto(hist);
        } else {
            Supplier<Pair<List<Image>, List<ImageInteger>>> supplier = ()->new Pair<>(new ArrayList<>(), new ArrayList<>());
            BiConsumer<Pair<List<Image>, List<ImageInteger>>, Map.Entry<Image, ImageMask>> accumulator =  (p, e)->{
                p.key.add(e.getKey());
                if (!(e.getValue() instanceof BlankMask)) p.value.add((ImageInteger)TypeConverter.toCommonImageType(e.getValue()));
            };
            BiConsumer<Pair<List<Image>, List<ImageInteger>>, Pair<List<Image>, List<ImageInteger>>> combiner = (p1, p2) -> {p1.key.addAll(p2.key);p1.value.addAll(p2.value);};
            Pair<List<Image>, List<ImageInteger>> globalImagesList = maskMap.entrySet().stream().collect( supplier,  accumulator,  combiner);
            Image globalImage = (Image)Image.mergeImagesInZ(globalImagesList.key);
            ImageMask globalMask = globalImagesList.value.isEmpty() ? new BlankMask(globalImage) : (ImageInteger)Image.mergeImagesInZ(globalImagesList.value);
            return thlder.runSimpleThresholder(globalImage, globalMask);
        }
    }
    
    
}
