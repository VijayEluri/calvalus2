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

package com.bc.calvalus.production.hadoop;

import com.bc.calvalus.inventory.InventoryService;
import com.bc.calvalus.processing.hadoop.HadoopProcessingService;
import com.bc.calvalus.production.Production;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.production.ProductionRequest;
import com.bc.calvalus.production.ProductionType;
import com.bc.calvalus.staging.Staging;
import com.bc.calvalus.staging.StagingService;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Abstract base class for production types that require a Hadoop processing system.
 * <p/>
 * <i>Important TODOs</i>
 * <ol>
 * <li>todo - Rename HadoopWorkflowItem --> ProcessingStep (mz, nf, 2011.08.15)</li>
 * <li>todo - Express in API that the HadoopProductionType is responsible for setting up the workflow comprising one or more ProcessingSteps (mz, nf, 2011.08.15)</li>
 * <li>todo - Express in API that the HadoopProductionType is responsible for converting a ProductionRequest into processing parameters for each ProcessingStep (mz, nf, 2011.08.15)</li>
 * <li>todo - Constructors of ProcessingStep shall use a job configuration to pass processing parameters (mz, nf, 2011.08.15)</li>
 * <li>todo - Use {@link #serializeProductionRequest} method to serialize the productionRequest into the Hadoop job configuration (mz, nf, 2011.08.15)</li>
 * </ol>
 *
 * @author MarcoZ
 * @author Norman
 */
public abstract class HadoopProductionType implements ProductionType {
    private final String name;
    private final InventoryService inventoryService;
    private final HadoopProcessingService processingService;
    private final StagingService stagingService;

    protected HadoopProductionType(String name,
                                   InventoryService inventoryService,
                                   HadoopProcessingService processingService,
                                   StagingService stagingService) {
        this.name = name;
        this.inventoryService = inventoryService;
        this.processingService = processingService;
        this.stagingService = stagingService;
    }

    public InventoryService getInventoryService() {
        return inventoryService;
    }

    public HadoopProcessingService getProcessingService() {
        return processingService;
    }

    public StagingService getStagingService() {
        return stagingService;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean accepts(ProductionRequest productionRequest) {
        return getName().equalsIgnoreCase(productionRequest.getProductionType());
    }

    @Override
    public Staging createStaging(Production production) throws ProductionException {
        Staging staging = createUnsubmittedStaging(production);
        try {
            getStagingService().submitStaging(staging);
        } catch (IOException e) {
            throw new ProductionException(String.format("Failed to order staging for production '%s': %s",
                                                        production.getId(), e.getMessage()), e);
        }
        return staging;
    }

    protected abstract Staging createUnsubmittedStaging(Production production);


    public String[] getInputPaths(String inputPathPattern, Date minDate, Date maxDate) throws ProductionException {
        InputPathResolver inputPathResolver = new InputPathResolver();
        inputPathResolver.setMinDate(minDate);
        inputPathResolver.setMaxDate(maxDate);
        List<String> inputPatterns = inputPathResolver.resolve(inputPathPattern);
        try {
            return inventoryService.getDataInputPaths(inputPatterns);
        } catch (IOException e) {
            throw new ProductionException("Failed to compute input file list.", e);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    protected void serializeProductionRequest(ProductionRequest productionRequest, Configuration jobConfiguration) {
        HadoopProductionServiceFactory.transferConfiguration(productionRequest.getParameters(), jobConfiguration);
        jobConfiguration.set("calvalus.productionRequest.productionType", productionRequest.getProductionType());
        jobConfiguration.set("calvalus.productionRequest.userName", productionRequest.getUserName());
    }
}
