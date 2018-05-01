package com.cloud.network.lb;

import com.cloud.context.CallContext;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.legacymodel.user.Account;
import com.cloud.network.lb.LoadBalancingRule.LbDestination;
import com.cloud.network.lb.LoadBalancingRule.LbHealthCheckPolicy;
import com.cloud.network.lb.LoadBalancingRule.LbSslCert;
import com.cloud.network.lb.LoadBalancingRule.LbStickinessPolicy;
import com.cloud.network.rules.LbStickinessMethod;
import com.cloud.network.rules.LoadBalancer;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;

import java.util.List;

public interface LoadBalancingRulesManager {

    LoadBalancer createPublicLoadBalancer(String xId, String name, String description, int srcPort, int destPort, long sourceIpId, String protocol, String algorithm,
                                          boolean openFirewall, CallContext caller, String lbProtocol, Boolean forDisplay, int clientTimeout, int serverTimeout)
            throws NetworkRuleConflictException;

    boolean removeAllLoadBalanacersForIp(long ipId, Account caller, long callerUserId);

    boolean removeAllLoadBalanacersForNetwork(long networkId, Account caller, long callerUserId);

    List<LbDestination> getExistingDestinations(long lbId);

    List<LbStickinessPolicy> getStickinessPolicies(long lbId);

    List<LbStickinessMethod> getStickinessMethods(long networkid);

    List<LbHealthCheckPolicy> getHealthCheckPolicies(long lbId);

    LbSslCert getLbSslCert(long lbId);

    /**
     * Remove vm from all load balancers
     *
     * @param vmId
     * @return true if removal is successful
     */
    boolean removeVmFromLoadBalancers(long vmId);

    boolean applyLoadBalancersForNetwork(long networkId, Scheme scheme) throws ResourceUnavailableException;

    String getLBCapability(long networkid, String capabilityName);

    boolean revokeLoadBalancersForNetwork(long networkId, Scheme scheme) throws ResourceUnavailableException;

    boolean validateLbRule(LoadBalancingRule lbRule);

    void removeLBRule(LoadBalancer rule);

    void isLbServiceSupportedInNetwork(long networkId, Scheme scheme);
}
