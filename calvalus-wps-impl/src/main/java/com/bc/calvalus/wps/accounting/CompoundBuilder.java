package com.bc.calvalus.wps.accounting;

import com.bc.calvalus.wps.accounting.Compound;

public class CompoundBuilder {
    private String id;
    private String name;
    private String type;
    private String uri;
    private String timestamp;

    private CompoundBuilder() {
    }

    public static CompoundBuilder create() {
        return new CompoundBuilder();
    }

    public Compound build() {
        return new Compound(this);
    }

    public CompoundBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public CompoundBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public CompoundBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public CompoundBuilder withUri(String uri) {
        this.uri = uri;
        return this;
    }

    public CompoundBuilder withTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public String getUri() {
        return this.uri;
    }

    public String getTimestamp() {
        return this.timestamp;
    }
}