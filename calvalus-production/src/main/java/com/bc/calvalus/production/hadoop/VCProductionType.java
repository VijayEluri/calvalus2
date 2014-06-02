/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.commons.DateRange;
import com.bc.calvalus.commons.Workflow;
import com.bc.calvalus.commons.WorkflowItem;
import com.bc.calvalus.inventory.InventoryService;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.processing.ma.MAConfig;
import com.bc.calvalus.processing.vc.VCWorkflowItem;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.production.ProductionType;
import com.bc.calvalus.staging.Staging;
import com.bc.calvalus.staging.StagingService;
import com.vividsolutions.jts.geom.Geometry;
import org.apache.hadoop.conf.Configuration;
import org.esa.beam.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * Vicarious Calibration: A production type used for supporting the computation of vicarious calibration coefficients
 *
 * @author MarcoZ
 */
public class VCProductionType extends HadoopProductionType {

    public static class Spi extends HadoopProductionType.Spi {

        @Override
        public ProductionType create(InventoryService inventory, HadoopProcessingService processing, StagingService staging) {
            return new VCProductionType(inventory, processing, staging);
        }
    }

    VCProductionType(InventoryService inventoryService, HadoopProcessingService processingService,
                     StagingService stagingService) {
        super("VC", inventoryService, processingService, stagingService);
    }

    @Override
    public Production createProduction(ProductionRequest productionRequest) throws ProductionException {

        final String productionId = Production.createId(productionRequest.getProductionType());
        String defaultProductionName = createProductionName("Vicarious Calibration ", productionRequest);
        final String productionName = productionRequest.getProductionName(defaultProductionName);

        Geometry regionGeometry = productionRequest.getRegionGeometry(null);
        String geometryWKT = regionGeometry != null ? regionGeometry.toString() : "";
        String regionName = productionRequest.getRegionName();
        String dataRanges = StringUtils.join(productionRequest.getDateRanges(), ",");
        String level1Input = productionRequest.getString("inputPath");

        ///////////////////////////////////////////////////////////////////////////////////////////
        Configuration jobConfig = createJobConfig(productionRequest);

        jobConfig.set(JobConfigNames.CALVALUS_INPUT_PATH_PATTERNS, level1Input);
        jobConfig.set(JobConfigNames.CALVALUS_INPUT_REGION_NAME, regionName);
        jobConfig.set(JobConfigNames.CALVALUS_INPUT_DATE_RANGES, dataRanges);

        String outputDir = getOutputPath(productionRequest, productionId, "");
        jobConfig.set(JobConfigNames.CALVALUS_OUTPUT_DIR, outputDir);
        jobConfig.set(JobConfigNames.CALVALUS_REGION_GEOMETRY, geometryWKT);

        MAConfig maConfig = MAProductionType.getMAConfig(productionRequest);
        jobConfig.set(JobConfigNames.CALVALUS_MA_PARAMETERS, maConfig.toXml());

        ///////////////////////////////////////////////////////////////////////////////////////////
        ProcessorProductionRequest pprDifferentiation = new ProcessorProductionRequest(productionRequest,
                                                                                       VCWorkflowItem.DIFFERENTIATION_SUFFIX);
        setDefaultProcessorParameters(pprDifferentiation, jobConfig);
        setRequestParameters(productionRequest, jobConfig);
        pprDifferentiation.configureProcessor(jobConfig);

        ProcessorProductionRequest pprL2 = new ProcessorProductionRequest(productionRequest);
        setDefaultProcessorParameters(pprL2, jobConfig);
        setRequestParameters(productionRequest, jobConfig);
        pprL2.configureProcessor(jobConfig);


        WorkflowItem workflow = new VCWorkflowItem(getProcessingService(), productionRequest.getUserName(), productionName, jobConfig);

        String stagingDir = productionRequest.getStagingDirectory(productionId);
        boolean autoStaging = productionRequest.isAutoStaging();
        return new Production(productionId,
                              productionName,
                              outputDir,
                              stagingDir,
                              autoStaging,
                              productionRequest,
                              workflow);
    }

    @Override
    protected Staging createUnsubmittedStaging(Production production) throws IOException {
        return new CopyStaging(production,
                               getProcessingService().getJobClient(production.getProductionRequest().getUserName()).getConf(),
                               getStagingService().getStagingDir());
    }
}
