package com.gaurav.lms.domain;

import com.gaurav.lms.util.Validation;

/** Common identity data for library roles. */
public abstract class User {
    private final String id;
    private final String name;

    protected User(String id, String name) {
        this.id = Validation.required(id, "id");
        this.name = Validation.required(name, "name");
    }

    public String id() { return id; }
    public String name() { return name; }
}
