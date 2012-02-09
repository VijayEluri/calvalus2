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

package com.bc.calvalus.processing.l3;

import com.bc.calvalus.binning.BinManager;
import com.bc.calvalus.binning.TemporalBin;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

/**
 * Reduces list of spatial bins to a temporal bin.
 *
 * @author Norman Fomferra
 * @author Marco Zuehlke
 */
public class L3Reducer extends Reducer<LongWritable, L3SpatialBin, LongWritable, L3TemporalBin> implements Configurable {

    private Configuration conf;
    private BinManager binManager;

    @Override
    protected void reduce(LongWritable binIndex, Iterable<L3SpatialBin> spatialBins, Context context) throws IOException, InterruptedException {
        L3TemporalBin temporalBin = (L3TemporalBin) binManager.createTemporalBin(binIndex.get());
        for (L3SpatialBin spatialBin : spatialBins) {
            binManager.aggregateTemporalBin(spatialBin, temporalBin);
        }
        binManager.completeTemporalBin(temporalBin);
        context.write(binIndex, temporalBin);
    }

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        this.binManager = L3Config.get(conf).getBinningContext().getBinManager();
    }

    @Override
    public Configuration getConf() {
        return conf;
    }
}
