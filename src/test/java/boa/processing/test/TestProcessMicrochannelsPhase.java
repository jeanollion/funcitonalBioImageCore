/*
 * Copyright (C) 2016 jollion
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
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.core.Task;
import boa.configuration.experiment.MicroscopyField;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.TrackPostFilterSequence;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import ij.ImageJ;
import boa.image.Image;
import java.util.ArrayList;
import java.util.Arrays;
import boa.plugins.PluginFactory;
import boa.plugins.ProcessingScheme;
import boa.plugins.ProcessingSchemeWithTracking;
import boa.plugins.Segmenter;
import boa.plugins.TrackPostFilter;
import boa.plugins.plugins.post_filters.FitMicrochannelHeadToEdges;
import boa.plugins.plugins.segmenters.MicrochannelPhase2D;
import boa.plugins.plugins.track_post_filter.TrackLengthFilter;

/**
 *
 * @author jollion
 */
public class TestProcessMicrochannelsPhase {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        int time =0;
        int field = 0;
        //String dbName = "TestThomasRawStacks";
        String dbName = "MutH_150324";
        testSegMicrochannelsFromXP(dbName, field, time);
        //testPostProcessTracking(dbName, field, time);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO =new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image input = root.getRawImage(0);
        MicrochannelPhase2D.debug=true;
        //MicroChannelPhase2D seg = new MicroChannelPhase2D().setyStartAdjustWindow(5);
        Segmenter s = mDAO.getExperiment().getStructure(0).getProcessingScheme().getSegmenter();
        input = mDAO.getExperiment().getStructure(0).getProcessingScheme().getPreFilters().filter(input, root.getMask());
        RegionPopulation pop = s.runSegmenter(input, 0, root);
        FitMicrochannelHeadToEdges.debug=true;
        if (mDAO.getExperiment().getStructure(0).getProcessingScheme() instanceof ProcessingSchemeWithTracking) {
            TrackPostFilterSequence tpf = ((ProcessingSchemeWithTracking)mDAO.getExperiment().getStructure(0).getProcessingScheme()).getTrackPostFilters();
            for (PluginParameter<TrackPostFilter> pp : tpf.getChildren()) if (pp.instanciatePlugin() instanceof TrackLengthFilter) pp.setActivated(false);
            tpf.filter(0, Arrays.asList(new StructureObject[]{root}), null);
            pop = root.getObjectPopulation(0);
        }
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(pop.getLabelMap());
        logger.debug("{} objects found", pop.getRegions().size());
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
    public static void testPostProcessTracking(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO =new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image input = root.getRawImage(0);
        ImageWindowManagerFactory.showImage(input);
        RegionPopulation pop = root.getObjectPopulation(0);
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        FitMicrochannelHeadToEdges.debug=true;
        if (mDAO.getExperiment().getStructure(0).getProcessingScheme() instanceof ProcessingSchemeWithTracking) {
            ((ProcessingSchemeWithTracking)mDAO.getExperiment().getStructure(0).getProcessingScheme()).getTrackPostFilters().filter(0, Arrays.asList(new StructureObject[]{root}), null);
            pop = root.getObjectPopulation(0);
        } else return;
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        logger.debug("{} objects found", pop.getRegions().size());
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
}