package com.cloud.api.command.user.loadbalancer;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.FirewallRuleResponse;
import com.cloud.api.response.SuccessResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.user.Account;
import com.cloud.utils.StringUtils;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.vm.VirtualMachine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "removeFromLoadBalancerRule", group = "Load Balancer",
        description = "Removes a virtual machine or a list of virtual machines from a load balancer rule.",
        responseObject = SuccessResponse.class,
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = false)
public class RemoveFromLoadBalancerRuleCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(RemoveFromLoadBalancerRuleCmd.class.getName());

    private static final String s_name = "removefromloadbalancerruleresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID,
            type = CommandType.UUID,
            entityType = FirewallRuleResponse.class,
            required = true,
            description = "The ID of the load balancer rule")
    private Long id;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_IDS,
            type = CommandType.LIST,
            collectionType = CommandType.UUID,
            entityType = UserVmResponse.class,
            description = "the list of IDs of the virtual machines that are being removed from the load balancer rule (i.e. virtualMachineIds=1,2,3)")
    private List<Long> virtualMachineIds;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID_IP,
            type = CommandType.MAP,
            description = "VM ID and IP map, vmidipmap[0].vmid=1 vmidipmap[0].ip=10.1.1.75",
            since = "4.4")
    private Map vmIdIpMap;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Map<Long, String> getVmIdIpMap() {
        return vmIdIpMap;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_REMOVE_FROM_LOAD_BALANCER_RULE;
    }

    @Override
    public String getEventDescription() {
        return "removing instances from load balancer: " + getId() + " (ids: " + StringUtils.join(getVirtualMachineIds(), ",") + ")";
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        final LoadBalancer lb = _lbService.findById(id);
        if (lb == null) {
            throw new InvalidParameterValueException("Unable to find load balancer rule: " + id);
        }
        return lb.getNetworkId();
    }

    public Long getId() {
        return id;
    }

    public List<Long> getVirtualMachineIds() {
        return virtualMachineIds;
    }

    @Override
    public void execute() {
        CallContext.current().setEventDetails("Load balancer Id: " + getId() + " VmIds: " + StringUtils.join(getVirtualMachineIds(), ","));
        final Map<Long, List<String>> vmIdIpsMap = getVmIdIpListMap();
        try {
            final boolean result = _lbService.removeFromLoadBalancer(id, virtualMachineIds, vmIdIpsMap);
            if (result) {
                final SuccessResponse response = new SuccessResponse(getCommandName());
                this.setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to remove instance from load balancer rule");
            }
        } catch (final InvalidParameterValueException ex) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Failed to remove instance from load balancer rule");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final LoadBalancer lb = _entityMgr.findById(LoadBalancer.class, getId());
        if (lb == null) {
            return Account.ACCOUNT_ID_SYSTEM; // bad id given, parent this command to SYSTEM so ERROR events are tracked
        }
        return lb.getAccountId();
    }

    public Map<Long, List<String>> getVmIdIpListMap() {
        final Map<Long, List<String>> vmIdIpsMap = new HashMap<>();
        if (vmIdIpMap != null && !vmIdIpMap.isEmpty()) {
            final Collection idIpsCollection = vmIdIpMap.values();
            final Iterator iter = idIpsCollection.iterator();
            while (iter.hasNext()) {
                final HashMap<String, String> idIpsMap = (HashMap<String, String>) iter.next();
                final String vmId = idIpsMap.get("vmid");
                final String vmIp = idIpsMap.get("vmip");

                final VirtualMachine lbvm = _entityMgr.findByUuid(VirtualMachine.class, vmId);
                if (lbvm == null) {
                    throw new InvalidParameterValueException("Unable to find virtual machine ID: " + vmId);
                }

                final Long longVmId = lbvm.getId();

                List<String> ipsList = null;
                if (vmIdIpsMap.containsKey(longVmId)) {
                    ipsList = vmIdIpsMap.get(longVmId);
                } else {
                    ipsList = new ArrayList<>();
                }
                ipsList.add(vmIp);
                vmIdIpsMap.put(longVmId, ipsList);
            }
        }

        return vmIdIpsMap;
    }
}
