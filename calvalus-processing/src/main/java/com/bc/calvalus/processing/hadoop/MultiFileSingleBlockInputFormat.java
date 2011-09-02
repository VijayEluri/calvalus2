package com.bc.calvalus.processing.hadoop;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An input format that maps each input file to a single (file) split.
 * Input files are given by the configuration parameter
 * {@link com.bc.calvalus.processing.JobConfigNames#CALVALUS_INPUT CALVALUS_INPUT}. Its value is expected to
 * be a comma-separated list of file paths (HDFS URLs).
 * <p/>
 * <b>Important note:</b> The implementation assumes that all input files comprise a single block.
 * That is, each file's size must be less than (unlikely) or equal to (more likely) its block size.
 * The behaviour for files that do not meet this requirement depends on the configuration parameter
 * {@link com.bc.calvalus.processing.JobConfigNames#CALVALUS_FAIL_FAST CALVALUS_FAIL_FAST}:
 * If it is "true", an I/O exception will be thrown, otherwise only a message will be logged.
 *
 * @author Martin
 * @author Marco
 * @author Norman
 */
public class MultiFileSingleBlockInputFormat extends InputFormat {

    private static final Logger LOG = CalvalusLogger.getLogger();

    /**
     * Maps each input file to a single (file) split.
     * Input files are given by the configuration parameter
     * {@link com.bc.calvalus.processing.JobConfigNames#CALVALUS_INPUT}. Its value is expected to
     * be a comma-separated list of file paths (HDFS URLs).
     */
    @Override
    public List<FileSplit> getSplits(JobContext job) throws IOException {

        try {
            // parse request
            Configuration configuration = job.getConfiguration();
            String[] inputUrls = configuration.get(JobConfigNames.CALVALUS_INPUT).split(",");
            boolean failFast = Boolean.parseBoolean(configuration.get(JobConfigNames.CALVALUS_FAIL_FAST, "false"));
            String inputFormat = configuration.get(JobConfigNames.CALVALUS_INPUT_FORMAT, "ENVISAT");

            // create splits for each calvalus.input in request
            List<FileSplit> splits = new ArrayList<FileSplit>(inputUrls.length);
            for (String inputUrl : inputUrls) {
                // get input out of request
                // inquire "status" of file from HDFS
                Path inputPath = new Path(inputUrl);
                FileSystem fs = inputPath.getFileSystem(configuration);
                FileStatus[] files = fs.listStatus(inputPath);
                if (files.length == 1) {
                    FileStatus file = files[0];
                    long fileLength = file.getLen();
                    BlockLocation[] blocks = fs.getFileBlockLocations(file, 0, fileLength);
                    if (blocks != null && blocks.length > 0) {
                        BlockLocation block = blocks[0];
                        if (blocks.length == 1 && block.getLength() >= fileLength || inputFormat.equals("HADOOP-STREAMING")) {
                            // create file split for the input
                            FileSplit split = new FileSplit(inputPath, 0, fileLength, block.getHosts());
                            splits.add(split);
                        } else {
                            reportIOProblem(failFast, String.format("Multiple blocks detected for file '%s'.", inputUrl));
                        }
                    } else {
                        reportIOProblem(failFast, String.format("Failed to retrieve block location for file '%s'. Ignoring it.", inputUrl));
                    }
                } else {
                    reportIOProblem(failFast, String.format("File '%s' not found.", inputUrl));
                }
            }
            return splits;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, String.format("Failed to compute list of input splits: %s", e.getMessage()), e);
            throw e;
        }
    }

    private void reportIOProblem(boolean failFast, String msg) throws IOException {
        if (failFast) {
            throw new IOException(msg);
        } else {
            LOG.severe(msg);
        }
    }

    /**
     * Creates a {@link NoRecordReader} because records are not used with this input format.
     */
    @Override
    public RecordReader<NullWritable, NullWritable> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        return new NoRecordReader();
    }
}
