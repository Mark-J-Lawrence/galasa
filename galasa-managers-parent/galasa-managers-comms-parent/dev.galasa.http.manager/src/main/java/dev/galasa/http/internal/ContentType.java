/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.http.internal;

import org.apache.http.HttpEntity;

public enum ContentType {

	APPLICATION_XML(org.apache.http.entity.ContentType.APPLICATION_XML),
	APPLICATION_JSON(org.apache.http.entity.ContentType.APPLICATION_JSON),
	APPLICATION_OCTET_STREAM(org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM),
	MULTIPART_FORM_DATA(org.apache.http.entity.ContentType.MULTIPART_FORM_DATA),
	APPLICATION_FORM_URLENCODED(org.apache.http.entity.ContentType.APPLICATION_FORM_URLENCODED),
	TEXT_PLAIN(org.apache.http.entity.ContentType.TEXT_PLAIN),
	TEXT_HTML(org.apache.http.entity.ContentType.TEXT_HTML),
	TEXT_XML(org.apache.http.entity.ContentType.TEXT_XML),
	RDF_XML("application/rdf+xml"), 
	SOAP_XML("application/soap+xml");
	
	
	private final org.apache.http.entity.ContentType c;
	private final String custom;
	
	ContentType(org.apache.http.entity.ContentType c) {
		this.c = c;
		this.custom = null;
	}
	
	ContentType(String custom) {
		this.c = null;
		this.custom = custom;
	}
	
	protected org.apache.http.entity.ContentType getC() {
		return c;
	}
	
	protected String getCustom() {
		return custom;
	}
	
	protected static ContentType get(HttpEntity e) {
		
		String type = e.getContentType().getValue();
		
		for (ContentType ct : values()) {
			if (ct.c != null) {
				if (ct.c.getMimeType().equalsIgnoreCase(type)) {
					return ct;
				}
			} else {
				if (ct.custom.equalsIgnoreCase(type)) {
					return ct;
				}
			}
		}
		
		return null;
	}

	public String getMimeType() {
		if (c != null) {
			return c.getMimeType();
		} 
		return custom;
	}
	
	/**
	 * Get the {@link ContentType} for a mime type string
	 * 
	 * @param mimeTypeString
	 * @return the corresponding {@link ContentType}
	 */
	public static ContentType fromMimeTypeString(String mimeTypeString) {
		for (ContentType contentType : values()) {
			if (contentType.getMimeType().equalsIgnoreCase(mimeTypeString)) {
				return contentType;
			}
		}
		
		throw new IllegalArgumentException("No ContentType exists for mime type " + mimeTypeString);
	}
}
