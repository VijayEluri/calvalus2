/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.binning.aggregators;

import com.bc.calvalus.binning.BinContext;
import com.bc.calvalus.binning.MyVariableContext;
import com.bc.calvalus.binning.VectorImpl;
import org.junit.Before;
import org.junit.Test;

import static com.bc.calvalus.binning.aggregators.AggregatorTestUtils.createCtx;
import static com.bc.calvalus.binning.aggregators.AggregatorTestUtils.vec;
import static java.lang.Float.NaN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AggregatorPercentileTest {

    BinContext ctx;

    @Before
    public void setUp() throws Exception {
        ctx = createCtx();
    }

    @Test
    public void testMetadata_P90() {
        AggregatorPercentile agg = new AggregatorPercentile(new MyVariableContext("c"), "c", null, null);

        assertEquals("c_sum_x", agg.getSpatialFeatureNames()[0]);
        assertEquals("c_P90", agg.getTemporalFeatureNames()[0]);
        assertEquals("c_P90", agg.getOutputFeatureNames()[0]);
        assertTrue(Float.isNaN(agg.getOutputFillValue()));
    }

    @Test
    public void testMetadata_P70() {
        AggregatorPercentile agg = new AggregatorPercentile(new MyVariableContext("c"), "c", 70, 0.42F);

        assertEquals("PERCENTILE", agg.getName());

        assertEquals(1, agg.getSpatialFeatureNames().length);
        assertEquals("c_sum_x", agg.getSpatialFeatureNames()[0]);

        assertEquals(1, agg.getTemporalFeatureNames().length);
        assertEquals("c_P70", agg.getTemporalFeatureNames()[0]);

        assertEquals(1, agg.getOutputFeatureNames().length);
        assertEquals("c_P70", agg.getOutputFeatureNames()[0]);

        assertEquals(0.42F, agg.getOutputFillValue(), 1e-5F);
    }

    @Test
    public void testAggregatorPercentile() {
        AggregatorPercentile agg = new AggregatorPercentile(new MyVariableContext("c"), "c", 70, 0.42F);

        VectorImpl svec = vec(NaN);
        VectorImpl tvec = vec(NaN);
        VectorImpl out = vec(NaN);

        agg.initSpatial(ctx, svec);
        assertEquals(0.0f, svec.get(0), 0.0f);

        agg.aggregateSpatial(ctx, vec(1.5f), svec);
        agg.aggregateSpatial(ctx, vec(2.5f), svec);
        agg.aggregateSpatial(ctx, vec(0.5f), svec);
        float sumX = 1.5f + 2.5f + 0.5f;
        assertEquals(sumX, svec.get(0), 1e-5f);

        int numObs = 3;
        agg.completeSpatial(ctx, numObs, svec);
        assertEquals(sumX / numObs, svec.get(0), 1e-5f);

        agg.initTemporal(ctx, tvec);
        assertEquals(0.0f, tvec.get(0), 0.0f);

        agg.aggregateTemporal(ctx, vec(0.1f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.2f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.3f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.4f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.5f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.6f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.7f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.8f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(0.9f), 1, tvec);
        agg.aggregateTemporal(ctx, vec(1.0f), 1, tvec);
        assertEquals(0.0f, tvec.get(0), 1e-5f);

        agg.completeTemporal(ctx, 10, tvec);
        assertEquals(0.77f, tvec.get(0), 1e-5f);

        agg.computeOutput(tvec, out);
        assertEquals(0.77f, out.get(0), 1e-5f);
    }

}
