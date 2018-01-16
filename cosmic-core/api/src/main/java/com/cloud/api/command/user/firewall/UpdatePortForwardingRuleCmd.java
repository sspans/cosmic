package com.cloud.api.command.user.firewall;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCustomIdCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.FirewallRuleResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.event.EventTypes;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.net.Ip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updatePortForwardingRule", group = "Firewall",
        responseObject = FirewallRuleResponse.class,
        description = "Updates a port forwarding rule. Only the private port and the virtual machine can be updated.", entityType = {PortForwardingRule.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdatePortForwardingRuleCmd extends BaseAsyncCustomIdCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdatePortForwardingRuleCmd.class.getName());
    private static final String s_name = "updateportforwardingruleresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = FirewallRuleResponse.class, required = true, description = "the ID of the port forwarding rule",
            since = "4.4")
    private Long id;

    @Parameter(name = ApiConstants.PRIVATE_PORT, type = CommandType.INTEGER, description = "the private port of the port forwarding rule")
    private Integer privatePort;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID,
            type = CommandType.UUID,
            entityType = UserVmResponse.class,
            description = "the ID of the virtual machine for the port forwarding rule")
    private Long virtualMachineId;

    @Parameter(name = ApiConstants.VM_GUEST_IP, type = CommandType.STRING, required = false, description = "VM guest nic Secondary ip address for the port forwarding rule",
            since = "4.5")
    private String vmGuestIp;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the rule to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return EventTypes.EVENT_NET_RULE_MODIFY;
    }

    @Override
    public String getEventDescription() {
        return "updating port forwarding rule";
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        final PortForwardingRule rule = _entityMgr.findById(PortForwardingRule.class, getId());
        if (rule != null) {
            return rule.getNetworkId();
        } else {
            throw new InvalidParameterValueException("Unable to find the rule by id");
        }
    }

    public Long getId() {
        return id;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void checkUuid() {
        if (getCustomId() != null) {
            _uuidMgr.checkUuid(getCustomId(), FirewallRule.class);
        }
    }

    @Override
    public void execute() {
        final PortForwardingRule rule = _rulesService.updatePortForwardingRule(id, getPrivatePort(), getVirtualMachineId(), getVmGuestIp(), getCustomId(), getDisplay());
        FirewallRuleResponse fwResponse = new FirewallRuleResponse();
        if (rule != null) {
            fwResponse = _responseGenerator.createPortForwardingRuleResponse(rule);
            setResponseObject(fwResponse);
        }
        fwResponse.setResponseName(getCommandName());
    }

    public Integer getPrivatePort() {
        return privatePort;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public Ip getVmGuestIp() {
        if (vmGuestIp == null) {
            return null;
        }
        return new Ip(vmGuestIp);
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
        final PortForwardingRule rule = _entityMgr.findById(PortForwardingRule.class, getId());
        if (rule != null) {
            return rule.getAccountId();
        }

        // bad address given, parent this command to SYSTEM so ERROR events are tracked
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
