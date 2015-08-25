//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.08.11 at 03:37:15 PM CEST 
//


package com.bc.calvalus.wpsrest.jaxb;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for typeType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="typeType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}token">
 *     &lt;enumeration value="simple"/>
 *     &lt;enumeration value="extended"/>
 *     &lt;enumeration value="title"/>
 *     &lt;enumeration value="resource"/>
 *     &lt;enumeration value="locator"/>
 *     &lt;enumeration value="arc"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "typeType")
@XmlEnum
public enum TypeType {

    @XmlEnumValue("simple")
    SIMPLE("simple"),
    @XmlEnumValue("extended")
    EXTENDED("extended"),
    @XmlEnumValue("title")
    TITLE("title"),
    @XmlEnumValue("resource")
    RESOURCE("resource"),
    @XmlEnumValue("locator")
    LOCATOR("locator"),
    @XmlEnumValue("arc")
    ARC("arc");
    private final String value;

    TypeType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TypeType fromValue(String v) {
        for (TypeType c: TypeType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
