package com.cloud.api.command.user.loadbalancer;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.FirewallRuleResponse;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.LoadBalancerResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listLoadBalancerRules", group = "Load Balancer", description = "Lists load balancer rules.", responseObject = LoadBalancerResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListLoadBalancerRulesCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListLoadBalancerRulesCmd.class.getName());

    private static final String s_name = "listloadbalancerrulesresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, description = "the ID of the load balancer rule")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the load balancer rule")
    private String loadBalancerRuleName;

    @Parameter(name = ApiConstants.PUBLIC_IP_ID,
            type = CommandType.UUID,
            entityType = IPAddressResponse.class,
            description = "the public IP address ID of the load balancer rule")
    private Long publicIpId;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID,
            type = CommandType.UUID,
            entityType = UserVmResponse.class,
            description = "the ID of the virtual machine of the load balancer rule")
    private Long virtualMachineId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "the availability zone ID")
    private Long zoneId;

    @Parameter(name = ApiConstants.NETWORK_ID, type = CommandType.UUID, entityType = NetworkResponse.class, description = "list by network ID the rule belongs to")
    private Long networkId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public String getLoadBalancerRuleName() {
        return loadBalancerRuleName;
    }

    public Long getPublicIpId() {
        return publicIpId;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public Long getZoneId() {
        return zoneId;
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

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        final Pair<List<? extends LoadBalancer>, Integer> loadBalancers = _lbService.searchForLoadBalancers(this);
        final ListResponse<LoadBalancerResponse> response = new ListResponse<>();
        final List<LoadBalancerResponse> lbResponses = new ArrayList<>();
        if (loadBalancers != null) {
            for (final LoadBalancer loadBalancer : loadBalancers.first()) {
                final LoadBalancerResponse lbResponse = _responseGenerator.createLoadBalancerResponse(loadBalancer);
                lbResponse.setObjectName("loadbalancerrule");
                lbResponses.add(lbResponse);
            }
            response.setResponses(lbResponses, loadBalancers.second());
        }
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
