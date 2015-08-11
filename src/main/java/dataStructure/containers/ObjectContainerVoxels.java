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
package dataStructure.containers;

import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import dataStructure.objects.Voxel2D;
import dataStructure.objects.Voxel3D;
import de.caluga.morphium.annotations.Embedded;
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
@Embedded(polymorph=true)
public class ObjectContainerVoxels extends ObjectContainer {
    int[] x, y, z;
    int label;

    public ObjectContainerVoxels(Object3D object) {
        super(object.getBounds(), object.getScaleXY(), object.getScaleZ());
        createCoordsArrays(object);
        label=object.getLabel();
    }
    
    public void updateObject(Object3D object) {
        createCoordsArrays(object);
        bounds=object.getBounds();
        label = object.getLabel();
    }
    
    private void createCoordsArrays(Object3D object) {
        if (object.is3D()) {
            ArrayList<Voxel3D> voxels = object.getVoxels();
            x = new int[voxels.size()];
            y = new int[voxels.size()];
            z = new int[voxels.size()];
            int idx = 0;
            for (Voxel3D v : voxels) {
                x[idx]=v.x;
                y[idx]=v.y;
                z[idx++]=v.z;
            }
        } else {
            ArrayList<Voxel2D> voxels = object.getVoxels();
            x = new int[voxels.size()];
            y = new int[voxels.size()];
            z = null;
            int idx = 0;
            for (Voxel2D v : voxels) {
                x[idx]=v.x;
                y[idx++]=v.y;
            }
        }
    }
    
    private ArrayList<? extends Voxel> getVoxels() {
        if (x==null || y==null) return new ArrayList(0);
        if (z!=null) {
            ArrayList<Voxel3D> voxels = new ArrayList<Voxel3D>(x.length);
            for (int i  = 0; i<x.length; ++i) voxels.add(new Voxel3D(x[i], y[i], z[i]));
            return voxels;
        } else {
            ArrayList<Voxel2D> voxels = new ArrayList<Voxel2D>(x.length);
            for (int i  = 0; i<x.length; ++i) voxels.add(new Voxel2D(x[i], y[i]));
            return voxels;
        }
    }
    
    public Object3D getObject() {
        return new Object3D(getVoxels(), label, scaleXY, scaleZ, bounds);
    }
    
}
