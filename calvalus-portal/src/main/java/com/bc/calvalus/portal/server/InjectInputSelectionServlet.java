package com.bc.calvalus.portal.server;

import com.bc.calvalus.portal.shared.DtoInputSelection;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Principal;

/**
 * @author hans
 */
public class InjectInputSelectionServlet extends HttpServlet {

    private static final String CATALOGUE_SEARCH_PREFIX = "catalogueSearch_";

    @Override
    protected void doPost(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
                throws IOException {
        StringWriter writer = new StringWriter();
        try (ServletInputStream inputStream = servletRequest.getInputStream()) {
            IOUtils.copy(inputStream, writer);
        }
        String inputSelectionString = writer.toString();
        Gson gson = new Gson();
        DtoInputSelection dtoInputSelection = gson.fromJson(inputSelectionString, DtoInputSelection.class);
        Principal userPrincipal = servletRequest.getUserPrincipal();
        if (userPrincipal != null) {
            getServletContext().setAttribute(CATALOGUE_SEARCH_PREFIX + userPrincipal.getName(), dtoInputSelection);
        }
    }
}
