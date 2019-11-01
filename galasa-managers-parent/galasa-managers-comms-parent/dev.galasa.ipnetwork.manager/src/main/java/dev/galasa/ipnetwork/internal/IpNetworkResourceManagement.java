/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.ipnetwork.internal;

import java.util.concurrent.TimeUnit;

import org.osgi.service.component.annotations.Component;

import dev.galasa.framework.spi.IConfigurationPropertyStoreService;
import dev.galasa.framework.spi.IDynamicStatusStoreService;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IResourceManagement;
import dev.galasa.framework.spi.IResourceManagementProvider;
import dev.galasa.framework.spi.ResourceManagerException;

@Component(service= {IResourceManagementProvider.class})
public class IpNetworkResourceManagement implements IResourceManagementProvider {

	private IFramework                         framework;
	private IResourceManagement                resourceManagement;
	private IDynamicStatusStoreService         dss;
	private IConfigurationPropertyStoreService cps;
	
	private PortResourceMonitor                portResourceMonitor;
	
	@Override
	public boolean initialise(IFramework framework, IResourceManagement resourceManagement) throws ResourceManagerException {
		this.framework = framework;
		this.resourceManagement = resourceManagement;
		try {
			this.dss = this.framework.getDynamicStatusStoreService("ipnetwork");
			this.cps = this.framework.getConfigurationPropertyService("ipnetwork");
		} catch (Exception e) {
			throw new ResourceManagerException("Unable to initialise IP Network resource monitor", e);
		}
		
		// TODO Must add a check every 10 minutes to tidy up all the properties that may have been left hanging
		
		portResourceMonitor = new PortResourceMonitor(framework, resourceManagement, dss, this, cps);
		
		return true;
	}

	@Override
	public void start() {
		this.resourceManagement.getScheduledExecutorService().scheduleWithFixedDelay(portResourceMonitor, 
				this.framework.getRandom().nextInt(20),
				20, 
				TimeUnit.SECONDS);
	}

	@Override
	public void shutdown() {
	}

	@Override
	public void runFinishedOrDeleted(String runName) {
		this.portResourceMonitor.runFinishedOrDeleted(runName);
	}

}
