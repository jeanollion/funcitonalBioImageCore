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
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import image.Image;
import java.util.List;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.ProcessingScheme;
import plugins.Segmenter;
import plugins.Tracker;

/**
 *
 * @author jollion
 */
public class SegmentThenTrack implements ProcessingScheme {
    protected PluginParameter<Tracker> tracker = new PluginParameter<Tracker>("Tracker", Tracker.class, true);
    protected PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    protected PostFilterSequence postFilters = new PostFilterSequence("Post-Filters");
    protected PluginParameter<Segmenter> segmenter = new PluginParameter<Segmenter>("Segmentation algorithm", Segmenter.class, false);
    protected Parameter[] parameters= new Parameter[]{preFilters, segmenter, postFilters, tracker};
    public SegmentThenTrack() {}
    public SegmentThenTrack(Segmenter segmenter, Tracker tracker) {
        this.segmenter.setPlugin(segmenter);
        this.tracker.setPlugin(tracker);
    }
    public SegmentThenTrack addPreFilters(PreFilter... preFilter) {
        preFilters.addPreFilters(preFilter);
        return this;
    }
    public SegmentThenTrack addPostFilters(PostFilter... postFilter) {
        postFilters.addPostFilters(postFilter);
        return this;
    }
    public Segmenter getSegmenter() {return segmenter.instanciatePlugin();}
    public Tracker getTracker() {return tracker.instanciatePlugin();}

    public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        if (!segmenter.isOnePluginSet()) {
            logger.info("No segmenter set for structure: {}", structureIdx);
            return;
        }
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        for (StructureObject parent : parentTrack) segment(parent, structureIdx);
        Tracker t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
    }
    
    private void segment(StructureObject parent, int structureIdx) {
        Segmenter s = segmenter.instanciatePlugin();
        Image input = preFilters.filter(parent.getRawImage(structureIdx), parent);
        ObjectPopulation pop = s.runSegmenter(input, structureIdx, parent);
        pop = postFilters.filter(pop, structureIdx, parent);
        parent.setChildrenObjects(pop, structureIdx);
    }

    public void trackOnly(final int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        Tracker t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}
