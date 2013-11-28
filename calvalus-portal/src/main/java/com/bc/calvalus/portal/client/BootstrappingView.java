/*
 * Copyright (C) 2013 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.portal.client;

import com.bc.calvalus.production.hadoop.ProcessorProductionRequest;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistical bootstrapping for data sets
 */
public class BootstrappingView extends OrderProductionView {


    private final VerticalPanel widget;
    private final BootstrappingForm bootstrappingForm;

    public BootstrappingView(PortalContext portalContext) {
        super(portalContext);

        this.bootstrappingForm = new BootstrappingForm(portalContext);
        VerticalPanel panel = new VerticalPanel();
        panel.setWidth("100%");
        panel.add(bootstrappingForm);
        panel.add(new HTML("<br/>"));
        panel.add(createOrderPanel());

        this.widget = panel;

    }

    @Override
    protected String getProductionType() {
        return "Bootstrapping";
    }

    @Override
    protected Map<String, String> getProductionParameters() {
        HashMap<String, String> parameters = new HashMap<String, String>();

        parameters.put(ProcessorProductionRequest.PROCESSOR_BUNDLE_NAME, "bootstrapping");
        parameters.put(ProcessorProductionRequest.PROCESSOR_BUNDLE_VERSION, "1.0");
        //parameters.put(ProcessorProductionRequest.PROCESSOR_BUNDLE_LOCATION, "???");
        parameters.put(ProcessorProductionRequest.PROCESSOR_NAME, "r-bootstrap");
        //parameters.put(ProcessorProductionRequest.PROCESSOR_PARAMETERS, "???");

        parameters.put("calvalus.bootstrap.numberOfIterations", bootstrappingForm.numberOfIterations.getText());

        parameters.put("calvalus.bootstrap.inputFile", "/calvalus/home/marcop/boostrapping_input.csv");

        parameters.put("productionName", bootstrappingForm.productionName.getValue());

        return parameters;
    }

    @Override
    protected boolean validateForm() {
        Integer numIterValue = bootstrappingForm.numberOfIterations.getValue();
        return numIterValue != null && numIterValue > 0;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getTitle() {
        return "Bootstrapping";
    }

}
