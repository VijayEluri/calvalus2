package com.bc.calvalus.processing.ta;

import com.bc.calvalus.processing.l3.L3Config;
import com.bc.calvalus.processing.l3.L3TemporalBin;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.esa.beam.binning.PlanetaryGrid;

import java.io.IOException;

/**
 * Maps temporal bins (as produced by L3Reducer) to region keys.
 *
 * @author Norman
 */
public class TAMapper extends Mapper<LongWritable, L3TemporalBin, Text, L3TemporalBin> implements Configurable {
    private Configuration conf;
    private PlanetaryGrid planetaryGrid;
    private TAConfig taConfig;

    @Override
    protected void map(LongWritable binIndex, L3TemporalBin temporalBin, Context context) throws IOException, InterruptedException {
        GeometryFactory geometryFactory = new GeometryFactory();
        double[] centerLatLon = planetaryGrid.getCenterLatLon(binIndex.get());
        Point point = geometryFactory.createPoint(new Coordinate(centerLatLon[1], centerLatLon[0]));
        TAConfig.RegionConfiguration[] regions = taConfig.getRegions();
        for (TAConfig.RegionConfiguration region : regions) {
            if (region.getGeometry().contains(point)) {
                context.write(new Text(region.getName()), temporalBin);
            }
        }
    }

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        L3Config l3Config = L3Config.get(conf);
        planetaryGrid = l3Config.createPlanetaryGrid();
        taConfig = TAConfig.get(conf);
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

}
