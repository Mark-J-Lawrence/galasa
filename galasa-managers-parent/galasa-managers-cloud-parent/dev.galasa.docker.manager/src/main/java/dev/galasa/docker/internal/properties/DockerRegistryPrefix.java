/*
 * Copyright contributors to the Galasa project
 */
package dev.galasa.docker.internal.properties;

import dev.galasa.docker.DockerManagerException;
import dev.galasa.docker.internal.DockerRegistryImpl;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.cps.CpsProperties;

/**
 * Docker Registry Prefix CPS Property
 * 
 * @galasa.cps.property
 * 
 * @galasa.name docker.registry.ID.prefix
 * 
 * @galasa.description Provides a prefix to be applied to all image names, particularly useful if you have a dockerhub proxy.
 * 
 * @galasa.default None
 * 
 * @galasa.valid_values A valid String
 * 
 * @galasa.examples 
 * <code>docker.registry.LOCAL.prefix=dockerhub/</code>
 */
public class DockerRegistryPrefix extends CpsProperties {

    public static String get(DockerRegistryImpl dockerRegistry) throws DockerManagerException {
        String id = dockerRegistry.getId();
        String dockerRegistryPrefix = "";
        try {
            dockerRegistryPrefix = getStringNulled(DockerPropertiesSingleton.cps(), "registry", "image.prefix", id);
            // Default value
            if (dockerRegistryPrefix == null) {
            	return "";
            }
            return dockerRegistryPrefix + "/";
        } catch (ConfigurationPropertyStoreException e) {
            throw new DockerManagerException("Problem asking the CPS for the docker registry type", e);
        }
    }
}
