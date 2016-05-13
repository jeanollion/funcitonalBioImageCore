package utils.clustering;

import dataStructure.objects.Object3D;

/**
 *
 **
 * /**
 * Copyright (C) 2012 Jean Ollion
 *
 *
 *
 * This file is part of tango
 *
 * tango is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Jean Ollion
 */

public class LabelPair {
    int r1, r2;
    public LabelPair(Object3D r1, Object3D r2) {
        if (r1.getLabel()<r2.getLabel()) {
            this.r1=r1.getLabel();
            this.r2=r2.getLabel();
        } else {
            this.r2=r1.getLabel();
            this.r1=r2.getLabel();
        }
    }
    public LabelPair(int r1, int r2) {
        if (r1<r2) {
            this.r1=r1;
            this.r2=r2;
        } else {
            this.r2=r1;
            this.r1=r2;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof LabelPair) {
            return ((LabelPair)o).r1==r1 && ((LabelPair)o).r2==r2;
        } else if (o instanceof Interface && ((Interface)o).e1 instanceof Object3D) {
            return (r1 == ((Interface<Object3D, ?>)o).e1.getLabel() && r2 == ((Interface<Object3D, ?>)o).e2.getLabel());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + this.r1;
        hash = 97 * hash + this.r2;
        return hash;
    }
    
    @Override 
    public String toString() {
        return "Region pair:"+r1+ "+"+r2;
    }
   
}