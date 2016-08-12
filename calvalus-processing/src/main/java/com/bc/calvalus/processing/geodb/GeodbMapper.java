/*
 * Copyright (C) 2015 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.processing.geodb;

import com.bc.calvalus.processing.ProcessorAdapter;
import com.bc.calvalus.processing.ProcessorFactory;
import com.bc.calvalus.processing.hadoop.ProgressSplitProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.util.ProductUtils;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;

/**
 * A mapper for generating entries for the product-DB
 */
public class GeodbMapper extends Mapper<NullWritable, NullWritable, Text, Text> {

    @Override
    public void run(Context context) throws IOException, InterruptedException {
        ProcessorAdapter processorAdapter = ProcessorFactory.createAdapter(context);
        ProgressMonitor pm = new ProgressSplitProgressMonitor(context);
        pm.beginTask("Geometry", 100);
        try {
            Product product = processorAdapter.getInputProduct();
            if (product != null) {
                Polygon poylgon = computeProductGeometryDefault(product);
                if (poylgon != null) {
                    String wkt = poylgon.toString();
                    pm.worked(50);

                    DateFormat dateFormat = ProductData.UTC.createDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                    ProductData.UTC startUTC = product.getStartTime();
                    String startTime = "null";
                    if (startUTC != null) {
                        startTime = dateFormat.format(startUTC.getAsDate());
                    }
                    ProductData.UTC endUTC = product.getEndTime();
                    String endTime = "null";
                    if (endUTC != null) {
                        endTime = dateFormat.format(endUTC.getAsDate());
                    }
                    String dbPath = getDBPath(processorAdapter.getInputPath(), context.getConfiguration());

                    String result = startTime + "\t" + endTime + "\t" + wkt;
                    context.write(new Text(dbPath), new Text(result));
                }
            }
        } finally {
            pm.done();
            processorAdapter.dispose();
        }
    }

    /**
     * Removes the schema and the autority, if they are from the default filesystem
     */
    private String getDBPath(Path productPath, Configuration configuration) {
        URI productURI = productPath.toUri();
        URI defaultURI = FileSystem.getDefaultUri(configuration);

        boolean sameSchema = false;
        if (defaultURI.getScheme() != null && defaultURI.getScheme().equals(productURI.getScheme())) {
            sameSchema = true;
        }
        boolean sameAuthority = false;
        if (defaultURI.getAuthority() != null && defaultURI.getAuthority().equals(productURI.getAuthority())) {
            sameAuthority = true;
        }

        String dbPath = productPath.toString();
        if (sameSchema && sameAuthority) {
            dbPath = new Path(null, null, productURI.getPath()).toString();
        }
        return dbPath;
    }

    public static Polygon computeProductGeometryDefault(Product product) {
        try {
            final boolean usePixelCenter = true;
            final Rectangle region = new Rectangle(0, 0, product.getSceneRasterWidth(), product.getSceneRasterHeight());
            final int step = Math.min(region.width, region.height) / 8;
            final GeoPos[] geoPoints = ProductUtils.createGeoBoundary(product, region, step, usePixelCenter);

            final Coordinate[] coordinates = new Coordinate[geoPoints.length + 1];
            for (int i = 0; i < geoPoints.length; i++) {
                coordinates[i] = new Coordinate(geoPoints[i].lon, geoPoints[i].lat);
            }
            coordinates[coordinates.length - 1] = new Coordinate(geoPoints[0].lon, geoPoints[0].lat);

            GeometryFactory factory = new GeometryFactory();
            LinearRing linearRing = factory.createLinearRing(coordinates);
            return factory.createPolygon(linearRing, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}