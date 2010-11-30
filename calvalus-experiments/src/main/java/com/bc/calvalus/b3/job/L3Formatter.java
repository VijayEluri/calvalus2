package com.bc.calvalus.b3.job;

import com.bc.calvalus.b3.Aggregator;
import com.bc.calvalus.b3.BinManager;
import com.bc.calvalus.b3.BinningContext;
import com.bc.calvalus.b3.BinningGrid;
import com.bc.calvalus.b3.TemporalBin;
import com.bc.calvalus.b3.WritableVector;
import com.bc.calvalus.experiments.util.CalvalusLogger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Formatter for the outputs generated by the L3Tool.
 * <pre>
 *   Usage:
 *       <b>L3Formatter</b> <i>input-dir</i> <i>output-file</i> <b>RGB</b>  <i>r-band</i> <i>r-v-min</i> <i>r-v-max</i>  <i>g-band</i> <i>g-v-min</i> <i>g-v-max</i>  <i>b-band</i> <i>b-v-min</i> <i>b-v-max</i>
 *   or
 *       <b>L3Formatter</b> <i>input-dir</i> <i>output-file</i> <b>Grey</b>  <i>band</i> <i>v-min</i> <i>v-max</i>  [ <i>band</i> <i>v-min</i> <i>v-max</i> ... ]
 * </pre>
 *
 * @author Norman Fomferra
 */
public class L3Formatter extends Configured implements Tool {

    private static final Logger LOG = CalvalusLogger.getLogger();
    private static final String PART_R = "part-r-";
    private static final String JOB_CONF = "job-conf.xml";

    @Override
    public int run(String[] args) throws Exception {
        try {
            final String requestFile = args.length > 0 ? args[0] : "l3formatter.properties";
            final Properties request = L3Tool.loadProperties(new File(requestFile));

            final Path l3OutputDir = new Path(request.getProperty(L3Config.CONFNAME_L3_OUTPUT));
            final File outputFile = new File(request.getProperty("calvalus.l3.formatter.output"));

            final String outputType = request.getProperty("calvalus.l3.formatter.outputType");
            if (!outputType.equalsIgnoreCase("RGB")
                    && !outputType.equalsIgnoreCase("Grey")) {
                throw new IllegalArgumentException("Unknown output type: " + outputType);
            }

            String outputFormat = request.getProperty("calvalus.l3.formatter.outputFormat");
            final String fileName = outputFile.getName();
            final int extPos = fileName.lastIndexOf(".");
            final String fileNameBase = fileName.substring(0, extPos);
            final String fileNameExt = fileName.substring(extPos + 1);
            if (outputFormat == null) {
                outputFormat = fileNameExt.toUpperCase();
            }
            if (!outputFormat.equalsIgnoreCase("PNG")
                    && !outputFormat.equalsIgnoreCase("JPG")) {
                throw new IllegalArgumentException("Unknown output format: " + outputFormat);
            }


            final Configuration l3Config = new Configuration();
            l3Config.addResource(new Path(l3OutputDir, JOB_CONF));
            l3Config.reloadConfiguration();
            final BinningContext ctx = L3Config.getBinningContext(l3Config);
            BinManager binManager = ctx.getBinManager();
            int count = binManager.getAggregatorCount();
            System.out.println("aggregators.length = " + count);
            for (int i = 0; i < count; i++) {
                Aggregator aggregator = binManager.getAggregator(i);
                System.out.println("aggregators."+i+" = " + aggregator);
            }

            final Configuration conf = getConf();
            for (String key : request.stringPropertyNames()) {
                conf.set(key, request.getProperty(key));
            }

            final BinningGrid binningGrid = ctx.getBinningGrid();
            final int width = binningGrid.getNumRows() * 2;
            final int height = binningGrid.getNumRows();

            int[] indices = new int[16];
            String[] names = new String[16];
            float[] v1s = new float[16];
            float[] v2s = new float[16];
            int numBands = 0;
            for (int i = 0; i < indices.length; i++) {
                String indexStr = request.getProperty(String.format("calvalus.l3.formatter.bands.%d.index", i));
                String nameStr = request.getProperty(String.format("calvalus.l3.formatter.bands.%d.name", i));
                String v1Str = request.getProperty(String.format("calvalus.l3.formatter.bands.%d.v1", i));
                String v2Str = request.getProperty(String.format("calvalus.l3.formatter.bands.%d.v2", i));
                if (indexStr == null) {
                    break;
                }
                indices[numBands] = Integer.parseInt(indexStr);
                names[numBands] = nameStr != null ? nameStr : indices[numBands] + "";
                v1s[numBands] = Float.parseFloat(v1Str);
                v2s[numBands] = Float.parseFloat(v2Str);
                numBands++;
            }
            if (numBands == 0) {
                throw new IllegalArgumentException("No output band given.");
            }
            indices = Arrays.copyOf(indices, numBands);
            names = Arrays.copyOf(names, numBands);
            v1s = Arrays.copyOf(v1s, numBands);
            v2s = Arrays.copyOf(v2s, numBands);
            final float[][] data = reprojectProperties(ctx, l3OutputDir, indices);

            if (outputType.equalsIgnoreCase("RGB")) {
                writeRgbImage(width, height, data, v1s, v2s, outputFormat, outputFile);
            } else if (outputType.equalsIgnoreCase("Grey")) {
                for (int i = 0; i < numBands; i++) {
                    final File imageFile = new File(outputFile.getParentFile(),
                                                    fileNameBase + "-" + names[i] + "." + fileNameExt);
                    writeGrayScaleImage(width, height, data[i], v1s[i], v2s[i], outputFormat, imageFile);
                }
            }

            return 0;
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return 1;
        }
    }

    private float[][] reprojectProperties(BinningContext ctx, Path output, int[] indices) throws IOException {
        BinningGrid binningGrid = ctx.getBinningGrid();
        int width = binningGrid.getNumRows() * 2;
        int height = binningGrid.getNumRows();
        float[][] data = new float[indices.length][width * height];
        for (int i = 0; i < indices.length; i++) {
            Arrays.fill(data[i], Float.NaN);
        }

        long startTime = System.nanoTime();

        final FileStatus[] fileStati = output.getFileSystem(getConf()).listStatus(output, new PathFilter() {
            @Override
            public boolean accept(Path path) {
                return path.getName().startsWith(PART_R);
            }
        });

        LOG.info(MessageFormat.format("start reprojection, collecting {0} parts", fileStati.length));

        Arrays.sort(fileStati);

        for (FileStatus fileStatus : fileStati) {
            Path partFile = fileStatus.getPath();
            SequenceFile.Reader reader = new SequenceFile.Reader(partFile.getFileSystem(getConf()), partFile, getConf());

            LOG.info(MessageFormat.format("reading part {0}", partFile));

            try {
                int lastRowIndex = -1;
                ArrayList<TemporalBin> binRow = new ArrayList<TemporalBin>();
                while (true) {
                    IntWritable binIndex = new IntWritable();
                    TemporalBin temporalBin = new TemporalBin();
                    if (!reader.next(binIndex, temporalBin)) {
                        // last row
                        processBinRow(ctx,
                                      lastRowIndex, binRow,
                                      data, indices,
                                      width, height);
                        binRow.clear();
                        break;
                    }
                    int rowIndex = binningGrid.getRowIndex(binIndex.get());
                    if (rowIndex != lastRowIndex) {
                        processBinRow(ctx,
                                      lastRowIndex, binRow,
                                      data, indices,
                                      width, height);
                        binRow.clear();
                        lastRowIndex = rowIndex;
                    }
                    temporalBin.setIndex(binIndex.get());
                    binRow.add(temporalBin);
                }
            } finally {
                reader.close();
            }
        }
        long stopTime = System.nanoTime();

        LOG.info(MessageFormat.format("stop reprojection after {0} sec", (stopTime - startTime) / 1E9));

        return data;
    }

     static void processBinRow(BinningContext ctx,
                              int y, List<TemporalBin> binRow,
                              float[][] data, int[] indices,
                              int width, int height) {
        if (y >= 0 && !binRow.isEmpty()) {
//            LOG.info("row " + y + ": processing " + binRow.size() + " bins, bin #0 = " + binRow.get(0));
            processBinRow0(ctx,
                           y, binRow,
                           data, indices,
                           width, height);
        } else {
//            LOG.info("row " + y + ": no bins");
        }
    }

    static void processBinRow0(BinningContext ctx,
                               int y,
                               List<TemporalBin> binRow,
                               float[][] data, int[] indices,
                               int width,
                               int height) {
        final BinningGrid binningGrid = ctx.getBinningGrid();
        final BinManager binManager = ctx.getBinManager();
        final WritableVector outputVector = binManager.createOutputVector();
        final int offset = y * width;
        final double lat = -90.0 + (y + 0.5) * 180.0 / height;
        int lastBinIndex = -1;
        TemporalBin temporalBin = null;
        int rowIndex = -1;
        for (int x = 0; x < width; x++) {
            double lon = -180.0 + (x + 0.5) * 360.0 / width;
            int wantedBinIndex = binningGrid.getBinIndex(lat, lon);
            if (lastBinIndex != wantedBinIndex) {
                //search
                temporalBin = null;
                for (int i = rowIndex + 1; i < binRow.size(); i++) {
                    final int binIndex = binRow.get(i).getIndex();
                    if (binIndex == wantedBinIndex) {
                        temporalBin = binRow.get(i);
                        binManager.computeOutput(temporalBin, outputVector);
                        lastBinIndex = wantedBinIndex;
                        rowIndex = i;
                        break;
                    } else if (binIndex > wantedBinIndex) {
                        break;
                    }
                }
            }
            if (temporalBin != null) {
                for (int i = 0; i < indices.length; i++) {
                    data[i][offset + x] = outputVector.get(indices[i]);
                }
            } else {
                for (int i = 0; i < indices.length; i++) {
                    data[i][offset + x] = Float.NaN;
                }
            }
        }
    }

    private void writeGrayScaleImage(int width, int height,
                                     float[] rawData,
                                     float rawValue1, float rawValue2,
                                     String outputFormat, File outputImageFile) throws IOException {
        LOG.info(MessageFormat.format("writing image {0}", outputImageFile));
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        final DataBufferByte dataBuffer = (DataBufferByte) image.getRaster().getDataBuffer();
        final byte[] data = dataBuffer.getData();
        final float a = 255f / (rawValue2 - rawValue1);
        final float b = -255f * rawValue1 / (rawValue2 - rawValue1);
        for (int i = 0; i < rawData.length; i++) {
            data[i] = toByte(rawData[i], a, b);
        }
        ImageIO.write(image, outputFormat, outputImageFile);
    }

    private void writeRgbImage(int width, int height,
                               float[][] rawData,
                               float[] rawValue1, float[] rawValue2,
                               String outputFormat, File outputImageFile) throws IOException {
        LOG.info(MessageFormat.format("writing image {0}", outputImageFile));
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        final DataBufferByte dataBuffer = (DataBufferByte) image.getRaster().getDataBuffer();
        final byte[] data = dataBuffer.getData();
        final float[] rawDataR = rawData[0];
        final float[] rawDataG = rawData[1];
        final float[] rawDataB = rawData[2];
        final float aR = 255f / (rawValue2[0] - rawValue1[0]);
        final float bR = -255f * rawValue1[0] / (rawValue2[0] - rawValue1[0]);
        final float aG = 255f / (rawValue2[1] - rawValue1[1]);
        final float bG = -255f * rawValue1[1] / (rawValue2[1] - rawValue1[1]);
        final float aB = 255f / (rawValue2[2] - rawValue1[2]);
        final float bB = -255f * rawValue1[2] / (rawValue2[2] - rawValue1[2]);
        for (int i = 0; i < rawData.length; i += 3) {
            data[i + 2] = toByte(rawDataR[i], aR, bR);
            data[i + 1] = toByte(rawDataG[i], aG, bG);
            data[i] = toByte(rawDataB[i], aB, bB);
        }
        ImageIO.write(image, outputFormat, outputImageFile);
    }

    private static byte toByte(float s, float a, float b) {
        int sample = (int) (a * s + b);
        if (sample < 0) {
            sample = 0;
        } else if (sample > 255) {
            sample = 255;
        }
        return (byte) sample;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new L3Formatter(), args));
    }
}
