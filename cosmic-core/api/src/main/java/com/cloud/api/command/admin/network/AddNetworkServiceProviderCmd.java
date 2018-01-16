package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.PhysicalNetworkResponse;
import com.cloud.api.response.ProviderResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.user.Account;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "addNetworkServiceProvider", group = "Network",
        description = "Adds a network serviceProvider to a physical network",
        responseObject = ProviderResponse.class,
        since = "3.0.0",
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class AddNetworkServiceProviderCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(AddNetworkServiceProviderCmd.class.getName());

    private static final String s_name = "addnetworkserviceproviderresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            required = true,
            description = "the Physical Network ID to add the provider to")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.DEST_PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            description = "the destination Physical Network ID to bridge to")
    private Long destinationPhysicalNetworkId;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name for the physical network service provider")
    private String name;

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
        CallContext.current().setEventDetails("Network ServiceProvider Id: " + getEntityId());
        final PhysicalNetworkServiceProvider result = _networkService.getCreatedPhysicalNetworkServiceProvider(getEntityId());
        if (result != null) {
            final ProviderResponse response = _responseGenerator.createNetworkServiceProviderResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to add service provider to physical network");
        }
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
    public void create() throws ResourceAllocationException {
        final PhysicalNetworkServiceProvider result =
                _networkService.addProviderToPhysicalNetwork(getPhysicalNetworkId(), getProviderName(), getDestinationPhysicalNetworkId(), getEnabledServices());
        if (result != null) {
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to add service provider entity to physical network");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public String getProviderName() {
        return name;
    }

    public Long getDestinationPhysicalNetworkId() {
        return destinationPhysicalNetworkId;
    }

    public List<String> getEnabledServices() {
        return enabledServices;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_SERVICE_PROVIDER_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Adding physical network ServiceProvider: " + getEntityId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.PhysicalNetworkServiceProvider;
    }
}
