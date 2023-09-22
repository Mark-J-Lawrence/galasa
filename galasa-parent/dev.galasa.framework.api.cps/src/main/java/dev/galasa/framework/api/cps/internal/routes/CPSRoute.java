/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.api.cps.internal.routes;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import dev.galasa.framework.ResourceNameValidator;
import dev.galasa.framework.api.common.BaseRoute;
import dev.galasa.framework.api.common.ResponseBuilder;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.utils.GalasaGsonBuilder;


/**
 * An abstract route used by all the Run-related routes.
 */
public abstract class CPSRoute extends BaseRoute {

    static final ResourceNameValidator nameValidator = new ResourceNameValidator();
    static final Gson gson = GalasaGsonBuilder.build();

    // Define a default filter to accept everything
    static DirectoryStream.Filter<Path> defaultFilter = path -> { return true; };

    protected IFramework framework;

    private static final String REDACTED_PROPERTY_VALUE = "********";

    private final static Set<String> hiddenNamespaces = new HashSet<>();
    static {
        hiddenNamespaces.add("dss");
    }

    /**
     * Some namespaces are able to be set, but cannot be queried.
     *
     * When they are queried, the values are redacted
     */
    private final static Set<String> writeOnlyNamespaces = new HashSet<>();
    static {
        writeOnlyNamespaces.add("secure");
    }

        public CPSRoute(ResponseBuilder responseBuilder, String path , IFramework framework ) {
        super(responseBuilder, path);
        this.framework = framework;
    }

    protected boolean isHiddenNamespace(String namespace){
        return hiddenNamespaces.contains(namespace);
    }

    protected String getProtectedValue(String actualValue , String namespace) {
        String protectedValue ;
        if (writeOnlyNamespaces.contains(namespace)) {
            // The namespace is protected, write-only, so should not be readable.
            protectedValue = REDACTED_PROPERTY_VALUE;
        } else {
            protectedValue = actualValue ;
        }
        return protectedValue ;
    }

    protected IFramework getFramework() {
        return this.framework;
    }

    protected Map<String, String> getAllProperties(String namespace) throws ConfigurationPropertyStoreException {
        return framework.getConfigurationPropertyService(namespace).getAllProperties();
    }

    protected String getNamespaceFromURL(String pathInfo){
        String[] namespace = pathInfo.split("/");
        return namespace[2];
    }
}