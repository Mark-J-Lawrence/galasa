/*
 * Copyright (c) 2019 IBM Corporation.
 */
package dev.galasa.zos3270.internal.datastream;

public class CommandEraseWrite extends CommandCode {

	public byte[] getBytes() {
		return new byte[] {ERASE_WRITE};
	}
	
}
