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
package boa.image.processing.clustering;

import boa.data_structure.Region;

/**
 *
 * @author jollion
 */
public class SimpleInterfaceVoxelSet extends InterfaceVoxelSet<SimpleInterfaceVoxelSet> {

    public SimpleInterfaceVoxelSet(Region e1, Region e2) {
        super(e1, e2);
    }

    @Override
    public boolean checkFusion() {
        return false;
    }

    @Override
    public void updateInterface() {
        
    }

    @Override
    public int compareTo(SimpleInterfaceVoxelSet o) {
        return 0;
    }
    
}
