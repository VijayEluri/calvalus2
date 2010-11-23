package com.bc.calvalus.experiments.executables;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ToolRunner;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * 
 */
public class ExecutablesToolTest {
    private static final String REQUEST = "/home/boe/modules/calvalus/calvalus-experiments/bin/l2gen-local-request.xml";
    private static final String OUTPUT_DIR = "file:///tmp/meris-l2gen-99";

    @Test
    public void runProcessing() throws Exception {
        
        // verify that output is empty
        Path outputFile = new Path(OUTPUT_DIR);
        assertTrue("Shall not exist: " + outputFile,
                   ! outputFile.getFileSystem(new Configuration()).exists(outputFile));

        // Run the job
        ToolRunner.run(new ExecutablesTool(), new String[] { REQUEST });

        assertTrue("Shall now exist: " + outputFile,
                   outputFile.getFileSystem(new Configuration()).exists(outputFile));
    }

    @Before
    public void init() throws IOException {
        clearWorkingDirs();
    }

//    @After
//    public void finish() throws IOException {
//        clearWorkingDirs();
//    }

    private void clearWorkingDirs() throws IOException {
        Path outputDir = new Path(OUTPUT_DIR);
        outputDir.getFileSystem(new Configuration()).delete(outputDir, true);
    }

}
