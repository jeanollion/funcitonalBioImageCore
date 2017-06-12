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
package plugins.plugins.processingScheme;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.PostFilterSequence;
import configuration.parameters.PreFilterSequence;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.UseMaps;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SegmentOnly implements ProcessingScheme {
    @FunctionalInterface public static interface ApplyToSegmenter { public void apply(StructureObject o, Segmenter segmenter);}
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    Parameter[] parameters;
    
    public SegmentOnly() {}
    
    public SegmentOnly(Segmenter segmenter) {
        this.segmenter.setPlugin(segmenter);
    }
    protected SegmentOnly(PluginParameter<Segmenter> segmenter) {
        this.segmenter=segmenter;
    }
    @Override public SegmentOnly addPreFilters(PreFilter... preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentOnly addPostFilters(PostFilter... postFilter) {
        postFilters.add(postFilter);
        return this;
    }
    @Override public SegmentOnly addPreFilters(Collection<PreFilter> preFilter) {
        preFilters.add(preFilter);
        return this;
    }
    @Override public SegmentOnly addPostFilters(Collection<PostFilter> postFilter){
        postFilters.add(postFilter);
        return this;
    }
    public SegmentOnly setPreFilters(PreFilterSequence preFilters) {
        this.preFilters=preFilters;
        return this;
    }
    public SegmentOnly setPostFilters(PostFilterSequence postFilters) {
        this.postFilters=postFilters;
        return this;
    }
    @Override public PreFilterSequence getPreFilters() {
        return preFilters;
    }
    
    @Override public PostFilterSequence getPostFilters() {
        return postFilters;
    }
    @Override public List<Pair<String, Exception>> segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor) {
        return segmentAndTrack(structureIdx, parentTrack, executor, null);
    }
    public List<Pair<String, Exception>> segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack, ExecutorService executor, ApplyToSegmenter applyToSegmenter) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return Collections.EMPTY_LIST;
        }
        if (parentTrack.isEmpty()) return Collections.EMPTY_LIST;
        int parentStructureIdx = parentTrack.get(0).getStructureIdx();
        int segParentStructureIdx = parentTrack.get(0).getExperiment().getStructure(structureIdx).getSegmentationParentStructure();
        boolean subSegmentation = segParentStructureIdx>parentStructureIdx;
        boolean useMaps =  subSegmentation && segmenter.instanciatePlugin() instanceof UseMaps;
        boolean singleFrame = parentTrack.get(0).getMicroscopyField().singleFrame(structureIdx); // will semgent only on first frame
        
        HashMapGetCreate<StructureObject, Image> inputImages =  new HashMapGetCreate<>(parentTrack.size(), parent->preFilters.filter(parent.getRawImage(structureIdx), parent));
        HashMapGetCreate<StructureObject, Image[]> subMaps = useMaps? new HashMapGetCreate<>(parentTrack.size(), parent->((UseMaps)segmenter.instanciatePlugin()).computeMaps(parent.getRawImage(structureIdx), inputImages.getAndCreateIfNecessarySync(parent))) : null; //
        
        // segment in direct parents
        List<StructureObject> allParents = singleFrame ? StructureObjectUtils.getAllChildren(parentTrack.subList(0, 1), segParentStructureIdx) : StructureObjectUtils.getAllChildren(parentTrack, segParentStructureIdx);
        Collections.shuffle(allParents); // reduce thread blocking
        ObjectPopulation[] pops = new ObjectPopulation[allParents.size()];
        List<Pair<String, Exception>> errors = ThreadRunner.execute(allParents, false, (subParent, idx) -> {
            StructureObject globalParent = subParent.getParent(segParentStructureIdx);
            Segmenter seg = segmenter.instanciatePlugin();
            if (useMaps) {
                Image[] maps = subMaps.getAndCreateIfNecessarySync(globalParent);
                ((UseMaps)seg).setMaps(Utils.transform(maps, new Image[maps.length], i -> i.cropWithOffset(subParent.getBounds())));
            }
            if (applyToSegmenter!=null) applyToSegmenter.apply(subParent, seg);
            Image input = inputImages.getAndCreateIfNecessarySync(globalParent);
            if (subSegmentation) input = input.cropWithOffset(subParent.getBounds());
            ObjectPopulation pop = seg.runSegmenter(input, structureIdx, subParent);
            pop = postFilters.filter(pop, structureIdx, subParent);
            if (subSegmentation && pop!=null) pop.translate(subParent.getBounds(), true);
            pops[idx] = pop;
        }, executor, null);
        inputImages.clear();
        if (useMaps) subMaps.clear();
        // collect if necessary and set to parent
        if (subSegmentation) {
            HashMapGetCreate<StructureObject, List<Object3D>> mapParentPop = new HashMapGetCreate<>(parentTrack.size(), new HashMapGetCreate.ListFactory());
            for (int i = 0; i<pops.length; ++i) {
                StructureObject subParent = allParents.get(i);
                StructureObject parent = subParent.getParent(parentStructureIdx);
                if (pops[i]!=null) mapParentPop.getAndCreateIfNecessary(parent).addAll(pops[i].getObjects());
                else logger.debug("pop null for subParent: {}", allParents.get(i));
            }
            ObjectPopulation pop=null;
            for (Entry<StructureObject, List<Object3D>> e : mapParentPop.entrySet()) {
                pop = new ObjectPopulation(e.getValue(), e.getKey().getMaskProperties(), true);
                e.getKey().setChildrenObjects(pop, structureIdx);
            }
            if (singleFrame) {
                if (mapParentPop.size()>1) logger.error("Segmentation of structure: {} from track: {}, single frame but several populations", structureIdx, parentTrack.get(0));
                else {
                    for (StructureObject parent : parentTrack.subList(1, parentTrack.size())) parent.setChildrenObjects(pop.duplicate(), structureIdx);
                }
            }
        } else {
           for (int i = 0; i<pops.length; ++i) allParents.get(i).setChildrenObjects(pops[i], structureIdx);
           if (singleFrame) {
               if (pops.length>1) logger.error("Segmentation of structure: {} from track: {}, single frame but several populations", structureIdx, parentTrack.get(0));
               else for (StructureObject parent : parentTrack.subList(1, parentTrack.size())) parent.setChildrenObjects(pops[0].duplicate(), structureIdx);
           }
        }
        return errors;
    }
    
    private ObjectPopulation segment(StructureObject parent, int structureIdx, int segmentationStructureIdx) { // TODO mieux gérer threads -> faire liste. Option filtres avant ou après découpage.. 
        Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
        if (segmentationStructureIdx>parent.getStructureIdx()) {
            Segmenter seg = segmenter.instanciatePlugin();
            Image[] maps=null;
            if (seg instanceof UseMaps) maps = ((UseMaps)seg).computeMaps(parent.getRawImage(structureIdx), input);
            List<Object3D> objects = new ArrayList<>();
            for (StructureObject subParent : parent.getChildren(segmentationStructureIdx)) {
                seg = segmenter.instanciatePlugin();
                if (maps!=null) ((UseMaps)seg).setMaps(Utils.transform(maps, new Image[maps.length], i -> i.cropWithOffset(subParent.getBounds())));
                ObjectPopulation pop = seg.runSegmenter(input.cropWithOffset(subParent.getBounds()), structureIdx, subParent);
                pop = postFilters.filter(pop, structureIdx, subParent);
                pop.translate(subParent.getBounds(), true);
                objects.addAll(pop.getObjects());
            }
            //logger.debug("Segment: Parent: {}, subParents: {}, totalChildren: {}, subPBound: {}, {}, {}, pBOunds: {}", parent, parent.getChildren(segmentationStructureIdx).size(), objects.size(), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getBounds().toString()), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getRelativeBoundingBox(parent).toString()), Utils.toStringList(parent.getChildren(segmentationStructureIdx).subList(0,1), o -> o.getRelativeBoundingBox(parent.getRoot()).toString()), parent.getBounds());
            return new ObjectPopulation(objects, input, true);
        } else {
            ObjectPopulation pop = segmenter.instanciatePlugin().runSegmenter(input, structureIdx, parent);
            return postFilters.filter(pop, structureIdx, parent);
        }
    }

    @Override public List<Pair<String, Exception>> trackOnly(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) {return Collections.EMPTY_LIST;}

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters, segmenter, postFilters};
    }
    @Override public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
}
