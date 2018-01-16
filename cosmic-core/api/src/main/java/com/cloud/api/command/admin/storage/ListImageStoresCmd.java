package com.cloud.api.command.admin.storage;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ImageStoreResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ZoneResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listImageStores", group = "Image Store", description = "Lists image stores.", responseObject = ImageStoreResponse.class, since = "4.2.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListImageStoresCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListImageStoresCmd.class.getName());

    private static final String s_name = "listimagestoresresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the image store")
    private String storeName;

    @Parameter(name = ApiConstants.PROTOCOL, type = CommandType.STRING, description = "the image store protocol")
    private String protocol;

    @Parameter(name = ApiConstants.PROVIDER, type = CommandType.STRING, description = "the image store provider")
    private String provider;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the Zone ID for the image store")
    private Long zoneId;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ImageStoreResponse.class, description = "the ID of the storage pool")
    private Long id;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getZoneId() {
        return zoneId;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getProtocol() {
        return protocol;
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(final String provider) {
        this.provider = provider;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ListResponse<ImageStoreResponse> response = _queryService.searchForImageStores(this);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
