package com.cloud.api.command.user.firewall;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.FirewallRuleResponse;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listPortForwardingRules", group = "Firewall", description = "Lists all port forwarding rules for an IP address.", responseObject = FirewallRuleResponse.class, entityType =
        {PortForwardingRule.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListPortForwardingRulesCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListPortForwardingRulesCmd.class.getName());

    private static final String s_name = "listportforwardingrulesresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, description = "Lists rule with the specified ID.")
    private Long id;

    @Parameter(name = ApiConstants.IP_ADDRESS_ID,
            type = CommandType.UUID,
            entityType = IPAddressResponse.class,
            description = "the ID of IP address of the port forwarding services")
    private Long ipAddressId;

    @Parameter(name = ApiConstants.NETWORK_ID,
            type = CommandType.UUID,
            entityType = NetworkResponse.class,
            description = "list port forwarding rules for certain network",
            since = "4.3")
    private Long networkId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getIpAddressId() {
        return ipAddressId;
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
        final Pair<List<? extends PortForwardingRule>, Integer> result = _rulesService.listPortForwardingRules(this);
        final ListResponse<FirewallRuleResponse> response = new ListResponse<>();
        final List<FirewallRuleResponse> fwResponses = new ArrayList<>();

        for (final PortForwardingRule fwRule : result.first()) {
            final FirewallRuleResponse ruleData = _responseGenerator.createPortForwardingRuleResponse(fwRule);
            ruleData.setObjectName("portforwardingrule");
            fwResponses.add(ruleData);
        }
        response.setResponses(fwResponses, result.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
