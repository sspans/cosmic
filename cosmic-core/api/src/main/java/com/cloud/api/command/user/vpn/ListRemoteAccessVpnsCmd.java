package com.cloud.api.command.user.vpn;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListProjectAndAccountResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.api.response.RemoteAccessVpnResponse;
import com.cloud.network.RemoteAccessVpn;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listRemoteAccessVpns", group = "VPN", description = "Lists remote access vpns", responseObject = RemoteAccessVpnResponse.class, entityType = {RemoteAccessVpn.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListRemoteAccessVpnsCmd extends BaseListProjectAndAccountResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListRemoteAccessVpnsCmd.class.getName());

    private static final String s_name = "listremoteaccessvpnsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.PUBLIC_IP_ID, type = CommandType.UUID, entityType = IPAddressResponse.class, description = "public ip address id of the vpn server")
    private Long publicIpId;

    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = RemoteAccessVpnResponse.class,
            description = "Lists remote access vpn rule with the specified ID",
            since = "4.3")
    private Long id;

    @Parameter(name = ApiConstants.NETWORK_ID,
            type = CommandType.UUID,
            entityType = NetworkResponse.class,
            description = "list remote access VPNs for ceratin network",
            since = "4.3")
    private Long networkId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getPublicIpId() {
        return publicIpId;
    }

    public Long getId() {
        return id;
    }

    public Long getNetworkId() {
        return networkId;
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
        final Pair<List<? extends RemoteAccessVpn>, Integer> vpns = _ravService.searchForRemoteAccessVpns(this);
        final ListResponse<RemoteAccessVpnResponse> response = new ListResponse<>();
        final List<RemoteAccessVpnResponse> vpnResponses = new ArrayList<>();
        if (vpns.first() != null && !vpns.first().isEmpty()) {
            for (final RemoteAccessVpn vpn : vpns.first()) {
                vpnResponses.add(_responseGenerator.createRemoteAccessVpnResponse(vpn));
            }
        }
        response.setResponses(vpnResponses, vpns.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
