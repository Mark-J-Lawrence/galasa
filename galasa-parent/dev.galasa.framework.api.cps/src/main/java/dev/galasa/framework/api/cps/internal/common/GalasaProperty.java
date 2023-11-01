/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.api.cps.internal.common;

public class GalasaProperty {
    String apiVersion = "v1alpha1";
    final String kind = "GalasaProperty"; 
    Metadata metadata ;
    Data data;
    
    public class Metadata {
        String namespace;
        String name;
        
        public Metadata (String namespace, String name){
            this.namespace = namespace;
            this.name = name;
        }
    }

    public class Data {
        String value;
        
        public Data (String propertyValue){
            this.value = propertyValue;
        }
    }

    public GalasaProperty (String completeCPSname, String propertyValue){
        String[] name = completeCPSname.split("[.]", 2);
        this.metadata = new Metadata(name[0],name[1]);
        this.data = new Data(propertyValue);
    }

    public GalasaProperty (String namespace, String propertyName, String propertyValue){
        this.metadata = new Metadata(namespace, propertyName);
        this.data = new Data(propertyValue);
    }

    public GalasaProperty (String namespace, String propertyName, String propertyValue, String apiVersion){
        this(namespace, propertyName, propertyValue);
        this.apiVersion = apiVersion;
    }
}
