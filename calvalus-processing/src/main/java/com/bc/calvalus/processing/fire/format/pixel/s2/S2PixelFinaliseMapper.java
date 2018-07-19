package com.bc.calvalus.processing.fire.format.pixel.s2;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.fire.format.pixel.PixelFinaliseMapper;
import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.esa.snap.collocation.CollocateOp;
import org.esa.snap.collocation.ResamplingType;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.PlainFeatureFactory;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.VectorDataNode;
import org.esa.snap.core.util.FeatureUtils;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class S2PixelFinaliseMapper extends PixelFinaliseMapper {

    @Override
    public Product collocateWithSource(Product lcProduct, Product source) {
        CollocateOp collocateOp = new CollocateOp();
        collocateOp.setMasterProduct(source);
        collocateOp.setSlaveProduct(lcProduct);
        collocateOp.setResamplingType(ResamplingType.NEAREST_NEIGHBOUR);

        return collocateOp.getTargetProduct();
    }

    @Override
    protected String getCalvalusSensor() {
        return "S2";
    }

    @Override
    protected ClScaler getClScaler() {
        return cl -> {
            if (cl < 0.05) {
                return 0;
            } else if (cl <= 0.14) {
                return 50;
            } else if (cl <= 0.23) {
                return 60;
            } else if (cl <= 0.32) {
                return 70;
            } else if (cl <= 0.41) {
                return 80;
            } else if (cl <= 0.50) {
                return 90;
            } else {
                return 100;
            }
        };
    }

    @Override
    protected Band getLcBand(Product lcProduct) {
        return lcProduct.getBand("band_1");
    }

    @Override
    public String createBaseFilename(String year, String month, String version, String areaString) {
        return String.format("%s%s01-ESACCI-L3S_FIRE-BA-MSI-AREA_%s-%s", year, month, areaString.split(";")[1], version);
    }

    @Override
    protected void applyFixes(Product resultJD) {
        SimpleFeatureType wktFeatureType = PlainFeatureFactory.createDefaultFeatureType(DefaultGeographicCRS.WGS84);
        ListFeatureCollection newCollection = new ListFeatureCollection(wktFeatureType);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(wktFeatureType);
        SimpleFeature wktFeature = featureBuilder.buildFeature("ID" + Long.toHexString(System.nanoTime()));
        Geometry geometry;
        try {
            geometry = new WKTReader().read("POLYGON ((11.9989595413208 -13.672914505004883, 12.007415771484375 -14.997162818908691, \n" +
                    "   11.6032075881958 -14.99885368347168, 11.752037048339844 -14.643692016601562, \n" +
                    "   11.833216667175293 -14.325736999511719, 11.80784797668457 -14.237792015075684, \n" +
                    "   11.802774429321289 -14.114330291748047, 11.831525802612305 -13.997633934020996, \n" +
                    "   11.880572319030762 -13.848804473876953, 11.995576858520508 -13.65431022644043, \n" +
                    "   11.995576858520508 -13.65431022644043, 11.9989595413208 -13.672914505004883))");
        } catch (ParseException e) {
            throw new IllegalStateException("Programming error, must not come here");
        }
        wktFeature.setDefaultGeometry(geometry);
        newCollection.add(wktFeature);

        FeatureCollection<SimpleFeatureType, SimpleFeature> productFeatures = FeatureUtils.clipFeatureCollectionToProductBounds(
                newCollection,
                resultJD,
                null,
                ProgressMonitor.NULL);

        if (productFeatures.isEmpty()) {
            CalvalusLogger.getLogger().info("The geometry is not contained in the product.");
            return;
        }

        Product temp = new Product(resultJD.getName(), resultJD.getProductType(), resultJD.getSceneRasterWidth(), resultJD.getSceneRasterHeight());
        Band originalJDBand = resultJD.getBand("JD");
        temp.addBand(originalJDBand);
        resultJD.removeBand(originalJDBand);

        VectorDataNode geometryNode = new VectorDataNode("geometry", productFeatures);
        temp.getVectorDataGroup().add(geometryNode);

        temp.getBand("JD").setName("JD_orig");
        temp.addBand("JD", "if geometry then -2 else JD_orig");

        resultJD.addBand(temp.getBand("JD"));

    }
}