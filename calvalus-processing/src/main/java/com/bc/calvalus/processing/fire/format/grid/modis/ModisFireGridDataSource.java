package com.bc.calvalus.processing.fire.format.grid.modis;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.fire.format.LcRemapping;
import com.bc.calvalus.processing.fire.format.grid.AbstractFireGridDataSource;
import com.bc.calvalus.processing.fire.format.grid.GridFormatUtils;
import com.bc.calvalus.processing.fire.format.grid.SourceData;
import com.bc.ceres.core.ProgressMonitor;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.PlainFeatureFactory;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductNode;
import org.esa.snap.core.datamodel.VectorDataNode;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.common.SubsetOp;
import org.esa.snap.core.gpf.common.reproject.ReprojectionOp;
import org.esa.snap.core.util.FeatureUtils;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

public class ModisFireGridDataSource extends AbstractFireGridDataSource {

    public static final double MODIS_AREA_SIZE = 53664.6683222854702276;
    private final Product[] products;
    private final Product[] lcProducts;
    private final String targetCell; // "800,312"
    private static final Logger LOG = CalvalusLogger.getLogger();

    public ModisFireGridDataSource(Product[] products, Product[] lcProducts, String targetCell) {
        super(-1, -1);
        this.products = products;
        Arrays.sort(products, Comparator.comparing(ProductNode::getName));
        this.lcProducts = lcProducts;
        this.targetCell = targetCell;
    }

    @Override
    public SourceData readPixels(int x, int y) throws IOException {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();

        LOG.info("x=" + x + ", y=" + y);

        double lon0 = getLeftLon(x, targetCell);
        double lat0 = getTopLat(y, targetCell);

//        int totalWidth = 0;
//        int totalHeight = 0;

//        for (Product sourceProduct : products) {
//            Product jdSubset = getSubset(lon0, lat0, sourceProduct);
//            if (jdSubset != null && jdSubset.getSceneRasterWidth() > 1 && jdSubset.getSceneRasterHeight() > 1) {
//                totalWidth += jdSubset.getSceneRasterWidth();
//                totalHeight += jdSubset.getSceneRasterHeight();
//            }
//        }

//        if (totalHeight == 0 || totalWidth == 0) {
           //  grid cell is covered by water completely - that's fine.
//            LOG.warning("Completely covered by water? x=" + x + ", y=" + y);
//            return null;
//        }

        int targetPixelIndex = 0;

        SourceData data = new SourceData(4800, 4800);
        data.reset();

        for (int i = 0; i < products.length; i++) {
            Product sourceProduct = products[i];
            Product lcProduct = lcProducts[i];
//            Product jdSubset = getSubset(lon0, lat0, sourceProduct);
//            if (jdSubset == null || jdSubset.getSceneRasterWidth() <= 1 || jdSubset.getSceneRasterHeight() <= 1) {
//                continue;
//            }

//            Product lcSubset = getSubset(lon0, lat0, lcProducts);
//            Product lcSubset1 = getLcSubset(jdSubset, lcProducts);


            Mask mask = addMask(lon0, lat0, sourceProduct);

            Band lc = lcProduct.getBand("lccs_class");
            Band jd = sourceProduct.getBand("classification");
            Band cl = sourceProduct.getBand("uncertainty");
            Band no = sourceProduct.getBand("numObs1");

            int width = sourceProduct.getSceneRasterWidth();

            for (int lineIndex = 0; lineIndex < sourceProduct.getSceneRasterHeight(); lineIndex++) {

                int[] jdPixels = new int[width];
                float[] clPixels = new float[width];
                int[] lcPixels = new int[width];
                int[] numObsPixels = new int[width];

                int[] maskPixels = new int[width];
                mask.readPixels(0, lineIndex, width, 1, maskPixels);

                jd.readPixels(0, lineIndex, width, 1, jdPixels);
                if (cl != null) {
                    cl.readPixels(0, lineIndex, width, 1, clPixels);
                } else {
                    Arrays.fill(clPixels, 0.0F);
                }
                no.readPixels(0, lineIndex, width, 1, numObsPixels);
                lc.readPixels(0, lineIndex, width, 1, lcPixels);

                for (int x0 = 0; x0 < width; x0++) {
                    if (maskPixels[x0] == 0) {
                        continue;
                    }
                    int sourceJD = jdPixels[x0];
                    float sourceCL = clPixels[x0];
                    int sourceLC = lcPixels[x0];
                    int numbObs = numObsPixels[x0];

                    data.burnable[targetPixelIndex] = LcRemapping.isInBurnableLcClass(sourceLC);
                    boolean isValidPixel = isValidPixel(doyFirstOfMonth, doyLastOfMonth, sourceJD);
                    if (isValidPixel) {
                        // set burned pixel value consistently with CL value -- both if burned pixel is valid
                        data.burnedPixels[targetPixelIndex] = sourceJD;
                        data.probabilityOfBurn[targetPixelIndex] = sourceCL;
                    }

                    data.lcClasses[targetPixelIndex] = sourceLC;
                    if (numbObs == 0) {
                        data.statusPixels[targetPixelIndex] = 1;
                    } else {
                        data.statusPixels[targetPixelIndex] = 0;
                    }

                    data.areas[targetPixelIndex] = MODIS_AREA_SIZE;
                    targetPixelIndex++;
                }
            }
        }

//        if (x == 12 && y == 0) {
//            try {
//                Thread.sleep(1000*60*10);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }

        data.patchCount = getPatchNumbers(GridFormatUtils.make2Dims(data.burnedPixels, 4800, 4800), GridFormatUtils.make2Dims(data.burnable, 4800, 4800));
        return data;
    }

    private static Mask addMask(double lon0, double lat0, Product jd) {
        Mask mask = new Mask("mask", jd.getSceneRasterWidth(), jd.getSceneRasterHeight(), Mask.VectorDataType.INSTANCE);
        VectorDataNode vdn = createVDN(getWktString(lon0, lat0), jd);
        Mask.VectorDataType.setVectorData(mask, vdn);
        jd.getMaskGroup().add(mask);
        jd.getVectorDataGroup().add(vdn);
        vdn.setOwner(jd);
        mask.setOwner(jd);
        return mask;
    }

    private static VectorDataNode createVDN(String wkt, Product p) {
        SimpleFeatureType wktFeatureType = PlainFeatureFactory.createDefaultFeatureType(p.getSceneGeoCoding().getGeoCRS());
        ListFeatureCollection newCollection = new ListFeatureCollection(wktFeatureType);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(wktFeatureType);
        SimpleFeature wktFeature = featureBuilder.buildFeature("ID" + Long.toHexString(0L));
        Geometry geometry;
        try {
            geometry = new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
        wktFeature.setDefaultGeometry(geometry);
        newCollection.add(wktFeature);

        FeatureCollection<SimpleFeatureType, SimpleFeature> productFeatures = FeatureUtils.clipFeatureCollectionToProductBounds(
                newCollection,
                p,
                null,
                ProgressMonitor.NULL);
        return new VectorDataNode("geo", productFeatures);
    }

    protected static double getTopLat(int y, String tile) {
        return 90 - Integer.parseInt(tile.split(",")[1]) / 4.0 - y * 0.25;
    }

    protected static double getLeftLon(int x, String tile) {
        return -180 + Integer.parseInt(tile.split(",")[0]) / 4.0 + x * 0.25;
    }

    private Product getLcSubset(Product sourceProduct, Product lcProduct) {
        ReprojectionOp reprojectionOp = new ReprojectionOp();
        reprojectionOp.setParameterDefaultValues();
        reprojectionOp.setSourceProduct("collocationProduct", sourceProduct);
        reprojectionOp.setSourceProduct(lcProduct);
        return reprojectionOp.getTargetProduct();
    }


    private Product getSubset(double lon0, double lat0, Product sourceProduct) {
        SubsetOp subsetOp = new SubsetOp();
        Geometry geometry;
        String polygonString;
        try {
            polygonString = getWktString(lon0, lat0);
            geometry = new WKTReader().read(polygonString);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }

//        ReprojectionOp reprojectionOp = new ReprojectionOp();
//        reprojectionOp.setParameterDefaultValues();
//        reprojectionOp.setSourceProduct(sourceProduct);
//        reprojectionOp.setParameter("crs", "EPSG:4326");
//        Product targetProduct1 = reprojectionOp.getTargetProduct();
//
        subsetOp.setGeoRegion(geometry);
        subsetOp.setSourceProduct(sourceProduct);
        Product targetProduct = null;
        try {
            targetProduct = subsetOp.getTargetProduct();
        } catch (OperatorException exception) {
            if (exception.getMessage().contains("No intersection with source product boundary")) {
                // ignore - not all products are contained in each grid cell
                return null;
            }
        }
        if (targetProduct == null || targetProduct.getSceneRasterWidth() == 0 || targetProduct.getSceneRasterHeight() == 0) {
            return null;
        }
        return targetProduct;
    }

    private static String getWktString(double lon0, double lat0) {
        return String.format("POLYGON ((%s %s, %s %s, %s %s, %s %s, %s %s))",
                lon0, lat0,
                lon0 + 0.25, lat0,
                lon0 + 0.25, lat0 - 0.25,
                lon0, lat0 - 0.25,
                lon0, lat0);
    }
}
