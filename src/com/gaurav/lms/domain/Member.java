package com.gaurav.lms.domain;

/** A patron who can borrow and reserve books. */
public final class Member extends User {
    private boolean active = true;

    public Member(String id, String name) {
        super(id, name);
    }

    public boolean isActive() { return active; }

    public void deactivate() {
        active = false;
    }
}
