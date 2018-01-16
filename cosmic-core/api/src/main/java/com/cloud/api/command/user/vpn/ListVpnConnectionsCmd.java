package com.cloud.api.command.user.vpn;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListProjectAndAccountResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.Site2SiteVpnConnectionResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listVpnConnections", group = "VPN", description = "Lists site to site vpn connection gateways", responseObject = Site2SiteVpnConnectionResponse.class, entityType =
        {Site2SiteVpnConnection.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListVpnConnectionsCmd extends BaseListProjectAndAccountResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListVpnConnectionsCmd.class.getName());

    private static final String s_name = "listvpnconnectionsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = Site2SiteVpnConnectionResponse.class, description = "id of the vpn connection")
    private Long id;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class, description = "id of vpc")
    private Long vpcId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public Long getVpcId() {
        return vpcId;
    }

    @Override
    public Boolean getDisplay() {
        if (display != null) {
            return display;
        }
        return super.getDisplay();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final Pair<List<? extends Site2SiteVpnConnection>, Integer> conns = _s2sVpnService.searchForVpnConnections(this);
        final ListResponse<Site2SiteVpnConnectionResponse> response = new ListResponse<>();
        final List<Site2SiteVpnConnectionResponse> connResponses = new ArrayList<>();
        for (final Site2SiteVpnConnection conn : conns.first()) {
            if (conn == null) {
                continue;
            }
            final Site2SiteVpnConnectionResponse site2SiteVpnConnectonRes = _responseGenerator.createSite2SiteVpnConnectionResponse(conn);
            site2SiteVpnConnectonRes.setObjectName("vpnconnection");
            connResponses.add(site2SiteVpnConnectonRes);
        }

        response.setResponses(connResponses, conns.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
