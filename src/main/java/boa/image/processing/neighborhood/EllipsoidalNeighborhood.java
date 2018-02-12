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
package boa.image.processing.neighborhood;

import static boa.core.Processor.logger;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author jollion
 */
public class EllipsoidalNeighborhood extends DisplacementNeighborhood {
    double radius;
    double radiusZ;

    /**
     * 3D Elipsoidal Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * @param radiusZ in pixel in the Z-axis
     * @param excludeCenter if true, central point can excluded
     * return an array of diplacement from the center
     */
    public EllipsoidalNeighborhood(double radius, double radiusZ, boolean excludeCenter) {
        if (radiusZ<=0) init2D(radius, excludeCenter);
        else {
            this.radius=radius;
            this.radiusZ=radiusZ;
            is3D=radiusZ>0;
            double r = radiusZ>0 ? (double) radius / radiusZ : 0;
            int rad = (int) (radius + 0.5f);
            int radZ = (int) (radiusZ + 0.5f);
            List<double[]> coordsXYZD = new ArrayList<>();
            double rad2 = radius * radius;
            for (int zz = -radZ; zz <= radZ; zz++) {
                for (int yy = -rad; yy <= rad; yy++) {
                    for (int xx = -rad; xx <= rad; xx++) {
                        double d2 = zz * r * zz * r + yy * yy + xx * xx;
                        if (d2 <= rad2 && (!excludeCenter || d2 > 0)) {	//exclusion du point central
                            double[] coords = new double[4];
                            coordsXYZD.add(coords);
                            coords[0] = xx;
                            coords[1]= yy;
                            coords[2]= zz;
                            coords[3] = Math.sqrt(d2);
                        }
                    }
                }
            }
            Collections.sort(coordsXYZD, (a1, a2)->Double.compare(a1[3], a2[3]));
            distances = new float[coordsXYZD.size()];
            dx= new int[coordsXYZD.size()];
            dy= new int[coordsXYZD.size()];
            dz= new int[coordsXYZD.size()];
            values=new float[coordsXYZD.size()];
            for (int i = 0; i<coordsXYZD.size(); ++i) {
                double[] c = coordsXYZD.get(i);
                dx[i] = (int)c[0];
                dy[i] = (int)c[1];
                dz[i] = (int)c[2];
                distances[i] = (float)c[3];
            }
        }
    }
    /**
     * 2D Circular Neighbourhood around a voxel
     * @param radius in pixel in the XY-axis
     * an array of diplacement from the center. 
     * @param excludeCenter if true, central point can excluded
     */
    public EllipsoidalNeighborhood(double radius, boolean excludeCenter) { 
        init2D(radius, excludeCenter);
    }
    private void init2D(double radius, boolean excludeCenter) {
        this.radius = radius;
        this.radiusZ=0;
        is3D=false;
        int rad = (int) (radius + 0.5f);
        List<double[]> coordsXYD = new ArrayList<>();
        double rad2 = radius * radius;
        for (int yy = -rad; yy <= rad; yy++) {
            for (int xx = -rad; xx <= rad; xx++) {
                float d2 = yy * yy + xx * xx;
                if (d2 <= rad2 && (!excludeCenter || d2 > 0)) {	//exclusion du point central
                    double[] coords = new double[3];
                    coordsXYD.add(coords);
                    coords[0] = xx;
                    coords[1]= yy;
                    coords[2]= Math.sqrt(d2);
                }
            }
        }
        Collections.sort(coordsXYD, (a1, a2)->Double.compare(a1[2], a2[2]));
        distances = new float[coordsXYD.size()];
        dx= new int[coordsXYD.size()];
        dy= new int[coordsXYD.size()];
        dz= new int[coordsXYD.size()];
        values=new float[coordsXYD.size()];
        for (int i = 0; i<coordsXYD.size(); ++i) {
            double[] c = coordsXYD.get(i);
            dx[i] = (int)c[0];
            dy[i] = (int)c[1];
            distances[i] = (float)c[2];
        }
    }

    public double getRadiusXY() {
        return radius;
    }

    public double getRadiusZ() {
        return radiusZ;
    }
    
    @Override public BoundingBox getBoundingBox() {
        int r = (int) radius;
        int rZ = (int) radiusZ;
        return new BoundingBox(-r, r, -r, r, -rZ, rZ);
    }
    
    @Override public String toString() {
        if (this.is3D) {
            String res = "Neighborhood3D: radiusXY:"+radius+ "radiusZ: "+this.radiusZ+" [";
            for (int i = 0; i<dx.length; ++i) res+="dx:"+dx[i]+",dy:"+dy[i]+",dz:"+dz[i]+";";
            return res+"]";
        } else {
            String res = "Neighborhood2D: radius:"+radius+ " [";
            for (int i = 0; i<dx.length; ++i) res+="dx:"+dx[i]+",dy:"+dy[i]+";";
            return res+"]";
        }
        
    }
    
    public void addVoxels(Voxel v, ImageProperties p, ArrayList<Voxel> res) {
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=v.x+dx[i];
                yy=v.y+dy[i];
                zz=v.z+dz[i];
                if (p.contains(xx, yy, zz)) res.add(new Voxel(xx, yy, zz));
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=v.x+dx[i];
                yy=v.y+dy[i];
                if (p.contains(xx, yy, v.z)) res.add(new Voxel(xx, yy, v.z));
            }
        }
    }

    public boolean is3D() {
        return is3D;
    }
    
    public ImageByte drawNeighborhood(ImageByte output) {
        int centerXY, centerZ;
        if (output == null) {
            int radXY = (int)(this.radius+0.5);
            int radZ = (int)(this.radiusZ+0.5);
            centerXY=radXY;
            centerZ=radZ;
            if (is3D) output = new ImageByte("3D EllipsoidalNeighborhood: XY:"+this.radius+" Z:"+this.radiusZ, radXY*2+1, radXY*2+1, radZ*2+1);
            else output = new ImageByte("2D EllipsoidalNeighborhood: XY:"+this.radius, radXY*2+1, radXY*2+1, 1);
        } else {
            centerXY = output.getSizeX()/2+1;
            centerZ = output.getSizeZ()/2+1;
        }
        if (is3D) for (int i = 0; i<this.dx.length;++i) output.setPixel(centerXY+dx[i], centerXY+dy[i], centerZ+dz[i], 1);
        else for (int i = 0; i<this.dx.length;++i) output.setPixel(centerXY+dx[i], centerXY+dy[i], 0, 1);
        return output;
    }

    
    
}