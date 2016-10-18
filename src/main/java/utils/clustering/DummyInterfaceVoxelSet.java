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
package utils.clustering;

import dataStructure.objects.Object3D;

/**
 *
 * @author jollion
 */
public class DummyInterfaceVoxelSet extends InterfaceVoxelSet<DummyInterfaceVoxelSet> {

    public DummyInterfaceVoxelSet(Object3D e1, Object3D e2) {
        super(e1, e2);
    }

    @Override
    public boolean checkFusion() {
        return false;
    }

    @Override
    public void updateSortValue() {
        
    }

    @Override
    public int compareTo(DummyInterfaceVoxelSet o) {
        return 0;
    }
    
}