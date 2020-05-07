package org.site.server.site;

import net.coobird.thumbnailator.Thumbnails;
import org.site.elements.NodeAttach;
import org.site.view.VUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;

import com.twelvemonkeys.image.ResampleOp;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ImageSite {

    public BufferedImage image;
    String path;

    public static void setAttach(String host, NodeAttach attach, String pathName) {
        attach.src = pathName;
        if (ImageSite.isBitmap(pathName)) {
            ImageSite image = new ImageSite().load(VUtil.pathToImageSrc(host, pathName));
            ImageSite imageMax = image.resize(1800).save();
            attach.w = imageMax.image.getWidth();
            attach.h = imageMax.image.getHeight();
            VUtil.pathToImageCache(host, pathName).forEach(item ->
                    image.resize(item.size).save(item.path)
            );
        }
    }


    public static boolean isImage(String pathName) {
        String ext = VUtil.getFileExt(pathName);
        return ext.equals("jpg") || ext.equals("png") || ext.equals("svg");
    }

    public static boolean isVector(String pathName) {
        String ext = VUtil.getFileExt(pathName);
        return ext.equals("svg");
    }

    public static boolean isBitmap(String pathName) {
        String ext = VUtil.getFileExt(pathName);
        return ext.equals("jpg") || ext.equals("png");
    }

    void imageJpegWrite(String path) {
        imageJpegWrite(path, 0.9F);
    }

    void imageJpegWrite(String path, float quality) {
        try {
            Files.deleteIfExists(Paths.get(path));


            File outfile = new File(path);
            //Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ImageIO.createImageOutputStream(outfile));
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();

        } catch (IOException ex) {
            System.out.println("Exception : " + ex);
        }
    }

    public ImageSite save() {
        save(path);
        return this;
    }

    public ImageSite save(String path) {
        VUtil.createDirs(path);
        if (path.endsWith(".jpg")) {
            imageJpegWrite(path);
        } else {
            File output = new File(path);
            int i = path.lastIndexOf(".");
            String extension = path.substring(i + 1);
            try {
                ImageIO.write(image, extension, output);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public boolean loaded() {
        return image == null;
    }

    public ImageSite load(String pathSrc) {
        path = pathSrc;
        image = null;
        try {
            image = ImageIO.read(new File(pathSrc));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return this;
    }


    public ImageSite resize(double size) {
        int w = image.getWidth();
        int h = image.getHeight();
        double k = 1.0;
        if (size > 0 && (w > size || h > size)) {
            k = size / (double) (w > h ? w : h);
        }

        w = (int) Math.round(w * k);
        h = (int) Math.round(h * k);

        ImageSite imgNew = new ImageSite();
        imgNew.path = this.path;
        try {

            imgNew.image = Thumbnails.of(image).size(w, h).asBufferedImage();
        } catch (IOException e) {
            e.printStackTrace();
        }


        imgNew.path = this.path;

        return imgNew;
    }


    public ImageSite rotate(int grad) {

        double rotationRequired = Math.toRadians(grad);
        AffineTransform tx = new AffineTransform();
        if (grad == 90) {
            tx.translate(image.getHeight(), 0);
        } else {
            tx.translate(0, image.getWidth());
        }
        tx.rotate(rotationRequired);
        //tx.scale(scalex, scaley);
        //tx.shear(shiftx, shifty);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage newImage = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
        op.filter(image, newImage);

        image = newImage;

        return this;
    }
}
