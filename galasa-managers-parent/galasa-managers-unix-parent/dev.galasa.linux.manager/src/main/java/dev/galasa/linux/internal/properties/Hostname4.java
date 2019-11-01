/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.linux.internal.properties;

import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.cps.CpsProperties;
import dev.galasa.linux.LinuxManagerException;

/**
 * Linux Hostname IPV4
 * <p>
 * The IPV4 hostname of the Linux DSE server
 * </p>
 * <p>
 * The property is:-<br>
 * <br>
 * linux.dse.tag.[tag].hostname4=cics.ibm.com
 * </p>
 * <p>
 * There is no default
 * </p>
 * 
 * @author Michael Baylis
 *
 */
public class Hostname4 extends CpsProperties {

    public static String get(String tag) throws LinuxManagerException, ConfigurationPropertyStoreException {
        return getStringNulled(LinuxPropertiesSingleton.cps(), "dse.tag." + tag, "hostname4");
    }

}
