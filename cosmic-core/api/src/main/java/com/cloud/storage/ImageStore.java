package com.cloud.storage;

import com.cloud.legacymodel.Identity;
import com.cloud.legacymodel.InternalIdentity;

public interface ImageStore extends Identity, InternalIdentity {

    /**
     * @return name of the object store.
     */
    String getName();

    /**
     * @return availability zone.
     */
    Long getDataCenterId();

    /**
     * @return object store provider name
     */
    String getProviderName();

    /**
     * @return data store protocol
     */
    String getProtocol();
}
