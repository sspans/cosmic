package com.cloud.api.command.user.vpn;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.network.VpnUser;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "removeVpnUser", group = "VPN", description = "Removes vpn user", responseObject = SuccessResponse.class, entityType = {VpnUser.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class RemoveVpnUserCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(RemoveVpnUserCmd.class.getName());

    private static final String s_name = "removevpnuserresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.USERNAME, type = CommandType.STRING, required = true, description = "username for the vpn user")
    private String userName;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional account for the vpn user. Must be used with domainId.")
    private String accountName;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "remove vpn user from the project")
    private Long projectId;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "an optional domainId for the vpn user. If the account parameter is used, domainId must also be used.")
    private Long domainId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public Long getProjecId() {
        return projectId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VPN_USER_REMOVE;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventDescription() {
        return "Remove Remote Access VPN user for account " + getEntityOwnerId() + " username= " + getUserName();
    }

    public String getUserName() {
        return userName;
    }

    @Override
    public void execute() {
        final Account owner = _accountService.getAccount(getEntityOwnerId());
        final boolean result = _ravService.removeVpnUser(owner.getId(), userName, CallContext.current().getCallingAccount());
        if (!result) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to remove vpn user");
        }

        if (!_ravService.applyVpnUsers(owner.getId(), userName)) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to apply vpn user removal");
        }
        final SuccessResponse response = new SuccessResponse(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Long accountId = _accountService.finalyzeAccountId(accountName, domainId, projectId, true);
        if (accountId == null) {
            return CallContext.current().getCallingAccount().getId();
        }

        return accountId;
    }
}
