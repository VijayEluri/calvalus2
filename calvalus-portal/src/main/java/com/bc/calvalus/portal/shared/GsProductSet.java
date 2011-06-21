package com.bc.calvalus.portal.shared;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * GWT-serializable version of the {@link com.bc.calvalus.catalogue.ProductSet} class.
 *
 * @author Norman
 */
public class GsProductSet implements IsSerializable {
    private String path;
    private String type;
    private String name;

    /**
     * No-arg constructor as required by {@link IsSerializable}. Don't use directly.
     */
    public GsProductSet() {
        this(null, "", "");
    }

    public GsProductSet(String id, String type, String name) {
        this.path = id;
        this.type = type;
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
