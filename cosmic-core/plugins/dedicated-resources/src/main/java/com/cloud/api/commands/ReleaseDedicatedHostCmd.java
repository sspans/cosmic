package com.cloud.api.commands;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.HostResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.dedicated.DedicatedService;
import com.cloud.event.EventTypes;
import com.cloud.user.Account;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "releaseDedicatedHost", group = "Affinity", description = "Release the dedication for host", responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ReleaseDedicatedHostCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ReleaseDedicatedHostCmd.class.getName());

    private static final String s_name = "releasededicatedhostresponse";
    @Inject
    DedicatedService dedicatedService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.HOST_ID, type = CommandType.UUID, entityType = HostResponse.class, required = true, description = "the ID of the host")
    private Long hostId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final boolean result = dedicatedService.releaseDedicatedResource(null, null, null, getHostId());
        if (result) {
            final SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to release dedicated Host");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Long getHostId() {
        return hostId;
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
        return EventTypes.EVENT_DEDICATE_RESOURCE_RELEASE;
    }

    @Override
    public String getEventDescription() {
        return "releasing dedicated host";
    }
}
