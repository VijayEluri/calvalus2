/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.processing.beam;

import com.bc.calvalus.binning.SpatialBin;
import com.bc.calvalus.binning.TemporalBin;
import com.bc.calvalus.processing.shellexec.ExecutablesInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

/**
 * Creates a beam hadoop job
 */
public class BeamJobService {

    public Job createBeamHadoopJob(Configuration configuration, String wpsXmlRequest) throws Exception {
        WpsConfig wpsConfig = new WpsConfig(wpsXmlRequest);
        String requestOutputDir = wpsConfig.getRequestOutputDir();
        String identifier = wpsConfig.getIdentifier();

        // construct job and set parameters and handlers
        Job job = new Job(configuration, identifier);
        Configuration conf = job.getConfiguration();
        conf.set("calvalus.request", wpsXmlRequest);

        // look up job jar either by class (if deployed) or by path (idea)
        // job.setJarByClass(getClass());
        String pathname = "lib/calvalus-processing-0.1-SNAPSHOT-job.jar";
        if (!new File(pathname).exists()) {
            pathname = "calvalus-processing/target/calvalus-processing-0.1-SNAPSHOT-job.jar";
            if (!new File(pathname).exists()) {
                throw new IllegalArgumentException("Cannot find job jar");
            }
        }
        conf.set("mapred.jar", pathname);


        // clear output directory
        final Path outputPath = new Path(requestOutputDir);
        final FileSystem fileSystem = outputPath.getFileSystem(configuration);
        fileSystem.delete(outputPath, true);
        FileOutputFormat.setOutputPath(job, outputPath);

        job.setInputFormatClass(ExecutablesInputFormat.class);

        if (wpsConfig.isLevel3()) {
            job.setNumReduceTasks(16);

            job.setMapperClass(L3Mapper.class);
            job.setMapOutputKeyClass(LongWritable.class);
            job.setMapOutputValueClass(SpatialBin.class);

            job.setPartitionerClass(L3Partitioner.class);

            job.setReducerClass(L3Reducer.class);
            job.setOutputKeyClass(LongWritable.class);
            job.setOutputValueClass(TemporalBin.class);

            job.setOutputFormatClass(SequenceFileOutputFormat.class);
            // todo - scan all input paths, collect all products and compute min start/ max stop sensing time
        } else {
            job.setMapperClass(BeamOperatorMapper.class);
            job.setNumReduceTasks(0);
            //job.setOutputFormatClass(TextOutputFormat.class);
            //job.setOutputKeyClass(Text.class);
            //job.setOutputValueClass(Text.class);
        }
        conf.set("hadoop.job.ugi", "hadoop,hadoop");  // user hadoop owns the outputs
        conf.set("mapred.map.tasks.speculative.execution", "false");
        conf.set("mapred.reduce.tasks.speculative.execution", "false");
        //conf.set("mapred.child.java.opts", "-Xmx1024m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8009");
        conf.set("mapred.child.java.opts", "-Xmx1024m");

        BeamCalvalusClasspath.configure(wpsConfig.getProcessorPackage(), conf);

        return job;
    }
}
