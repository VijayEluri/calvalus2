/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.binning.operator;

import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Geometry;
import org.esa.beam.binning.*;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProducts;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.experimental.Output;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StopWatch;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.converters.JtsGeometryConverter;
import org.esa.beam.util.io.FileUtils;
import ucar.ma2.InvalidRangeException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;

/*

todo - address the following BinningOp requirements (nf, 2012-03-09)

(1) allow for reading a metadata attributes file (e.g. Java Properties file) whose content will be converted
    to NetCDF global attributes. See http://oceancolor.gsfc.nasa.gov/DOCS/Ocean_Level-3_Binned_Data_Products.pdf
    for possible attributes. Ideally, we treat the metadata file as a template and fill in placeholders, e.g.
    ${operatorParameters}, or ${operatorName} or ${operatorVersion} ...
(2) We shall not only rely on the @SourceProducts annotation, but also use an input directory which we scan by
    globbing (using filename wildcards). This is important for windows users because the DOS shell does not allow
    for argument expansion using wildcard. PixExOp follows a similar approach but used a weird pattern. (check!)
(3) For simplicity, we shall not use BinningConfig and FormatterConfig but simply move their @Parameter declarations
    into the BinningOp class.
(4) It shall be possible to output both or either one, a mapped product file AND/OR the SeaDAS-like binned data
    file (SeaDAS).
(5) For dealing with really large amounts of bins (global binning), we need SpatialBinConsumer and TemporalBinSource
    implementations that write to and read from local files. (E.g. use memory-mapped file I/O, see
    MappedByteBufferTest.java)
(6) If the 'region' parameter is not given, the geographical extend of the mapped product shall be limited to the one
    given by the all the participating bin cells. This is in line with the case where the parameters 'startDate' and
    'endDate' are omitted: The actual start and end dates are computed from the source products.
(7) For simplicity, we shall introduce a Boolean parameter 'global'. If it is true, 'region' will be ignored.

*/

/**
 * An operator that is used to perform spatial and temporal aggregations into "bin" cells for any number of source
 * product. The output is either a file comprising the resulting bins or a reprojected "map" of the bin cells
 * represented by a usual data product.
 * <p/>
 * Unlike most other operators, that can compute single {@link org.esa.beam.framework.gpf.Tile tiles},
 * the binning operator processes all
 * of its source products in its {@link #initialize()} method.
 *
 * @author Norman Fomferra
 * @author Marco Zühlke
 * @author Thomas Storm
 */
@OperatorMetadata(alias = "Binning",
                  version = "0.1a",
                  authors = "Norman Fomferra, Marco Zühlke, Thomas Storm",
                  copyright = "(c) 2012 by Brockmann Consult GmbH",
                  description = "Performs spatial and temporal aggregation of pixel values into 'bin' cells")
public class BinningOp extends Operator implements Output {

    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @SourceProducts(count = -1,
                    description = "The source products to be binned. Must be all of the same structure.")
    Product[] sourceProducts;

    @TargetProduct
    Product targetProduct;

    @Parameter(converter = JtsGeometryConverter.class,
               description = "The considered geographical region as a geometry in well-known text format (WKT). If not given, it is the Globe.")
    Geometry region;

    @Parameter(description = "The start date. If not given, taken from the 'oldest' source product.",
               format = DATE_PATTERN)
    String startDate;

    @Parameter(description = "The end date. If not given, taken from the 'youngest' source product.",
               format = DATE_PATTERN)
    String endDate;

    @Parameter(notNull = true,
               description = "The configuration used for the binning process. Specifies the binning grid, any variables and their aggregators.")
    BinningConfig binningConfig;

    @Parameter(notNull = true,
               description = "The configuration used for the output formatting process.")
    FormatterConfig formatterConfig;


    private transient BinningContext binningContext;

    public BinningOp() {
    }

    public Geometry getRegion() {
        return region;
    }

    public void setRegion(Geometry region) {
        this.region = region;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public BinningConfig getBinningConfig() {
        return binningConfig;
    }

    public void setBinningConfig(BinningConfig binningConfig) {
        this.binningConfig = binningConfig;
    }

    public FormatterConfig getFormatterConfig() {
        return formatterConfig;
    }

    public void setFormatterConfig(FormatterConfig formatterConfig) {
        this.formatterConfig = formatterConfig;
    }

    /**
     * Processes all source products and writes the output file.
     * The target product represents the written output file
     *
     * @throws OperatorException If a processing error occurs.
     */
    @Override
    public void initialize() throws OperatorException {
        if (binningConfig == null) {
            throw new OperatorException("Missing operator parameter 'binningConfig'");
        }
        if (binningConfig.getMaskExpr() == null) {
            throw new OperatorException("Missing operator parameter 'binningConfig.maskExpr'");
        }
        if (binningConfig.getNumRows() <= 2) {
            throw new OperatorException("Operator parameter 'binningConfig.numRows' must be greater than 2");
        }
        if (formatterConfig == null) {
            throw new OperatorException("Missing operator parameter 'formatterConfig'");
        }
        if (formatterConfig.getOutputFile() == null) {
            throw new OperatorException("Missing operator parameter 'formatterConfig.outputFile'");
        }

        ProductData.UTC startDateUtc = getStartDateUtc("startDate");
        ProductData.UTC endDateUtc = getEndDateUtc("endDate");

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        binningContext = binningConfig.createBinningContext();

        try {
            // Step 1: Spatial binning - creates time-series of spatial bins for each bin ID ordered by ID. The tree map structure is <ID, time-series>
            SortedMap<Long, List<SpatialBin>> spatialBinMap = doSpatialBinning();
            // Step 2: Temporal binning - creates a list of temporal bins, sorted by bin ID
            List<TemporalBin> temporalBins = doTemporalBinning(spatialBinMap);
            // Step 3: Formatting
            writeOutput(temporalBins, startDateUtc, endDateUtc);

            // TODO - Check efficiency of interface 'org.esa.beam.framework.gpf.experimental.Output'  (nf, 2012-03-02)
            // actually, the following line of code would be sufficient, but then, the
            // 'Output' interface implemented by this operator has no effect, because it already has a
            // 'ProductReader' instance set. The overall concept of 'Output' is not fully thought-out!
            //
            // this.targetProduct = readOutput();
            //
            // This is why I have to do the following
            Product writtenProduct = readOutput();
            this.targetProduct = copyProduct(writtenProduct);

        } catch (OperatorException e) {
            throw e;
        } catch (Exception e) {
            throw new OperatorException(e);
        }

        stopWatch.stopAndTrace(String.format("Total time for binning %d product(s)", sourceProducts.length));
    }

    private static Product copyProduct(Product writtenProduct) {
        Product targetProduct = new Product(writtenProduct.getName(), writtenProduct.getProductType(), writtenProduct.getSceneRasterWidth(), writtenProduct.getSceneRasterHeight());
        targetProduct.setStartTime(writtenProduct.getStartTime());
        targetProduct.setEndTime(writtenProduct.getEndTime());
        ProductUtils.copyMetadata(writtenProduct, targetProduct);
        ProductUtils.copyGeoCoding(writtenProduct, targetProduct);
        ProductUtils.copyTiePointGrids(writtenProduct, targetProduct);
        ProductUtils.copyMasks(writtenProduct, targetProduct);
        ProductUtils.copyVectorData(writtenProduct, targetProduct);
        for (Band band : writtenProduct.getBands()) {
            // Force setting source image, otherwise GPF will set an OperatorImage and invoke computeTile()!!
            ProductUtils.copyBand(band.getName(), writtenProduct, targetProduct);
            targetProduct.getBand(band.getName()).setSourceImage(band.getSourceImage());
        }
        return targetProduct;
    }

    private Product readOutput() throws IOException {
        return ProductIO.readProduct(new File(formatterConfig.getOutputFile()));
    }


    private SortedMap<Long, List<SpatialBin>> doSpatialBinning() throws IOException {
        final SpatialBinStore spatialBinStore = new SpatialBinStore();
        final SpatialBinner spatialBinner = new SpatialBinner(binningContext, spatialBinStore);
        for (Product sourceProduct : sourceProducts) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            getLogger().info(String.format("Spatial binning of product '%s'...", sourceProduct.getName()));
            final long numObs = SpatialProductBinner.processProduct(sourceProduct, spatialBinner, binningContext.getSuperSampling(), ProgressMonitor.NULL);
            stopWatch.stop();
            getLogger().info(String.format("Spatial binning of product '%s' done, %d observations seen, took %s", sourceProduct.getName(), numObs, stopWatch));
        }
        return spatialBinStore.getSpatialBinMap();
    }

    private List<TemporalBin> doTemporalBinning(SortedMap<Long, List<SpatialBin>> spatialBinMap) throws IOException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        getLogger().info(String.format("Spatial binning of %d bins", spatialBinMap.size()));
        final TemporalBinner temporalBinner = new TemporalBinner(binningContext);
        final ArrayList<TemporalBin> temporalBins = new ArrayList<TemporalBin>();
        for (Map.Entry<Long, List<SpatialBin>> entry : spatialBinMap.entrySet()) {
            final TemporalBin temporalBin = temporalBinner.processSpatialBins(entry.getKey(), entry.getValue());
            temporalBins.add(temporalBin);
        }
        stopWatch.stop();
        getLogger().info(String.format("Spatial binning of %d bins done, took %s", spatialBinMap.size(), stopWatch));

        return temporalBins;
    }

    private void writeOutput(List<TemporalBin> temporalBins, ProductData.UTC startTime, ProductData.UTC stopTime) throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        File binnedDataFile = FileUtils.exchangeExtension(new File(formatterConfig.getOutputFile()), "-bins.nc");
        try {
            getLogger().info(String.format("Writing binned data to '%s'...", binnedDataFile));
            writeNetcdfBinFile(binnedDataFile,
                               temporalBins, startTime, stopTime);
            getLogger().info(String.format("Writing binned data to '%s' done.", binnedDataFile));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, String.format("Failed to write binned data to '%s': %s", binnedDataFile, e.getMessage()), e);
        }

        getLogger().info(String.format("Writing mapped product '%s'...", formatterConfig.getOutputFile()));
        // TODO - add metadata (nf)
        Formatter.format(binningContext,
                         new SimpleTemporalBinSource(temporalBins),
                         formatterConfig,
                         region,
                         startTime,
                         stopTime,
                         new MetadataElement("TODO_add_metadata_here"));
        stopWatch.stop();

        getLogger().info(String.format("Writing mapped product '%s' done, took %s", formatterConfig.getOutputFile(), stopWatch));
    }

    private void writeNetcdfBinFile(File file, List<TemporalBin> temporalBins, ProductData.UTC startTime, ProductData.UTC stopTime) throws IOException {
        final BinWriter writer = new BinWriter(getLogger());
        try {
            writer.write(file, temporalBins, binningContext, region, startTime, stopTime);
        } catch (InvalidRangeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private ProductData.UTC getStartDateUtc(String parameterName) throws OperatorException {
        if (!StringUtils.isNullOrEmpty(startDate)) {
            return parseDateUtc(parameterName, startDate);
        }
        ProductData.UTC startDateUtc = null;
        for (Product sourceProduct : sourceProducts) {
            if (sourceProduct.getStartTime() != null) {
                if (startDateUtc == null
                        || sourceProduct.getStartTime().getAsDate().before(startDateUtc.getAsDate())) {
                    startDateUtc = sourceProduct.getStartTime();
                }
            }
        }
        if (startDateUtc == null) {
            throw new OperatorException(String.format("Failed to determine '%s' from source products", parameterName));
        }
        return startDateUtc;
    }

    private ProductData.UTC getEndDateUtc(String parameterName) {
        if (!StringUtils.isNullOrEmpty(endDate)) {
            return parseDateUtc(parameterName, endDate);
        }
        ProductData.UTC endDateUtc = null;
        for (Product sourceProduct : sourceProducts) {
            if (sourceProduct.getEndTime() != null) {
                if (endDateUtc == null
                        || sourceProduct.getEndTime().getAsDate().after(endDateUtc.getAsDate())) {
                    endDateUtc = sourceProduct.getStartTime();
                }
            }
        }
        if (endDateUtc == null) {
            throw new OperatorException(String.format("Failed to determine '%s' from source products", parameterName));
        }
        return endDateUtc;
    }

    private ProductData.UTC parseDateUtc(String name, String date) {
        try {
            return ProductData.UTC.parse(date, DATE_PATTERN);
        } catch (ParseException e) {
            throw new OperatorException(String.format("Invalid parameter '%s': %s", name, e.getMessage()));
        }
    }

    private static class SpatialBinStore implements SpatialBinConsumer {
        // Note, we use a sorted map in order to sort entries on-the-fly
        final private SortedMap<Long, List<SpatialBin>> spatialBinMap = new TreeMap<Long, List<SpatialBin>>();

        public SortedMap<Long, List<SpatialBin>> getSpatialBinMap() {
            return spatialBinMap;
        }

        @Override
        public void consumeSpatialBins(BinningContext binningContext, List<SpatialBin> spatialBins) {

            for (SpatialBin spatialBin : spatialBins) {
                List<SpatialBin> spatialBinList = spatialBinMap.get(spatialBin.getIndex());
                if (spatialBinList == null) {
                    spatialBinList = new ArrayList<SpatialBin>();
                    spatialBinMap.put(spatialBin.getIndex(), spatialBinList);
                }
                spatialBinList.add(spatialBin);
            }
        }
    }

    private static class SimpleTemporalBinSource implements TemporalBinSource {
        private final List<TemporalBin> temporalBins;

        public SimpleTemporalBinSource(List<TemporalBin> temporalBins) {
            this.temporalBins = temporalBins;
        }

        @Override
        public int open() throws IOException {
            return 1;
        }

        @Override
        public Iterator<? extends TemporalBin> getPart(int index) throws IOException {
            return temporalBins.iterator();
        }

        @Override
        public void partProcessed(int index, Iterator<? extends TemporalBin> part) throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }

    /**
     * The service provider interface (SPI) which is referenced
     * in {@code /META-INF/services/org.esa.beam.framework.gpf.OperatorSpi}.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(BinningOp.class);
        }
    }

}
