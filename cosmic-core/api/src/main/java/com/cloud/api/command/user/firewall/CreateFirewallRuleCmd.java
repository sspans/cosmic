package com.cloud.api.command.user.firewall;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.FirewallResponse;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.rules.FirewallRule;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.net.NetUtils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createFirewallRule", group = "Firewall", description = "Creates a firewall rule for a given IP address", responseObject = FirewallResponse.class, entityType = {FirewallRule.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateFirewallRuleCmd extends BaseAsyncCreateCmd implements FirewallRule {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateFirewallRuleCmd.class.getName());

    private static final String s_name = "createfirewallruleresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.IP_ADDRESS_ID,
            type = CommandType.UUID,
            entityType = IPAddressResponse.class,
            required = true,
            description = "the IP address id of the port forwarding rule")
    private Long ipAddressId;

    @Parameter(name = ApiConstants.PROTOCOL,
            type = CommandType.STRING,
            required = true,
            description = "the protocol for the firewall rule. Valid values are TCP/UDP/ICMP.")
    private String protocol;

    @Parameter(name = ApiConstants.START_PORT, type = CommandType.INTEGER, description = "the starting port of firewall rule")
    private Integer publicStartPort;

    @Parameter(name = ApiConstants.END_PORT, type = CommandType.INTEGER, description = "the ending port of firewall rule")
    private Integer publicEndPort;

    @Parameter(name = ApiConstants.CIDR_LIST, type = CommandType.LIST, collectionType = CommandType.STRING, description = "the CIDR list to forward traffic from")
    private List<String> cidrlist;

    @Parameter(name = ApiConstants.ICMP_TYPE, type = CommandType.INTEGER, description = "type of the ICMP message being sent")
    private Integer icmpType;

    @Parameter(name = ApiConstants.ICMP_CODE, type = CommandType.INTEGER, description = "error code for this icmp message")
    private Integer icmpCode;

    @Parameter(name = ApiConstants.TYPE, type = CommandType.STRING, description = "type of firewallrule: system/user")
    private String type;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the rule to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceUnavailableException {
        final CallContext callerContext = CallContext.current();
        boolean success = false;
        FirewallRule rule = _entityMgr.findById(FirewallRule.class, getEntityId());
        try {
            CallContext.current().setEventDetails("Rule ID: " + getEntityId());
            success = _firewallService.applyIngressFwRules(rule.getSourceIpAddressId(), callerContext.getCallingAccount());

            // State is different after the rule is applied, so get new object here
            rule = _entityMgr.findById(FirewallRule.class, getEntityId());
            FirewallResponse fwResponse = new FirewallResponse();
            if (rule != null) {
                fwResponse = _responseGenerator.createFirewallResponse(rule);
                setResponseObject(fwResponse);
            }
            fwResponse.setResponseName(getCommandName());
        } finally {
            if (!success || rule == null) {
                _firewallService.revokeIngressFwRule(getEntityId(), true);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create firewall rule");
            }
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account account = CallContext.current().getCallingAccount();

        if (account != null) {
            return account.getId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public boolean isDisplay() {
        if (display != null) {
            return display;
        } else {
            return true;
        }
    }

    @Override
    public long getId() {
        throw new UnsupportedOperationException("database ID can only provided by VO objects");
    }

    @Override
    public String getXid() {
        // FIXME: We should allow for end user to specify Xid.
        return null;
    }

    @Override
    public Integer getSourcePortStart() {
        if (publicStartPort != null) {
            return publicStartPort;
        }
        return null;
    }

    @Override
    public Integer getSourcePortEnd() {
        if (publicEndPort == null) {
            if (publicStartPort != null) {
                return publicStartPort;
            }
        } else {
            return publicEndPort;
        }

        return null;
    }

    @Override
    public String getProtocol() {
        return protocol.trim();
    }

    @Override
    public Purpose getPurpose() {
        return Purpose.Firewall;
    }

    @Override
    public State getState() {
        throw new UnsupportedOperationException("Should never call me to find the state");
    }

    @Override
    public long getNetworkId() {
        final IpAddress ip = _entityMgr.findById(IpAddress.class, getIpAddressId());
        Long ntwkId = null;

        if (ip.getAssociatedWithNetworkId() != null) {
            ntwkId = ip.getAssociatedWithNetworkId();
        }

        if (ntwkId == null) {
            throw new InvalidParameterValueException("Unable to create firewall rule for the IP address ID=" + ipAddressId +
                    " as IP is not associated with any network and no networkId is passed in");
        }
        return ntwkId;
    }

    public Long getIpAddressId() {
        return ipAddressId;
    }

    @Override
    public Long getSourceIpAddressId() {
        return ipAddressId;
    }

    @Override
    public Integer getIcmpCode() {
        if (icmpCode != null) {
            return icmpCode;
        } else if (protocol.equalsIgnoreCase(NetUtils.ICMP_PROTO)) {
            return -1;
        }
        return null;
    }

    @Override
    public Integer getIcmpType() {
        if (icmpType != null) {
            return icmpType;
        } else if (protocol.equalsIgnoreCase(NetUtils.ICMP_PROTO)) {
            return -1;
        }
        return null;
    }

    @Override
    public List<String> getSourceCidrList() {
        if (cidrlist != null) {
            return cidrlist;
        } else {
            final List<String> oneCidrList = new ArrayList<>();
            oneCidrList.add(NetUtils.ALL_IP4_CIDRS);
            return oneCidrList;
        }
    }

    public void setSourceCidrList(final List<String> cidrs) {
        cidrlist = cidrs;
    }

    @Override
    public Long getRelated() {
        return null;
    }

    @Override
    public FirewallRuleType getType() {
        if (type != null && type.equalsIgnoreCase("system")) {
            return FirewallRuleType.System;
        } else {
            return FirewallRuleType.User;
        }
    }

    @Override
    public TrafficType getTrafficType() {
        return FirewallRule.TrafficType.Ingress;
    }

    @Override
    public String getUuid() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getDomainId() {
        final IpAddress ip = _networkService.getIp(ipAddressId);
        return ip.getDomainId();
    }

    @Override
    public void create() {
        if (getSourceCidrList() != null) {
            for (final String cidr : getSourceCidrList()) {
                if (!NetUtils.isValidIp4Cidr(cidr)) {
                    throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Source CIDRs formatting error " + cidr);
                }
            }
        }
        try {
            final FirewallRule result = _firewallService.createIngressFirewallRule(this);
            if (result != null) {
                setEntityId(result.getId());
                setEntityUuid(result.getUuid());
            }
        } catch (final NetworkRuleConflictException ex) {
            s_logger.info("Network rule conflict: " + ex.getMessage());
            s_logger.trace("Network Rule Conflict: ", ex);
            throw new ServerApiException(ApiErrorCode.NETWORK_RULE_CONFLICT_ERROR, ex.getMessage());
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_FIREWALL_OPEN;
    }

    @Override
    public String getEventDescription() {
        final IpAddress ip = _networkService.getIp(ipAddressId);
        return ("Creating firewall rule for IP: " + ip.getAddress() + " for protocol:" + getProtocol());
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.FirewallRule;
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return getIp().getAssociatedWithNetworkId();
    }

    private IpAddress getIp() {
        final IpAddress ip = _networkService.getIp(ipAddressId);
        if (ip == null) {
            throw new InvalidParameterValueException("Unable to find IP address by ID " + ipAddressId);
        }
        return ip;
    }

    @Override
    public long getAccountId() {
        final IpAddress ip = _networkService.getIp(ipAddressId);
        return ip.getAccountId();
    }

    @Override
    public Class<?> getEntityType() {
        return FirewallRule.class;
    }
}
