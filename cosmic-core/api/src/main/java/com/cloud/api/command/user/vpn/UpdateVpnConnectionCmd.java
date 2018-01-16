package com.cloud.api.command.user.vpn;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseAsyncCustomIdCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.Site2SiteVpnConnectionResponse;
import com.cloud.event.EventTypes;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateVpnConnection", group = "VPN", description = "Updates site to site vpn connection", responseObject = Site2SiteVpnConnectionResponse.class, since = "4.4",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateVpnConnectionCmd extends BaseAsyncCustomIdCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateVpnConnectionCmd.class.getName());

    private static final String s_name = "updatevpnconnectionresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = Site2SiteVpnConnectionResponse.class, required = true, description = "id of vpn connection")
    private Long id;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the vpn to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    @Override
    public String getEventType() {
        return EventTypes.EVENT_S2S_VPN_CONNECTION_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating site-to-site VPN connection id= " + id;
    }
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final Site2SiteVpnConnection result = _s2sVpnService.updateVpnConnection(id, this.getCustomId(), getDisplay());
        final Site2SiteVpnConnectionResponse response = _responseGenerator.createSite2SiteVpnConnectionResponse(result);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    public Boolean getDisplay() {
        return display;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Site2SiteVpnConnection conn = _entityMgr.findById(Site2SiteVpnConnection.class, getId());
        if (conn != null) {
            return conn.getAccountId();
        }
        return Account.ACCOUNT_ID_SYSTEM;
    }

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    public Long getId() {
        return id;
    }

    @Override
    public void checkUuid() {
        if (this.getCustomId() != null) {
            _uuidMgr.checkUuid(this.getCustomId(), Site2SiteVpnConnection.class);
        }
    }
}
