package com.bc.calvalus.processing.ma;

import com.bc.ceres.core.Assert;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Generates an output record from a {@link Product} using an input reference record.
 *
 * @author MarcoZ
 * @author Norman
 */
public class PixelExtractor {

    public static final String GOOD_PIXEL_MASK_NAME = "_good_pixel";
    public static final String ATTRIB_NAME_AGGREG_PREFIX = "*";
    public static final String EXCLUSION_REASON_ALL_MASKED = "PIXEL_EXPRESSION";

    private final Header header;
    private final Product product;
    private final Mask pixelMask;
    private final PixelTimeProvider pixelTimeProvider;
    private final int macroPixelSize;
    private final boolean copyInput;
    private final PixelPosProvider pixelPosProvider;

    public PixelExtractor(Header inputHeader,
                          Product product,
                          int macroPixelSize,
                          String goodPixelMaskExpression,
                          Double maxTimeDifference,
                          boolean copyInput) {
        this.product = product;
        if (goodPixelMaskExpression != null && !goodPixelMaskExpression.trim().isEmpty()) {
            this.pixelMask = createGoodPixelMask(product, goodPixelMaskExpression);
        } else {
            this.pixelMask = null;
        }
        this.macroPixelSize = macroPixelSize;
        this.pixelTimeProvider = PixelTimeProvider.create(product);
        this.pixelPosProvider = new PixelPosProvider(product, pixelTimeProvider, maxTimeDifference, inputHeader.hasTime());

        this.copyInput = copyInput;

        // Important note: createHeader() is dependent on a number of field values,
        // so we call it at last
        this.header = createHeader(inputHeader);
    }

    /**
     * @return The header corresponding to the records that are generated by this extractor.
     */
    public Header getHeader() {
        return header;
    }

    /**
     * Extracts an output record.
     *
     * @param inputRecord The input record.
     *
     * @return The output record or {@code null}, if a certain inclusion criterion is not met.
     *
     * @throws java.io.IOException If any I/O error occurs
     */
    public Record extract(Record inputRecord) throws IOException {
        Assert.notNull(inputRecord, "inputRecord");
        final PixelPos pixelPos = pixelPosProvider.getTemporallyAndSpatiallyValidPixelPos(inputRecord);
        if (pixelPos != null) {
            return extract(inputRecord, pixelPos);
        }
        return null;
    }

    /**
     * Extracts an output record.
     *
     * @param inputRecord The input record.
     * @param pixelPos    The validated pixel pos.
     *
     * @return The output record or {@code null}, if a certain inclusion criterion is not met.
     *
     * @throws java.io.IOException If an I/O error occurs
     */
    public Record extract(Record inputRecord, PixelPos pixelPos) throws IOException {

        final Rectangle macroPixelRect = new Rectangle(product.getSceneRasterWidth(), product.getSceneRasterHeight()).intersection(
                new Rectangle((int) pixelPos.x - macroPixelSize / 2,
                              (int) pixelPos.y - macroPixelSize / 2,
                              macroPixelSize, macroPixelSize));


        int x0 = macroPixelRect.x;
        int y0 = macroPixelRect.y;
        int width = macroPixelRect.width;
        int height = macroPixelRect.height;

        final int[] maskSamples;
        String exclusionReason = "";
        if (pixelMask != null) {
            maskSamples = new int[width * height];
            pixelMask.readPixels(x0, y0, width, height, maskSamples);
            boolean allBad = true;
            for (int i = 0; i < maskSamples.length; i++) {
                int sample = maskSamples[i];
                if (sample != 0) {
                    maskSamples[i] = 1;
                    allBad = false;
                }
            }
            if (allBad) {
                exclusionReason = EXCLUSION_REASON_ALL_MASKED;
            }
        } else {
            maskSamples = null;
        }

        final Object[] values = new Object[header.getAttributeNames().length];

        int index = 0;
        if (copyInput) {
            Object[] inputValues = inputRecord.getAttributeValues();
            System.arraycopy(inputValues, 0, values, 0, inputValues.length);
            index = inputValues.length;
        }

        final int[] pixelXPositions = new int[width * height];
        final int[] pixelYPositions = new int[width * height];
        final float[] pixelLatitudes = new float[width * height];
        final float[] pixelLongitudes = new float[width * height];

        for (int i = 0, y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++, i++) {
                PixelPos pp = new PixelPos(x + 0.5F, y + 0.5F);
                GeoPos gp = product.getGeoCoding().getGeoPos(pp, null);
                // todo - compute actual source pixel positions (need offsets here!)  (mz,nf)
                pixelXPositions[i] = x;
                pixelYPositions[i] = y;
                pixelLatitudes[i] = gp.lat;
                pixelLongitudes[i] = gp.lon;
            }
        }

        ////////////////////////////////////
        // 1. derived information
        //
        // field "source_name"
        values[index++] = product.getName();
        // field "pixel_time"
        values[index++] = pixelTimeProvider != null ? pixelTimeProvider.getTime(pixelPos) : null;
        // field "pixel_x"
        values[index++] = pixelXPositions;
        // field "pixel_y"
        values[index++] = pixelYPositions;
        // field "pixel_lat"
        values[index++] = pixelLatitudes;
        // field "pixel_lon"
        values[index++] = pixelLongitudes;
        if (maskSamples != null) {
            // field "pixel_mask"
            values[index++] = maskSamples;
        }

        ////////////////////////////////////
        // 2. + 3. bands and flags
        //
        final Band[] productBands = product.getBands();
        for (Band band : productBands) {
            if (!band.isFlagBand()) {
                if (band.isFloatingPointType()) {
                    final float[] floatSamples = new float[macroPixelRect.width * macroPixelRect.height];
                    band.readPixels(x0, y0, width, height, floatSamples);
                    values[index++] = floatSamples;
                    maskNaN(band, x0, y0, width, height, floatSamples);
                } else {
                    final int[] intSamples = new int[macroPixelRect.width * macroPixelRect.height];
                    band.readPixels(x0, y0, width, height, intSamples);
                    values[index++] = intSamples;
                }
            }
        }

        ////////////////////////////////////
        // 4. tie-points
        //
        for (TiePointGrid tiePointGrid : product.getTiePointGrids()) {
            final float[] floatSamples = new float[macroPixelRect.width * macroPixelRect.height];
            tiePointGrid.readPixels(x0, y0, width, height, floatSamples);
            values[index++] = floatSamples;
        }

        return new DefaultRecord(inputRecord.getLocation(), inputRecord.getTime(), values, new Object[]{exclusionReason});
    }

    private Header createHeader(Header inputHeader) {
        final java.util.List<String> attributeNames = new ArrayList<String>();

        if (copyInput) {
            Collections.addAll(attributeNames, inputHeader.getAttributeNames());
        }

        ////////////////////////////////////
        // 1. derived information
        //
        attributeNames.add(ProductRecordSource.SOURCE_NAME_ATT_NAME);
        attributeNames.add(ProductRecordSource.PIXEL_TIME_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_X_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_Y_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_LAT_ATT_NAME);
        attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_LON_ATT_NAME);
        if (pixelMask != null) {
            attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + ProductRecordSource.PIXEL_MASK_ATT_NAME);
        }

        ////////////////////////////////////
        // 2. bands
        //
        Band[] productBands = product.getBands();
        for (Band band : productBands) {
            if (!band.isFlagBand()) {
                attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + band.getName());
            }
        }

        ////////////////////////////////////
        // 3. flags (virtual bands)
        //
        for (Band band : productBands) {
            if (band.isFlagBand()) {
                FlagCoding flagCoding = band.getFlagCoding();
                String[] flagNames = flagCoding.getFlagNames();
                for (String flagName : flagNames) {
                    attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + band.getName() + "." + flagName);
                    // Note: side-effect here, adding new band to product
                    product.addBand("flag_" + band.getName() + "_" + flagName, band.getName() + "." + flagName, ProductData.TYPE_INT8);
                }
            }
        }

        ////////////////////////////////////
        // 4. tie-points
        //
        String[] tiePointGridNames = product.getTiePointGridNames();
        for (String tiePointGridName : tiePointGridNames) {
            attributeNames.add(ATTRIB_NAME_AGGREG_PREFIX + tiePointGridName);
        }

        return new DefaultHeader(true,
                                 pixelTimeProvider != null,
                                 attributeNames.toArray(new String[attributeNames.size()]));
    }

    /**
     * Gets the temporally and spatially valid pixel position.
     *
     * @param referenceRecord The reference record
     *
     * @return The pixel position, or {@code null} if no such exist.
     */
    public PixelPos getPixelPos(Record referenceRecord) {
        return pixelPosProvider.getPixelPos(referenceRecord);
    }

    private void maskNaN(Band band, int x0, int y0, int width, int height, float[] samples) {
        for (int i = 0, y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++, i++) {
                if (!band.isPixelValid(x, y)) {
                    samples[i] = Float.NaN;
                }
            }
        }
    }

    private static Mask createGoodPixelMask(Product product, String goodPixelExpression) {
        Mask mask = product.getMaskGroup().get(GOOD_PIXEL_MASK_NAME);
        if (mask != null) {
            product.getMaskGroup().remove(mask);
            mask.dispose();
        }

        int width = product.getSceneRasterWidth();
        int height = product.getSceneRasterHeight();

        mask = Mask.BandMathsType.create(GOOD_PIXEL_MASK_NAME,
                                         null,
                                         width,
                                         height,
                                         goodPixelExpression,
                                         Color.RED,
                                         0.5);

        // Note: side-effect here, adding new mask to product
        product.getMaskGroup().add(mask);

        return mask;
    }
}
