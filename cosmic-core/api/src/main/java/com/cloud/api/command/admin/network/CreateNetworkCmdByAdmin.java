package com.cloud.api.command.admin.network;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.user.network.CreateNetworkCmd;
import com.cloud.api.response.NetworkResponse;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.Network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createNetwork", group = "Network", description = "Creates a network", responseObject = NetworkResponse.class, responseView = ResponseView.Full, entityType = {Network.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateNetworkCmdByAdmin extends CreateNetworkCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateNetworkCmdByAdmin.class.getName());

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    // an exception thrown by createNetwork() will be caught by the dispatcher.
    public void execute() throws InsufficientCapacityException, ConcurrentOperationException, ResourceAllocationException {
        final Network result = _networkService.createGuestNetwork(this);
        if (result != null) {
            final NetworkResponse response = _responseGenerator.createNetworkResponse(ResponseView.Full, result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create network");
        }
    }
}
