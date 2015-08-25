package com.bc.calvalus.wpsrest.responses;


import com.bc.calvalus.wpsrest.jaxb.CodeType;
import com.bc.calvalus.wpsrest.jaxb.ExecuteResponse;
import com.bc.calvalus.wpsrest.jaxb.ExecuteResponse.ProcessOutputs;
import com.bc.calvalus.wpsrest.jaxb.OutputDataType;
import com.bc.calvalus.wpsrest.jaxb.OutputReferenceType;
import com.bc.calvalus.wpsrest.jaxb.StatusType;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by hans on 14/08/2015.
 */
public class ExecuteSuccessfulResponse {

    public ExecuteResponse getExecuteResponse(List<String> productionResultsUrls) throws DatatypeConfigurationException {
        ExecuteResponse executeResponse = new ExecuteResponse();

        StatusType statusType = new StatusType();
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        XMLGregorianCalendar currentTime = DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);
        statusType.setCreationTime(currentTime);
        statusType.setProcessSucceeded("The request has been processed successfully.");
        executeResponse.setStatus(statusType);

        ProcessOutputs productUrl = new ProcessOutputs();

        for(String productionResultsUrl : productionResultsUrls){
            OutputDataType url = new OutputDataType();
            CodeType outputId = new CodeType();
            outputId.setValue("productionResults");
            url.setIdentifier(outputId);
            OutputReferenceType urlLink = new OutputReferenceType();
            urlLink.setHref(productionResultsUrl);
            urlLink.setMimeType("binary");
            url.setReference(urlLink);

            productUrl.getOutput().add(url);
        }
        executeResponse.setProcessOutputs(productUrl);

        return executeResponse;
    }

}
