package com.bc.calvalus.processing.l3;

import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.JobUtils;
import com.bc.calvalus.processing.ProcessorFactory;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.processing.hadoop.HadoopWorkflowItem;
import com.bc.calvalus.processing.hadoop.ProcessingMetadata;
import com.bc.calvalus.processing.l3.cellstream.CellInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;

import java.io.IOException;
import java.util.Map;

/**
 * A workflow item taking cells (bins) and processing them and the stores them again
 */
public class CellProcessorWorkflowItem extends HadoopWorkflowItem {

    public CellProcessorWorkflowItem(HadoopProcessingService processingService, String username, String jobName, Configuration jobConfig) {
        super(processingService, username, jobName, jobConfig);
    }

    public String getInputDir() {
        return getJobConfig().get(JobConfigNames.CALVALUS_INPUT_DIR);
    }

    @Override
    public String getOutputDir() {
        return getJobConfig().get(JobConfigNames.CALVALUS_OUTPUT_DIR);
    }

    @Override
    protected String[][] getJobConfigDefaults() {
        return new String[][]{
                {JobConfigNames.CALVALUS_INPUT_DIR, NO_DEFAULT},
                {JobConfigNames.CALVALUS_OUTPUT_DIR, NO_DEFAULT},
                {JobConfigNames.CALVALUS_CELL_PARAMETERS, NO_DEFAULT}
        };
    }

    @Override
    protected void configureJob(Job job) throws IOException {
        Configuration jobConfig = job.getConfiguration();

        FileInputFormat.addInputPaths(job, getInputDir());
        job.setInputFormatClass(CellInputFormat.class);

        job.setMapperClass(CellProcessorMapper.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(L3TemporalBin.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(L3TemporalBin.class);

        job.setNumReduceTasks(0);

        JobUtils.clearAndSetOutputDir(getOutputDir(), job, this);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);

        ProcessorFactory.installProcessorBundles(jobConfig);

        Map<String, String> metadata = ProcessingMetadata.read(FileInputFormat.getInputPaths(job)[0], jobConfig);
        ProcessingMetadata.metadata2Config(metadata, jobConfig, JobConfigNames.LEVEL3_METADATA_KEYS);
    }

}
