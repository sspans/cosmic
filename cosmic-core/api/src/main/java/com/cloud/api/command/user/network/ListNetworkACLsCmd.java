package com.cloud.api.command.user.network;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.NetworkACLItemResponse;
import com.cloud.api.response.NetworkACLResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.network.vpc.NetworkACLItem;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listNetworkACLs", group = "Network ACL", description = "Lists all network ACL items", responseObject = NetworkACLItemResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListNetworkACLsCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListNetworkACLsCmd.class.getName());

    private static final String s_name = "listnetworkaclsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = NetworkACLItemResponse.class,
            description = "Lists network ACL Item with the specified ID")
    private Long id;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, description = "list network ACL items by network ID")
    private Long networkId;

    @Parameter(name = ApiConstants.TRAFFIC_TYPE, type = CommandType.STRING, description = "list network ACL items by traffic type - ingress or egress")
    private String trafficType;

    @Parameter(name = ApiConstants.ACL_ID, type = CommandType.UUID, entityType = NetworkACLResponse.class, description = "list network ACL items by ACL ID")
    private Long aclId;

    @Parameter(name = ApiConstants.PROTOCOL, type = CommandType.STRING, description = "list network ACL items by protocol")
    private String protocol;

    @Parameter(name = ApiConstants.ACTION, type = CommandType.STRING, description = "list network ACL items by action")
    private String action;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getNetworkId() {
        return networkId;
    }

    public Long getId() {
        return id;
    }

    public String getTrafficType() {
        return trafficType;
    }

    public Long getAclId() {
        return aclId;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getAction() {
        return action;
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
        final Pair<List<? extends NetworkACLItem>, Integer> result = _networkACLService.listNetworkACLItems(this);
        final ListResponse<NetworkACLItemResponse> response = new ListResponse<>();
        final List<NetworkACLItemResponse> aclResponses = new ArrayList<>();

        for (final NetworkACLItem acl : result.first()) {
            final NetworkACLItemResponse ruleData = _responseGenerator.createNetworkACLItemResponse(acl);
            aclResponses.add(ruleData);
        }
        response.setResponses(aclResponses, result.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
