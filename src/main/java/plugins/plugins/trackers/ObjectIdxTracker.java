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
package plugins.plugins.trackers;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.StructureObjectTracker;
import dataStructure.objects.Track;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import plugins.Plugin;
import plugins.Tracker;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ObjectIdxTracker implements Tracker {
    public static enum IndexingOrder {XYZ(0, 1, 2), YXZ(1, 0, 2), XZY(0, 2, 1), ZXY(2, 0, 1), ZYX(2, 1, 0);
        public final int i1, i2, i3;
        IndexingOrder(int i1, int i2, int i3) {
            this.i1=i1;
            this.i2=i2;
            this.i3=i3;
        }
    };
    ChoiceParameter order = new ChoiceParameter("Indexing order", Utils.toStringArray(IndexingOrder.values()), IndexingOrder.XYZ.toString(), false);
    
    public void assignPrevious(ArrayList<? extends StructureObjectTracker> previous, ArrayList<? extends StructureObjectTracker> next) {
        /*StructureObjectTracker[] previousCopy = new StructureObjectTracker[previous.length];
        System.arraycopy(previous, 0, previousCopy, 0, previous.length);
        StructureObjectTracker[] nextCopy = new StructureObjectTracker[next.length];
        System.arraycopy(next, 0, nextCopy, 0, next.length);
        */
        
        Collections.sort(previous, getComparator(IndexingOrder.valueOf(order.getSelectedItem())));
        Collections.sort(next, getComparator(IndexingOrder.valueOf(order.getSelectedItem())));
        for (int i = 0; i<Math.min(previous.size(), next.size()); ++i) {
            next.get(i).setPreviousInTrack(previous.get(i), false);
            Plugin.logger.trace("assign previous {} to next {}", previous.get(i), next.get(i));
        }
    }
    
    public static Comparator<? super StructureObjectPreProcessing> getComparator(final IndexingOrder order) {
        return new Comparator<StructureObjectPreProcessing>() {
            @Override
            public int compare(StructureObjectPreProcessing arg0, StructureObjectPreProcessing arg1) {
                return compareCenters(getCenterArray(arg0.getMask()), getCenterArray(arg1.getMask()), order);
            }
        };
    }
    
    private static double[] getCenterArray(ImageMask m) {
        return new double[]{m.getOffsetX()+m.getSizeX()/2d, m.getOffsetY()+m.getSizeY()/2d, m.getOffsetZ()+m.getSizeZ()/2d};
    }
    
    public static int compareCenters(double[] o1, double[] o2, IndexingOrder order) {
        if (o1[order.i1]!=o2[order.i1]) return Double.compare(o1[order.i1], o2[order.i1]);
        else if (o1[order.i2]!=o2[order.i2]) return Double.compare(o1[order.i2], o2[order.i2]);
        else return Double.compare(o1[order.i3], o2[order.i3]);
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }
    
}