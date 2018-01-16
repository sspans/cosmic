package com.cloud.api.command.user.vpn;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListProjectAndAccountResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.Site2SiteVpnGatewayResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.network.Site2SiteVpnGateway;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listVpnGateways", group = "VPN", description = "Lists site 2 site vpn gateways", responseObject = Site2SiteVpnGatewayResponse.class, entityType = {Site2SiteVpnGateway.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListVpnGatewaysCmd extends BaseListProjectAndAccountResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListVpnGatewaysCmd.class.getName());

    private static final String s_name = "listvpngatewaysresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = Site2SiteVpnGatewayResponse.class, description = "id of the vpn gateway")
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
        final Pair<List<? extends Site2SiteVpnGateway>, Integer> gws = _s2sVpnService.searchForVpnGateways(this);
        final ListResponse<Site2SiteVpnGatewayResponse> response = new ListResponse<>();
        final List<Site2SiteVpnGatewayResponse> gwResponses = new ArrayList<>();
        for (final Site2SiteVpnGateway gw : gws.first()) {
            if (gw == null) {
                continue;
            }
            final Site2SiteVpnGatewayResponse site2SiteVpnGatewayRes = _responseGenerator.createSite2SiteVpnGatewayResponse(gw);
            site2SiteVpnGatewayRes.setObjectName("vpngateway");
            gwResponses.add(site2SiteVpnGatewayRes);
        }

        response.setResponses(gwResponses, gws.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
