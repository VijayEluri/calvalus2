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

package org.esa.beam.binning;

import com.bc.ceres.binding.BindingException;
import com.bc.ceres.binding.ConversionException;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.ParameterBlockConverter;

import java.text.MessageFormat;
import java.text.ParseException;

/**
 * The configuration of the L3 formatter
 */
public class OutputterConfig {

    public static class BandConfiguration {
        public String index;
        public String name;
        public String v1;
        public String v2;
    }

    @Parameter
    private String outputType;
    @Parameter
    private String outputFile;
    @Parameter
    private String outputFormat;
    @Parameter(itemAlias = "band")
    private BandConfiguration[] bands;
    // todo - remove
    @Parameter
    private String startTime;
    // todo - remove
    @Parameter
    private String endTime;

    public OutputterConfig() {
        // used by DOM converter
    }

    public OutputterConfig(String outputType,
                           String outputFile,
                           String outputFormat,
                           BandConfiguration[] bands,
                           // todo - remove
                           String startTime,
                           // todo - remove
                           String endTime) {
        this.outputType = outputType;
        this.outputFile = outputFile;
        this.outputFormat = outputFormat;
        this.bands = bands;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Creates a new formatter configuration object.
     *
     * @param xml The configuration as an XML string.
     * @return The new formatter configuration object.
     * @throws com.bc.ceres.binding.BindingException
     *          If the XML cannot be converted to a new formatter configuration object
     */
    public static OutputterConfig fromXml(String xml) throws BindingException {
        return new ParameterBlockConverter().convertXmlToObject(xml, new OutputterConfig());
    }

    public String toXml() {
        try {
            return new ParameterBlockConverter().convertObjectToXml(this);
        } catch (ConversionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getOutputType() {
        if (outputType == null) {
            throw new IllegalArgumentException("No output type given");
        }
        if (!outputType.equalsIgnoreCase("Product")
                && !outputType.equalsIgnoreCase("RGB")
                && !outputType.equalsIgnoreCase("Grey")) {
            throw new IllegalArgumentException("Unknown output type: " + outputType);
        }
        return outputType;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public BandConfiguration[] getBands() {
        return bands;
    }

    // todo - remove
    public ProductData.UTC getStartTime() {
        return parseTime(startTime, "startTime");
    }

    // todo - remove
    public ProductData.UTC getEndTime() {
        return parseTime(endTime, "endTime");
    }

    private static ProductData.UTC parseTime(String timeString, String timeName) {
        if (timeString == null) {
            throw new IllegalArgumentException(MessageFormat.format("Parameter: {0} not given.", timeName));
        }
        try {
            return ProductData.UTC.parse(timeString, "yyyy-MM-dd");
        } catch (ParseException e) {
            throw new IllegalArgumentException("Illegal start date format.", e);
        }

    }
}