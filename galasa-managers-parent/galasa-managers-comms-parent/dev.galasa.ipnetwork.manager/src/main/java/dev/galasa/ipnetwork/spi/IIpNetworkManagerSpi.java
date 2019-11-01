/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.ipnetwork.spi;

import java.nio.file.FileSystem;

import javax.validation.constraints.NotNull;

import dev.galasa.ICredentials;
import dev.galasa.ipnetwork.ICommandShell;
import dev.galasa.ipnetwork.IIpHost;
import dev.galasa.ipnetwork.IpNetworkManagerException;

public interface IIpNetworkManagerSpi {

	@NotNull
	ICommandShell getCommandShell(IIpHost host, ICredentials credentials) throws IpNetworkManagerException;

	@NotNull
	FileSystem getFileSystem(IIpHost host) throws IpNetworkManagerException;

}
