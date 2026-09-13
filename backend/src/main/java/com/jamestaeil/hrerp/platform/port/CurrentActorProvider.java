package com.jamestaeil.hrerp.platform.port;

public interface CurrentActorProvider {
    /** Return the authenticated stable account ID, never a request-supplied identity. */
    long requireActorId();
}
