package com.bc.calvalus.processing.ma;

import org.esa.beam.framework.datamodel.Product;
import org.junit.Test;

/**
 * @author MarcoZ
 * @author Norman
 */
public class PixelExtractorTest {

    @Test(expected = NullPointerException.class)
    public void testExtractDoesNotAcceptNullRecord() throws Exception {
        PixelExtractor extractor = new PixelExtractor(new TestHeader(), new Product("A", "B", 2, 2), 1, null, null, true);
        extractor.extract(null);
    }
}
