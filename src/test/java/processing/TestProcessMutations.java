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
package processing;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.objects.DBConfiguration;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import static image.ImageOperations.pasteImage;
import java.util.ArrayList;
import java.util.HashSet;
import plugins.PluginFactory;
import plugins.plugins.segmenters.BacteriaFluo;
import plugins.plugins.segmenters.SpotFluo2D5;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class TestProcessMutations {
    DBConfiguration db;
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        TestProcessMutations t = new TestProcessMutations();
        t.init();
        t.testSegMutationsFromXP(true, 0, 20);
    }
    public void init() {
        String dbName = "testFluo60";
        db = new DBConfiguration(dbName);
        logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
    }
    public void testSegMutationsFromXP(int time) {
        testSegMutationsFromXP(true, time, null, null, null, null, null);
    }
    public void testSegMutationsFromXP(boolean parentMC, int time, ArrayList<ImageMask> mcMask_, ArrayList<ImageMask> parentMask_, ArrayList<Image> input_,  ArrayList<ImageInteger> outputLabel, ArrayList<ArrayList<Image>> intermediateImages_) {
        int field = 1;
        int channel = 0;
        //String dbName = "testFluo";
        
        MicroscopyField f = db.getExperiment().getMicroscopyField(field);
        StructureObject root = db.getDao().getRoot(f.getName(), time);
        //logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        StructureObject mc = root.getChildObjects(0, db.getDao(), false).get(channel);
        mcMask_.add(mc.getMask());
        if (parentMC) {
            testSegMutation(mc, parentMask_, input_, outputLabel, intermediateImages_);
        } else {
            for (StructureObject bact : mc.getChildObjects(1, db.getDao(), false)) {
                testSegMutation(bact, parentMask_, input_, outputLabel, intermediateImages_);
            }
        }
    }
    
    public void testSegMutation(StructureObject parent, ArrayList<ImageMask> parentMask_, ArrayList<Image> input_,  ArrayList<ImageInteger> outputLabel, ArrayList<ArrayList<Image>> intermediateImages_) {
            Image input = parent.getRawImage(2);
            ImageMask parentMask = parent.getMask();
            SpotFluo2D5.debug=true;
            SpotFluo2D5.displayImages=parentMask_==null;
            ArrayList<Image> intermediateImages = intermediateImages_==null? null:new ArrayList<Image>();
            ObjectPopulation pop = SpotFluo2D5.run(input, parentMask, 2, 3, 5, 1.5, 4, intermediateImages);
            /*ImageDisplayer disp = new IJImageDisplayer();
            disp.showImage(input);
            disp.showImage(pop.getLabelImage());:*/
            if (parentMask!=null) parentMask_.add(parentMask);
            if (input_!=null) input_.add(input);
            if (outputLabel!=null) outputLabel.add(pop.getLabelImage());
            if (intermediateImages_!=null) intermediateImages_.add(intermediateImages);
    }
    
    static int intervalX = 5;
    public void testSegMutationsFromXP(boolean parentMC, int tStart, int tEnd) {
        ArrayList<ImageMask> mcMask = new ArrayList<ImageMask>();
        ArrayList<ImageMask> parentMask = new ArrayList<ImageMask>();
        ArrayList<Image> input = new ArrayList<Image>();
        ArrayList<ImageInteger> outputLabel = new ArrayList<ImageInteger>(); 
        ArrayList<ArrayList<Image>> intermediateImages = new ArrayList<ArrayList<Image>>();
        
        for (int t = tStart; t<tEnd; ++t) {
            logger.debug("SEG MUT: time: {}", t);
            testSegMutationsFromXP(parentMC, t, mcMask, parentMask, input, outputLabel, intermediateImages);
        }
        
        int xSize = 0;
        int ySize = 0;
        HashSet<ImageMask> mcMaskUnique = new HashSet<ImageMask>();
        mcMaskUnique.addAll(mcMask);
        logger.debug("number of bacteries: {}, number of distinct microchannels: {}", parentMask.size(), mcMaskUnique.size());
        for (ImageMask m : mcMaskUnique) {
            if (ySize<m.getSizeY()) ySize = m.getSizeY();
            xSize+=intervalX+m.getSizeX();
        }
        xSize-=intervalX;
        Image inputPaste = Image.createEmptyImage("input", input.get(0), new BlankMask("", xSize, ySize, input.get(0).getSizeZ(), 0, 0, 0, 1, 1));
        Image outputLabelPaste = Image.createEmptyImage("labels", outputLabel.get(0), new BlankMask("", xSize, ySize, outputLabel.get(0).getSizeZ(), 0, 0, 0, 1, 1));
        ArrayList<Image> intermediateImagesList = new ArrayList<Image>();
        for (int i = 0; i<intermediateImages.get(0).size(); ++i) intermediateImagesList.add(Image.createEmptyImage(intermediateImages.get(0).get(i).getName(), intermediateImages.get(0).get(i), new BlankMask("", xSize, ySize, intermediateImages.get(0).get(i).getSizeZ(), 0, 0, 0, 1, 1)));
        BoundingBox offset = new BoundingBox(0, 0, 0);
        ImageMask lastMask = mcMask.get(0);
        for (int i = 0; i<parentMask.size(); ++i) {
            if (!mcMask.get(i).equals(lastMask)) offset.translate(mcMask.get(i).getSizeX()+intervalX , 0, 0);
            BoundingBox localOffset= parentMC? offset : parentMask.get(i).getBoundingBox().translate(offset.getxMin(), 0, 0);
            pasteImage(input.get(i), inputPaste, localOffset);
            pasteImage(outputLabel.get(i), outputLabelPaste, localOffset);
            for (int interIdx = 0; interIdx<intermediateImages.get(i).size(); ++interIdx) pasteImage(intermediateImages.get(i).get(interIdx), intermediateImagesList.get(interIdx), localOffset);
            
            lastMask = mcMask.get(i);
            
        }
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(inputPaste);
        for (Image i : intermediateImagesList) disp.showImage(i);
        disp.showImage(outputLabelPaste);
        
    }
}
