package com.cloud.api.command.user.vpn;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.Site2SiteVpnConnectionResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "resetVpnConnection", group = "VPN", description = "Reset site to site vpn connection", responseObject = Site2SiteVpnConnectionResponse.class, entityType =
        {Site2SiteVpnConnection.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ResetVpnConnectionCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ResetVpnConnectionCmd.class.getName());

    private static final String s_name = "resetvpnconnectionresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = Site2SiteVpnConnectionResponse.class, required = true, description = "id of vpn connection")
    private Long id;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional account for connection. " + "Must be used with domainId.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "an optional domainId for connection. If the account parameter is used, domainId must also be used.")
    private Long domainId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getDomainId() {
        return domainId;
    }

    public Long getAccountId() {
        return getEntityOwnerId();
    }

    public Long getId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return EventTypes.EVENT_S2S_VPN_CONNECTION_RESET;
    }

    @Override
    public String getEventDescription() {
        return "Reset site-to-site VPN connection for account " + getEntityOwnerId();
    }

    @Override
    public void execute() {
        try {
            final Site2SiteVpnConnection result = _s2sVpnService.resetVpnConnection(this);
            if (result != null) {
                final Site2SiteVpnConnectionResponse response = _responseGenerator.createSite2SiteVpnConnectionResponse(result);
                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to reset site to site VPN connection");
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Long accountId = _accountService.finalyzeAccountId(accountName, domainId, null, true);
        if (accountId == null) {
            return CallContext.current().getCallingAccount().getId();
        }
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
