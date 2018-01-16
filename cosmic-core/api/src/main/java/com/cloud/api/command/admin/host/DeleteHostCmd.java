package com.cloud.api.command.admin.host;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.HostResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "deleteHost", group = "Host", description = "Deletes a host.", responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DeleteHostCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(DeleteHostCmd.class.getName());

    private static final String s_name = "deletehostresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = HostResponse.class, required = true, description = "the host ID")
    private Long id;

    @Parameter(name = ApiConstants.FORCED,
            type = CommandType.BOOLEAN,
            description = "Force delete the host. All HA enabled vms running on the host will be put to HA; HA disabled ones will be stopped")
    private Boolean forced;

    @Parameter(name = ApiConstants.FORCED_DESTROY_LOCAL_STORAGE,
            type = CommandType.BOOLEAN,
            description = "Force destroy local storage on this host. All VMs created on this local storage will be destroyed")
    private Boolean forceDestroyLocalStorage;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        final boolean result = _resourceService.deleteHost(getId(), isForced(), isForceDestoryLocalStorage());
        if (result) {
            final SuccessResponse response = new SuccessResponse(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to delete host");
        }
    }

    public Long getId() {
        return id;
    }

    public boolean isForced() {
        return (forced != null) ? forced : false;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    public boolean isForceDestoryLocalStorage() {
        return (forceDestroyLocalStorage != null) ? forceDestroyLocalStorage : true;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
