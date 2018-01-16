package com.cloud.api.command.user.network;

import com.cloud.acl.SecurityChecker.AccessType;
import com.cloud.api.ACL;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.utils.exception.InvalidParameterValueException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "restartNetwork", group = "Network",
        description = "Restarts the network; includes 1) restarting network elements - virtual routers, DHCP servers 2) reapplying all public IPs 3) reapplying " +
                "loadBalancing/portForwarding rules",
        responseObject = IPAddressResponse.class, entityType = {Network.class},
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class RestartNetworkCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(RestartNetworkCmd.class.getName());
    private static final String s_name = "restartnetworkresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @ACL(accessType = AccessType.OperateEntry)
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = NetworkResponse.class, required = true, description = "The ID of the network to restart.")
    private Long id;

    @Parameter(name = ApiConstants.CLEANUP, type = CommandType.BOOLEAN, required = false, description = "If cleanup old network elements")
    private Boolean cleanup;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public static String getResultObjectName() {
        return "addressinfo";
    }

    @Override
    public void execute() throws ResourceUnavailableException, ResourceAllocationException, ConcurrentOperationException, InsufficientCapacityException {
        final boolean result = _networkService.restartNetwork(this, getCleanup());
        if (result) {
            final SuccessResponse response = new SuccessResponse(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to restart network");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Boolean getCleanup() {
        if (cleanup != null) {
            return cleanup;
        }
        return true;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Network network = _networkService.getNetwork(id);
        if (network == null) {
            throw new InvalidParameterValueException("Networkd ID=" + id + " doesn't exist");
        } else {
            return _networkService.getNetwork(id).getAccountId();
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_NETWORK_RESTART;
    }

    @Override
    public String getEventDescription() {
        return "Restarting network: " + getNetworkId();
    }

    public Long getNetworkId() {
        final Network network = _networkService.getNetwork(id);
        if (network == null) {
            throw new InvalidParameterValueException("Unable to find network by ID " + id);
        } else {
            return network.getId();
        }
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return id;
    }
}
