package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.ProviderResponse;
import com.cloud.event.EventTypes;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.user.Account;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateNetworkServiceProvider", group = "Network",
        description = "Updates a network serviceProvider of a physical network",
        responseObject = ProviderResponse.class,
        since = "3.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class UpdateNetworkServiceProviderCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateNetworkServiceProviderCmd.class.getName());

    private static final String s_name = "updatenetworkserviceproviderresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.STATE, type = CommandType.STRING, description = "Enabled/Disabled/Shutdown the physical network service provider")
    private String state;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = ProviderResponse.class, required = true, description = "network service provider id")
    private Long id;

    @Parameter(name = ApiConstants.SERVICE_LIST,
            type = CommandType.LIST,
            collectionType = CommandType.STRING,
            description = "the list of services to be enabled for this physical network service provider")
    private List<String> enabledServices;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final PhysicalNetworkServiceProvider result = _networkService.updateNetworkServiceProvider(getId(), getState(), getEnabledServices());
        if (result != null) {
            final ProviderResponse response = _responseGenerator.createNetworkServiceProviderResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update service provider");
        }
    }

    private Long getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public List<String> getEnabledServices() {
        return enabledServices;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_SERVICE_PROVIDER_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating physical network ServiceProvider: " + getId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.PhysicalNetworkServiceProvider;
    }
}
