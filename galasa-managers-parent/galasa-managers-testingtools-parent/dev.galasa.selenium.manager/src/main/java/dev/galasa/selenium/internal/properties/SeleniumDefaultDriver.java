/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2021.
 */
package dev.galasa.selenium.internal.properties;

import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.cps.CpsProperties;
import dev.galasa.selenium.SeleniumManagerException;

/**
 * Selenium Default Driver CPS Property
 * 
 * @galasa.cps.property
 * 
 * @galasa.name selenium.default.driver
 * 
 * @galasa.description Describes default the selenium driver that will be used.
 * 
 * @galasa.required No
 * 
 * @galasa.valid_values A valid String representation of a type. Available choices: local, docker, kubernetes, grid
 * 
 * @galasa.examples 
 * <code>selenium.default.driver=firefox</code>
 * 
 */
public class SeleniumDefaultDriver extends CpsProperties {
    
    public static String get() throws ConfigurationPropertyStoreException, SeleniumManagerException {
        String driver = getStringNulled(SeleniumPropertiesSingleton.cps(), "default", "driver");
        if (driver != null) {
            return driver;
        }
        throw new SeleniumManagerException("No default, set 'selenium.default.driver'.");
    }

}