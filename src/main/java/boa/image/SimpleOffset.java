/*
 * Copyright (C) 2018 jollion
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
package boa.image;

/**
 *
 * @author jollion
 */
public class SimpleOffset implements Offset<SimpleOffset> {
    int xMin, yMin, zMin;

    public SimpleOffset(int xMin, int yMin, int zMin) {
        this.xMin = xMin;
        this.yMin = yMin;
        this.zMin = zMin;
    }
    public SimpleOffset(Offset other) {
        this(other.xMin(), other.yMin(), other.zMin());
    }
    
    @Override
    public int xMin() {
        return xMin;
    }

    @Override
    public int yMin() {
        return yMin;
    }

    @Override
    public int zMin() {
        return zMin;
    }

    @Override
    public SimpleOffset resetOffset() {
        xMin=0;
        yMin=0;
        zMin=0;
        return this;
    }

    @Override
    public SimpleOffset reverseOffset() {
        xMin=-xMin;
        yMin=-yMin;
        zMin=-zMin;
        return this;
    }

    @Override
    public SimpleOffset translate(Offset other) {
        xMin+=other.xMin();
        yMin+=other.yMin();
        zMin+=other.zMin();
        return this;
    }
    
}
