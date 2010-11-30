package com.bc.calvalus.b3.job;

import com.bc.calvalus.b3.BinningGrid;
import com.bc.calvalus.b3.IsinBinningGrid;
import com.bc.calvalus.b3.SpatialBin;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Partitioner;

/**
 * Partitions the bins by their bin index.
 * Reduces will recive spatial bins of contiguous latitude ranges.
 *
 * @author Marco Zuehlke
 * @author Norman Fomferra
 */
public class L3Partitioner extends Partitioner<IntWritable, SpatialBin> implements Configurable {

    private Configuration conf;
    private BinningGrid binningGrid;

    @Override
    public int getPartition(IntWritable binIndex, SpatialBin spatialBin, int numPartitions) {
        int idx = binIndex.get();
        int row = binningGrid.getRowIndex(idx);
        return (row * numPartitions) / binningGrid.getNumRows();
    }

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        this.binningGrid = L3Config.getBinningGrid(conf);
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

    BinningGrid getBinningGrid() {
        return binningGrid;
    }
}
