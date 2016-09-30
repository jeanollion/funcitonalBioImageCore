package image;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.neighborhood.Neighborhood;


public abstract class Image implements ImageProperties {
    public final static Logger logger = LoggerFactory.getLogger(Image.class);

    protected String name;
    protected int sizeX;
    protected int sizeY;
    protected int sizeZ;
    protected int sizeXY;
    protected int sizeXYZ;
    protected int offsetX;
    protected int offsetY;
    protected int offsetZ;
    protected int offsetXY;
    protected float scaleXY;
    protected float scaleZ;
    protected LUT lut=LUT.Grays;
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ=sizeZ>=1?sizeZ:1;
        this.sizeXY=sizeX*sizeY;
        this.sizeXYZ=sizeXY*sizeZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetXY= offsetY * sizeX + offsetX;
        this.offsetZ = offsetZ;
        this.scaleXY = scaleXY;
        this.scaleZ = scaleZ;
    }
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    protected Image(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    public <T extends Image> T setName(String name) {
        this.name=name;
        return (T)this;
    }
    
    public ImageProperties getProperties() {return new BlankMask(name, this);}
    
    public static <T> T createEmptyImage(String name, T imageType, ImageProperties properties) {
        if (imageType instanceof ImageByte) return (T)new ImageByte(name, properties);
        else if (imageType instanceof ImageShort) return (T)new ImageShort(name, properties);
        else if (imageType instanceof ImageInt) return (T)new ImageInt(name, properties);
        else if (imageType instanceof ImageFloat) return (T)new ImageFloat(name, properties);
        else return (T)new BlankMask(name, properties);
    }
    
    public abstract <T extends Image> T getZPlane(int idxZ);
    
    /**
     * 
     * @param <T> type of Image
     * @param zLimit array containing minimal Z plane idx (included) and maximal Z-plane idx (included).
     * @return List of Z planes
     */
    public <T extends Image> ArrayList<T> splitZPlanes(int... zLimit) {
        int zMin = 0;
        int zMax = this.getSizeZ()-1;
        if (zLimit.length>0) zMin = Math.max(zMin, zLimit[0]);
        if (zLimit.length>1) zMax = Math.min(zMax, zLimit[1]);
        ArrayList<T> res = new ArrayList<T>(getSizeZ());
        for (int i = zMin; i<=zMax; ++i) res.add((T)getZPlane(i));
        return res;
    }
    
    public static <T extends Image> T mergeZPlanes(List<T> planes) {
        if (planes==null || planes.isEmpty()) return null;
        String title = "merged planes";
        T plane0 = planes.get(0);
        if (plane0 instanceof ImageByte) {
            byte[][] pixels = new byte[planes.size()][];
            for (int i = 0; i<pixels.length; ++i) pixels[i]=((byte[][])planes.get(i).getPixelArray())[0];
            return (T)new ImageByte(title, plane0.getSizeX(), pixels).setCalibration(plane0).addOffset(plane0);
        } else if (plane0 instanceof ImageShort) {
            short[][] pixels = new short[planes.size()][];
            for (int i = 0; i<pixels.length; ++i) pixels[i]=((short[][])planes.get(i).getPixelArray())[0];
            return (T)new ImageShort(title, plane0.getSizeX(), pixels).setCalibration(plane0).addOffset(plane0);
        } else if (plane0 instanceof ImageFloat) {
            float[][] pixels = new float[planes.size()][];
            for (int i = 0; i<pixels.length; ++i) pixels[i]=((float[][])planes.get(i).getPixelArray())[0];
            return (T)new ImageFloat(title, plane0.getSizeX(), pixels).setCalibration(plane0).addOffset(plane0);
        } else if (plane0 instanceof ImageInt) {
            int[][] pixels = new int[planes.size()][];
            for (int i = 0; i<pixels.length; ++i) pixels[i]=((int[][])planes.get(i).getPixelArray())[0];
            return (T)new ImageInt(title, plane0.getSizeX(), pixels).setCalibration(plane0).addOffset(plane0);
        } else {
            logger.error("merge plane Z: unrecognized image type");
            return null;
        }
    }
    
    @Override
    public boolean sameSize(ImageProperties other) {
        return sizeX==other.getSizeX() && sizeY==other.getSizeY() && sizeZ==other.getSizeZ();
    }
    
    //public abstract float getPixel(float x, float y, float z); // interpolation
    public abstract float getPixel(int x, int y, int z);
    public abstract float getPixelWithOffset(int x, int y, int z);
    public abstract float getPixelLinInterX(int x, int y, int z, float dx);
    public float getPixelLinInterXY(int x, int y, int z, float dx, float dy) {
        return getPixelLinInterX(x, y, z, dx) * (1 - dy) + dy * getPixelLinInterX(x, y+1, z, dx);
    }
    public float getPixelLinInter(int x, int y, int z, float dx, float dy, float dz) {
        if (this.getSizeZ()<=1 || dz==0) return getPixelLinInterXY(x, y, z, dx, dy);
        return getPixelLinInterXY(x, y, z, dx, dy) * (1 - dz) + dz * getPixelLinInterXY(x, y, z+1, dx, dy);
    }
    public float getPixel(double x, double y, double z) {
        //return getPixel((int)x, (int)y, (int)z);
        return getPixelLinInter((int)x, (int)y, (int)z, (float)(x-(int)x), (float)(y-(int)y), (float)(z-(int)z));
    }
    public float getPixelWithOffset(double x, double y, double z) {
        //return getPixel((int)x, (int)y, (int)z);
        return getPixelLinInter((int)x-offsetX, (int)y-offsetY, (int)z-offsetZ, (float)(x-(int)x), (float)(y-(int)y), (float)(z-(int)z));
    }
    public abstract float getPixel(int xz, int z);
    public abstract float getPixelWithOffset(int xy, int z);
    public abstract void setPixel(int x, int y, int z, double value);
    public abstract void setPixelWithOffset(int x, int y, int z, double value);
    public abstract void setPixel(int xy, int z, double value);
    public abstract void setPixelWithOffset(int xy, int z, double value);
    public abstract Object[] getPixelArray();
    public abstract <T extends Image> T duplicate(String name);
    public <T extends Image> T duplicate() {return duplicate(name);}
    public abstract Image newImage(String name, ImageProperties properties);
    public abstract <T extends Image> T crop(BoundingBox bounds);
    /**
     * 
     * @param <T> image type
     * @param extent minimal values: if negative, will extend the image, if positive will crop the image. maximal values: if positive will extend the image, if negative will crop the image
     * @return 
     */
    public <T extends Image> T extend(BoundingBox extent) {
        BoundingBox resizeBB = new BoundingBox(extent.getxMin(), getSizeX()-1+extent.getxMax(), extent.getyMin(), getSizeY()-1+extent.getyMax(), extent.getzMin(), getSizeZ()-1+extent.getzMax());
        return crop(resizeBB);
    }
    public abstract void invert();
    
    @Override
    public boolean contains(int x, int y, int z) {
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        x-=offsetX; y-=offsetY; z-=offsetZ;
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }
    
    public <T extends Image> T resetOffset() {
        offsetX=offsetY=offsetZ=offsetXY=0;
        return (T)this;
    }
    
    public <T extends Image> T addOffset(ImageProperties properties) {
        this.offsetX+=properties.getOffsetX();
        this.offsetY+=properties.getOffsetY();
        this.offsetZ+=properties.getOffsetZ();
        this.offsetXY = offsetX + sizeX * offsetY;
        return (T)this;
    }
    
    public <T extends Image> T addOffset(int offsetX, int offsetY, int offsetZ) {
        this.offsetX+=offsetX;
        this.offsetY+=offsetY;
        this.offsetZ+=offsetZ;
        this.offsetXY = this.offsetX + sizeX * this.offsetY;
        return (T)this;
    }
    
    public <T extends Image> T addOffset(BoundingBox bounds) {
        this.offsetX+=bounds.xMin;
        this.offsetY+=bounds.yMin;
        this.offsetZ+=bounds.zMin;
        this.offsetXY = offsetX + sizeX * offsetY;
        return (T)this;
    }

    public <T extends Image> T setCalibration(ImageProperties properties) {
        this.scaleXY=properties.getScaleXY();
        this.scaleZ=properties.getScaleZ();
        return (T)this;
    }
    
    public <T extends Image> T setCalibration(float scaleXY, float scaleZ) {
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
        return (T)this;
    }
    
    public BoundingBox getBoundingBox() {
        BoundingBox res = new BoundingBox(0, sizeX-1, 0, sizeY-1, 0, sizeZ-1);
        res.translate(offsetX, offsetY, offsetZ);
        return res;
    }
    public double[] getMinAndMax(ImageMask mask) {
        return getMinAndMax(mask, null);
    }
    /**
     * 
     * @param mask min and max are computed within the mask, or within the whole image if mask==null 
     * @return float[]{min, max}
     */
    public double[] getMinAndMax(ImageMask mask, BoundingBox limits) {
        if (mask==null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int z = limits.zMin; z <= limits.zMax; z++) {
            for (int y = limits.yMin; y<=limits.yMax; ++y) {
                for (int x = limits.xMin; x <= limits.xMax; ++x) {
                    if (mask.insideMask(x, y, z)) {
                        if (getPixel(x, y, z) > max) {
                            max = getPixel(x, y, z);
                        }
                        if (getPixel(x, y, z) < min) {
                            min = getPixel(x, y, z);
                        }
                    }
                }
            }
        }
        return new double[]{min, max};
    }
    public abstract int[] getHisto256(ImageMask mask, BoundingBox bounds);
    public int[] getHisto256(ImageMask mask) {return getHisto256(mask, null);}
    abstract int[] getHisto256(double min, double max, ImageMask mask, BoundingBox limit);
    /**
     * 
     * @param images
     * @param minAndMax the method will output min and max values in this array, except if minAndMax[0]<minAndMax[1] -> in this case will use these values for histogram
     * @return 
     */
    public static int[] getHisto256(List<Image> images, double[] minAndMax) {
        if ( !(minAndMax[0]<minAndMax[1])) {
            for (Image i : images) {
                double[] mm = i.getMinAndMax(null);
                if (minAndMax[0]>mm[0]) minAndMax[0]=mm[0];
                if (minAndMax[1]<mm[1]) minAndMax[1]=mm[1];
            }
        }
        int[] histo= new int[256];
        for (Image im : images) {
            int[] h = im.getHisto256(minAndMax[0], minAndMax[1], null, null);
            for (int i = 0; i<256; ++i) histo[i]=h[i];
        }
        return histo;
    }
    
    protected Image cropI(BoundingBox bounds) {
        //bounds.trimToImage(this);
        Image res = newImage(name, bounds.getImageProperties("", scaleXY, scaleZ));
        res.setCalibration(this);
        res.addOffset(this); // TODO: si absoluteLandmark -> ne pas ajouter...
        int x_min = bounds.getxMin();
        int y_min = bounds.getyMin();
        int z_min = bounds.getzMin();
        int x_max = bounds.getxMax();
        int y_max = bounds.getyMax();
        int z_max = bounds.getzMax();
        int sX = x_max - x_min + 1;
        int oZ = -z_min;
        int oY_i = 0;
        int oX = 0;
        if (x_min <= -1) {
            oX=-x_min;
            x_min = 0;
        }
        if (x_max >= sizeX) {
            x_max = sizeX - 1;
        }
        if (y_min <= -1) {
            oY_i = -sX * y_min;
            y_min = 0;
        }
        if (y_max >= sizeY) {
            y_max = sizeY - 1;
        }
        if (z_min <= -1) {
            z_min = 0;
        }
        if (z_max >= sizeZ) {
            z_max = sizeZ - 1;
        }
        int sXo = x_max - x_min + 1;
        for (int z = z_min; z <= z_max; ++z) {
            int offY = y_min * sizeX;
            int oY = oY_i;
            for (int y = y_min; y <= y_max; ++y) {
                System.arraycopy(getPixelArray()[z], offY + x_min, res.getPixelArray()[z + oZ], oY + oX, sXo);
                oY += sX;
                offY += sizeX;
            }
        }
        return res;
    }
    
    
    //public abstract Image[] crop(int[][] bounds);
    
    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getSizeXY() {
        return sizeXY;
    }

    public int getSizeXYZ() {
        return sizeXYZ;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public float getScaleXY() {
        return scaleXY;
    }

    public float getScaleZ() {
        return scaleZ;
    }
    
    public String getName() {
        return name;
    }
    
    
}
