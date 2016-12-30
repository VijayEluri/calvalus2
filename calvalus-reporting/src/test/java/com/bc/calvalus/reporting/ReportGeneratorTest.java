package com.bc.calvalus.reporting;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.*;

/**
 * @author hans
 */
public class ReportGeneratorTest {

    private ReportGenerator reportGenerator;

    @Test
    public void testSout() throws Exception {
        UsageStatistic usageStatistic = new UsageStatistic("job_1481485063251_7037",
                                                           "default",
                                                           1483007598241L,
                                                           1483015823051L,
                                                           "SUCCEEDED",
                                                           57687118L,
                                                           533456372L,
                                                           828041058358L,
                                                           195727993L,
                                                           156226661376L,
                                                           0L,
                                                           152565099L,
                                                           0L,
                                                           72907840L);
        reportGenerator = new ReportGenerator();
        assertThat(reportGenerator.generateTextSingleJob(usageStatistic), equalTo("Usage statistic for job 'job_1481485063251_7037'\n" +
                                                                                  "\n" +
                                                                                  "Project : default\n" +
                                                                                  "Start time : 29-Dec-2016 11:33:18\n" +
                                                                                  "Finish time : 29-Dec-2016 13:50:23\n" +
                                                                                  "Total time : 02:17:04\nStatus :  SUCCEEDED\n" +
                                                                                  "Total file writing (MB) : 695\n" +
                                                                                  "Total file reading (MB) : 789,736\n" +
                                                                                  "Total CPU time spent (hours) : 20:15:07\n" +
                                                                                  "Total Memory used (MB s) :  156,226,661\n" +
                                                                                  "Total vCores used (vCore s) :  152,565\n"));
    }
}