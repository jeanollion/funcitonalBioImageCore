package image;

public class ImageByte extends ImageInteger {

    private byte[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageByte(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new byte[sizeZ][sizeXY];
    }
    
    public ImageByte(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new byte[sizeZ][sizeX*sizeY];
    }
    
    public ImageByte(String name, int sizeX, byte[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageByte(String name, int sizeX, byte[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new byte[][]{pixels};
    }
    
    
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX] & 0xff;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy] & 0xff;
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy] & 0xff);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX] & 0xff);
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = (byte) value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = (byte) value;
    }

    @Override
    public void setPixel(int x, int y, int z, Number value) {
        pixels[z][x + y * sizeX] = value.byteValue();
    }

    @Override
    public void setPixel(int xy, int z, Number value) {
        pixels[z][xy] = value.byteValue();
    }

    @Override
    public ImageByte duplicate(String name) {
        byte[][] newPixels = new byte[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageByte(name, sizeX, newPixels);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override
    public byte[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageByte newImage(String name, ImageProperties properties) {
        return new ImageByte(name, properties);
    }
    
    @Override
    public ImageByte crop(BoundingBox bounds) {
        return (ImageByte) cropI(bounds);
    }
}
