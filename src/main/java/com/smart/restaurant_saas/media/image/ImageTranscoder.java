package com.smart.restaurant_saas.media.image;

import com.smart.restaurant_saas.common.ValidationException;
import com.smart.restaurant_saas.media.MediaErrorCode;
import com.smart.restaurant_saas.media.enums.DerivativeSet;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Turns uploaded bytes into the renditions a purpose asks for.
 *
 * <p>Two properties hold for every derivative: aspect ratio is preserved, and an image is
 * <strong>never upscaled</strong>. A rendition smaller than its target is still written rather than
 * skipped — the UI asks for {@code THUMB} unconditionally, and a missing row would be a 404 on a
 * perfectly good file.
 *
 * <p>Derivatives are written as JPEG, or PNG when the source carries an alpha channel. The original
 * keeps its own bytes and content type untouched, so a WebP upload stays WebP at {@code ORIGINAL}
 * even though nothing here can write WebP back.
 */
@Component
public class ImageTranscoder {

    private static final float JPEG_QUALITY = 0.85f;
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    static {
        // Keep decoding in memory. The default writes a cache file to java.io.tmpdir, which on a
        // container with a read-only or tiny tmpfs fails on the upload path rather than at startup.
        ImageIO.setUseCache(false);
    }

    /** One produced rendition, not yet stored. */
    public record Rendered(byte[] bytes, String contentType, int width, int height) {}

    /** Everything an upload needs to know about the decoded image. */
    public record Transcoded(int width, int height,
                             List<DerivativeRendition> derivatives) {}

    /** A rendition paired with the variant it satisfies. */
    public record DerivativeRendition(DerivativeSet.Rendition rendition, Rendered rendered) {}

    public Transcoded transcode(byte[] original, DerivativeSet derivativeSet) {
        BufferedImage source = decode(original);
        boolean hasAlpha = source.getColorModel().hasAlpha();
        String outputType = hasAlpha ? PNG : JPEG;

        List<DerivativeRendition> derivatives = new ArrayList<>();
        for (DerivativeSet.Rendition rendition : derivativeSet.getRenditions()) {
            BufferedImage scaled = scaleWithin(source, rendition.longestEdge(), hasAlpha);
            derivatives.add(new DerivativeRendition(rendition, encode(scaled, outputType)));
        }
        return new Transcoded(source.getWidth(), source.getHeight(), derivatives);
    }

    private BufferedImage decode(byte[] bytes) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw unreadable(ex);
        }
        if (image == null) {
            // ImageIO answers null rather than throwing when no registered reader claims the
            // bytes. A content type that passed the purpose check and still lands here means the
            // declared type and the actual bytes disagree.
            throw unreadable(null);
        }
        return image;
    }

    /**
     * Fits the image inside a square of {@code longestEdge}, preserving aspect ratio.
     *
     * <p>Downscaling happens by repeated halving before the final step. A single bicubic pass from
     * 1600px to 96px samples far too few source pixels and produces visible aliasing — most
     * obviously on text in a product photo, which is exactly what a thumbnail is read for.
     */
    private BufferedImage scaleWithin(BufferedImage source, int longestEdge, boolean hasAlpha) {
        int sourceLongest = Math.max(source.getWidth(), source.getHeight());
        if (sourceLongest <= longestEdge) {
            // Never upscale. Re-encoded at its own size so the rendition still exists.
            return source;
        }
        double ratio = (double) longestEdge / sourceLongest;
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));

        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        while (width / 2 > targetWidth && height / 2 > targetHeight) {
            width /= 2;
            height /= 2;
            current = redraw(current, width, height, hasAlpha);
        }
        return redraw(current, targetWidth, targetHeight, hasAlpha);
    }

    private BufferedImage redraw(BufferedImage source, int width, int height, boolean hasAlpha) {
        BufferedImage target = new BufferedImage(width, height,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private Rendered encode(BufferedImage image, String contentType) {
        // A JPEG writer handed an ARGB raster produces inverted colours rather than an error, so
        // the flattening below is load-bearing, not defensive.
        BufferedImage encodable = JPEG.equals(contentType) && image.getColorModel().hasAlpha()
                ? redraw(image, image.getWidth(), image.getHeight(), false)
                : image;

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(contentType);
        if (!writers.hasNext()) {
            throw new IllegalStateException("No ImageIO writer for " + contentType);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(encodable, null, null), param);
        } catch (IOException ex) {
            throw unreadable(ex);
        } finally {
            writer.dispose();
        }
        return new Rendered(out.toByteArray(), contentType,
                encodable.getWidth(), encodable.getHeight());
    }

    /**
     * The cause is folded into the debug message rather than chained: {@code ValidationException}
     * takes no cause, and this is a 400 whose stack trace says nothing the message does not.
     */
    private ValidationException unreadable(Exception cause) {
        return new ValidationException(MediaErrorCode.MEDIA_IMAGE_UNREADABLE,
                "Uploaded bytes could not be decoded as an image: "
                        + (cause == null ? "no registered ImageIO reader claimed them" : cause.toString()));
    }
}
