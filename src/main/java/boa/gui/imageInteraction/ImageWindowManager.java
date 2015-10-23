/*
 * Copyright (C) 2015 nasique
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
package boa.gui.imageInteraction;

import static boa.gui.GUI.logger;
import dataStructure.objects.StructureObject;
import ij.ImagePlus;
import image.Image;
import image.ImageInteger;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

/**
 *
 * @author jollion
 */
public abstract class ImageWindowManager<T> {
    public final static Color[] palette = new Color[]{new Color(166, 206, 227), new Color(31,120,180), new Color(178,223,138), new Color(51,160,44), new Color(251,154,153), new Color(253,191,111), new Color(255,127,0), new Color(255,255,153), new Color(177,89,40)};
    protected final static Color trackErrorColor = new Color(255, 0, 0);
    protected final static Color trackCorrectionColor = new Color(0, 0, 255);
    final static int trackArrowStrokeWidth = 3;
    private final HashMap<ImageObjectInterfaceKey, ImageObjectInterface> imageObjectInterfaces;
    private final HashMap<Image, ImageObjectInterface> imageObjectInterfaceMap;
    final ImageObjectListener listener;
    final ImageDisplayer<T> displayer;
    
    public ImageWindowManager(ImageObjectListener listener, ImageDisplayer displayer) {
        this.listener=listener;
        this.displayer=displayer;
        imageObjectInterfaceMap = new HashMap<Image, ImageObjectInterface>();
        imageObjectInterfaces = new HashMap<ImageObjectInterfaceKey, ImageObjectInterface>();
    }
    
    public ImageDisplayer getDisplayer() {return displayer;}
    
    //protected abstract T getImage(Image image);
    
    public void addImage(Image image, ImageObjectInterface i, boolean displayImage) {
        //ImageObjectInterface i = getImageObjectInterface(parent, childStructureIdx, timeImage);
        if (!imageObjectInterfaces.containsValue(i)) throw new RuntimeException("image object interface should be created through the manager");
        //T im = getImage(image);
        imageObjectInterfaceMap.put(image, i);
        if (displayImage) {
            displayer.showImage(image);
            addMouseListener(image);
        }
    }
    
    public void resetImageObjectInterface(StructureObject parent, int childStructureIdx) {
        imageObjectInterfaces.remove(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
    }
    
    public ImageObjectInterface getImageObjectInterface(StructureObject parent, int childStructureIdx) {
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parent, childStructureIdx, false));
        if (i==null) {
            i= new StructureObjectMask(parent, childStructureIdx);
            imageObjectInterfaces.put(new ImageObjectInterfaceKey(parent, childStructureIdx, false), i);
        } 
        return i;
    }
    
    public ImageObjectInterface getImageTrackObjectInterface(ArrayList<StructureObject> parentTrack, int childStructureIdx) {
        if (parentTrack.isEmpty()) {
            logger.warn("cannot open track image of length == 0" );
            return null;
        }
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parentTrack.get(0), childStructureIdx, true));
        if (i==null) {
            i = new TrackMask(parentTrack, childStructureIdx);
            imageObjectInterfaces.put(new ImageObjectInterfaceKey(parentTrack.get(0), childStructureIdx, true), i);
        } 
        return i;
    }
    
    protected void reloadObjects(StructureObject parent, int childStructureIdx, boolean track) {
        if (track) parent=parent.getTrackHead();
        ImageObjectInterface i = imageObjectInterfaces.get(new ImageObjectInterfaceKey(parent, childStructureIdx, track));
        if (i!=null) {
            i.reloadObjects();
            for (Entry<Image, ImageObjectInterface> e : imageObjectInterfaceMap.entrySet()) if (e.getValue().equals(i)) {
                i.draw((ImageInteger)e.getKey());
                if (!track) getDisplayer().updateImageDisplay(e.getKey());
            }
        }
    }
    
    public void reloadObjects(StructureObject parent, int childStructureIdx) {
        reloadObjects(parent, childStructureIdx, true);
        reloadObjects(parent, childStructureIdx, false);
    } 
    
    protected ImageObjectInterface getImageObjectInterface(Image image) {return imageObjectInterfaceMap.get(image);}
    
    public void removeImage(Image image) {
        imageObjectInterfaceMap.remove(image);
        //removeClickListener(image);
    }
    
    public abstract void addMouseListener(Image image);
    
    //public abstract void removeClickListener(Image image);
    
    public StructureObject getClickedObject(Image image, int x, int y, int z) {
        ImageObjectInterface i = imageObjectInterfaceMap.get(image);
        if (i!=null) {
            return i.getClickedObject(x, y, z);
        } else logger.warn("image: {} is not registered for click");
        return null;
    }
    
    public abstract void selectObjects(Image image, boolean addToCurrentSelection, StructureObject... selectedObjects);
    public abstract void unselectObjects(Image image);
    public abstract void displayTrack(Image image, boolean addToCurrentSelectedTracks, ArrayList<StructureObject> track, Color color);
}
