package com.cloud.network.rules;

import com.cloud.api.command.user.firewall.ListPortForwardingRulesCmd;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.engine.orchestration.service.NetworkOrchestrationService;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVMMapVO;
import com.cloud.network.rules.FirewallRule.FirewallRuleType;
import com.cloud.network.rules.FirewallRule.Purpose;
import com.cloud.network.rules.FirewallRule.State;
import com.cloud.network.rules.dao.PortForwardingRulesDao;
import com.cloud.network.vpc.VpcManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.server.ResourceTag.ResourceObjectType;
import com.cloud.tags.ResourceTagVO;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.uservm.UserVm;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionCallbackWithExceptionNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.Nic;
import com.cloud.vm.NicSecondaryIp;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.Type;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicSecondaryIpDao;
import com.cloud.vm.dao.NicSecondaryIpVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesManagerImpl extends ManagerBase implements RulesManager, RulesService {
    private static final Logger s_logger = LoggerFactory.getLogger(RulesManagerImpl.class);

    @Inject
    IpAddressManager _ipAddrMgr;
    @Inject
    EntityManager _entityMgr;
    @Inject
    PortForwardingRulesDao _portForwardingDao;
    @Inject
    FirewallRulesDao _firewallDao;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    UserVmDao _vmDao;
    @Inject
    VMInstanceDao _vmInstanceDao;
    @Inject
    AccountManager _accountMgr;
    @Inject
    NetworkOrchestrationService _networkMgr;
    @Inject
    NetworkModel _networkModel;
    @Inject
    FirewallManager _firewallMgr;
    @Inject
    NicDao _nicDao;
    @Inject
    ResourceTagDao _resourceTagDao;
    @Inject
    VpcManager _vpcMgr;
    @Inject
    NicSecondaryIpDao _nicSecondaryDao;
    @Inject
    LoadBalancerVMMapDao _loadBalancerVMMapDao;

    protected void checkIpAndUserVm(final IpAddress ipAddress, final UserVm userVm, final Account caller, final Boolean ignoreVmState) {
        if (ipAddress == null || ipAddress.getAllocatedTime() == null || ipAddress.getAllocatedToAccountId() == null) {
            throw new InvalidParameterValueException("Unable to create ip forwarding rule on address " + ipAddress + ", invalid IP address specified.");
        }

        if (userVm == null) {
            return;
        }

        if (userVm.getState() == VirtualMachine.State.Destroyed || userVm.getState() == VirtualMachine.State.Expunging) {
            if (!ignoreVmState) {
                throw new InvalidParameterValueException("Invalid user vm: " + userVm.getId());
            }
        }

        _accountMgr.checkAccess(caller, null, true, ipAddress, userVm);

        // validate that IP address and userVM belong to the same account
        if (ipAddress.getAllocatedToAccountId().longValue() != userVm.getAccountId()) {
            throw new InvalidParameterValueException("Unable to create ip forwarding rule, IP address " + ipAddress +
                    " owner is not the same as owner of virtual machine " + userVm.toString());
        }
    }

    private boolean enableStaticNat(final long ipId, final long vmId, final long networkId, final boolean isSystemVm, final String vmGuestIp) throws NetworkRuleConflictException,
            ResourceUnavailableException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        CallContext.current().setEventDetails("Ip Id: " + ipId);

        // Verify input parameters
        IPAddressVO ipAddress = _ipAddressDao.findById(ipId);
        if (ipAddress == null) {
            throw new InvalidParameterValueException("Unable to find ip address by id " + ipId);
        }

        // Verify input parameters
        boolean performedIpAssoc = false;
        final boolean isOneToOneNat = ipAddress.isOneToOneNat();
        final Long associatedWithVmId = ipAddress.getAssociatedWithVmId();
        final Nic guestNic;
        NicSecondaryIpVO nicSecIp = null;
        String dstIp = null;

        try {
            final Network network = _networkModel.getNetwork(networkId);
            if (network == null) {
                throw new InvalidParameterValueException("Unable to find network by id");
            }

            // Check that vm has a nic in the network
            guestNic = _networkModel.getNicInNetwork(vmId, networkId);
            if (guestNic == null) {
                throw new InvalidParameterValueException("Vm doesn't belong to the network with specified id");
            }
            dstIp = guestNic.getIPv4Address();

            if (!_networkModel.areServicesSupportedInNetwork(network.getId(), Service.StaticNat)) {
                throw new InvalidParameterValueException("Unable to create static nat rule; StaticNat service is not " + "supported in network with specified id");
            }

            if (!isSystemVm) {
                final UserVmVO vm = _vmDao.findById(vmId);
                if (vm == null) {
                    throw new InvalidParameterValueException("Can't enable static nat for the address id=" + ipId + ", invalid virtual machine id specified (" + vmId +
                            ").");
                }
                //associate ip address to network (if needed)
                if (ipAddress.getAssociatedWithNetworkId() == null) {
                    final boolean assignToVpcNtwk = network.getVpcId() != null && ipAddress.getVpcId() != null && ipAddress.getVpcId().longValue() == network.getVpcId();
                    if (assignToVpcNtwk) {
                        _networkModel.checkIpForService(ipAddress, Service.StaticNat, networkId);

                        s_logger.debug("The ip is not associated with the VPC network id=" + networkId + ", so assigning");
                        try {
                            ipAddress = _ipAddrMgr.associateIPToGuestNetwork(ipId, networkId, false);
                        } catch (final Exception ex) {
                            s_logger.warn("Failed to associate ip id=" + ipId + " to VPC network id=" + networkId + " as " + "a part of enable static nat");
                            return false;
                        }
                    }
                } else if (ipAddress.getAssociatedWithNetworkId() != networkId) {
                    throw new InvalidParameterValueException("Invalid network Id=" + networkId + ". IP is associated with" +
                            " a different network than passed network id");
                } else {
                    _networkModel.checkIpForService(ipAddress, Service.StaticNat, null);
                }

                if (ipAddress.getAssociatedWithNetworkId() == null) {
                    throw new InvalidParameterValueException("Ip address " + ipAddress + " is not assigned to the network " + network);
                }

                // Check permissions
                if (ipAddress.getSystem()) {
                    // when system is enabling static NAT on system IP's (for EIP) ignore VM state
                    checkIpAndUserVm(ipAddress, vm, caller, true);
                } else {
                    checkIpAndUserVm(ipAddress, vm, caller, false);
                }

                //is static nat is for vm secondary ip
                //dstIp = guestNic.getIp4Address();
                if (vmGuestIp != null) {
                    //dstIp = guestNic.getIp4Address();

                    if (!dstIp.equals(vmGuestIp)) {
                        //check whether the secondary ip set to the vm or not
                        final boolean secondaryIpSet = _networkMgr.isSecondaryIpSetForNic(guestNic.getId());
                        if (!secondaryIpSet) {
                            throw new InvalidParameterValueException("VM ip " + vmGuestIp + " address not belongs to the vm");
                        }
                        //check the ip belongs to the vm or not
                        nicSecIp = _nicSecondaryDao.findByIp4AddressAndNicId(vmGuestIp, guestNic.getId());
                        if (nicSecIp == null) {
                            throw new InvalidParameterValueException("VM ip " + vmGuestIp + " address not belongs to the vm");
                        }
                        dstIp = nicSecIp.getIp4Address();
                        // Set public ip column with the vm ip
                    }
                }

                // Verify ip address parameter
                // checking vm id is not sufficient, check for the vm ip
                isIpReadyForStaticNat(vmId, ipAddress, dstIp, caller, ctx.getCallingUserId());
            }

            ipAddress.setOneToOneNat(true);
            ipAddress.setAssociatedWithVmId(vmId);

            ipAddress.setVmIp(dstIp);
            if (_ipAddressDao.update(ipAddress.getId(), ipAddress)) {
                // enable static nat on the backend
                s_logger.trace("Enabling static nat for ip address " + ipAddress + " and vm id=" + vmId + " on the backend");
                if (applyStaticNatForIp(ipId, false, caller, false)) {
                    performedIpAssoc = false; // ignor unassignIPFromVpcNetwork in finally block
                    return true;
                } else {
                    s_logger.warn("Failed to enable static nat rule for ip address " + ipId + " on the backend");
                    ipAddress.setOneToOneNat(isOneToOneNat);
                    ipAddress.setAssociatedWithVmId(associatedWithVmId);
                    ipAddress.setVmIp(null);
                    _ipAddressDao.update(ipAddress.getId(), ipAddress);
                }
            } else {
                s_logger.warn("Failed to update ip address " + ipAddress + " in the DB as a part of enableStaticNat");
            }
        } finally {
            if (performedIpAssoc) {
                //if the rule is the last one for the ip address assigned to VPC, unassign it from the network
                final IpAddress ip = _ipAddressDao.findById(ipAddress.getId());
                _vpcMgr.unassignIPFromVpcNetwork(ip.getId(), networkId);
            }
        }
        return false;
    }

    protected void isIpReadyForStaticNat(final long vmId, final IPAddressVO ipAddress, final String vmIp, final Account caller, final long callerUserId) throws
            NetworkRuleConflictException,
            ResourceUnavailableException {
        if (ipAddress.isSourceNat()) {
            throw new InvalidParameterValueException("Can't enable static, ip address " + ipAddress + " is a sourceNat ip address");
        }

        if (!ipAddress.isOneToOneNat()) { // Dont allow to enable static nat if PF/LB rules exist for the IP
            final List<FirewallRuleVO> portForwardingRules = _firewallDao.listByIpAndPurposeAndNotRevoked(ipAddress.getId(), Purpose.PortForwarding);
            if (portForwardingRules != null && !portForwardingRules.isEmpty()) {
                throw new NetworkRuleConflictException("Failed to enable static nat for the ip address " + ipAddress + " as it already has PortForwarding rules assigned");
            }

            final List<FirewallRuleVO> loadBalancingRules = _firewallDao.listByIpAndPurposeAndNotRevoked(ipAddress.getId(), Purpose.LoadBalancing);
            if (loadBalancingRules != null && !loadBalancingRules.isEmpty()) {
                throw new NetworkRuleConflictException("Failed to enable static nat for the ip address " + ipAddress + " as it already has LoadBalancing rules assigned");
            }
        } else if (ipAddress.getAssociatedWithVmId() != null && ipAddress.getAssociatedWithVmId().longValue() != vmId) {
            throw new NetworkRuleConflictException("Failed to enable static for the ip address " + ipAddress + " and vm id=" + vmId +
                    " as it's already assigned to antoher vm");
        }

        //check wether the vm ip is alreday associated with any public ip address
        final IPAddressVO oldIP = _ipAddressDao.findByAssociatedVmIdAndVmIp(vmId, vmIp);

        if (oldIP != null) {
            // If elasticIP functionality is supported in the network, we always have to disable static nat on the old
            // ip in order to re-enable it on the new one
            final Long networkId = oldIP.getAssociatedWithNetworkId();
            final VMInstanceVO vm = _vmInstanceDao.findById(vmId);

            boolean reassignStaticNat = false;
            if (networkId != null) {
                final Network guestNetwork = _networkModel.getNetwork(networkId);
                final NetworkOffering offering = _entityMgr.findById(NetworkOffering.class, guestNetwork.getNetworkOfferingId());
                if (offering.getElasticIp()) {
                    reassignStaticNat = true;
                }
            }

            // If there is public ip address already associated with the vm, throw an exception
            if (!reassignStaticNat) {
                throw new InvalidParameterValueException("Failed to enable static nat on the  ip " +
                        ipAddress.getAddress() + " with Id " + ipAddress.getUuid() + " as the vm " + vm.getInstanceName() + " with Id " +
                        vm.getUuid() + " is already associated with another public ip " + oldIP.getAddress() + " with id " +
                        oldIP.getUuid());
            }
            // unassign old static nat rule
            s_logger.debug("Disassociating static nat for ip " + oldIP);
            if (!disableStaticNat(oldIP.getId(), caller, callerUserId, true)) {
                throw new CloudRuntimeException("Failed to disable old static nat rule for vm " + vm.getInstanceName() +
                        " with id " + vm.getUuid() + "  and public ip " + oldIP);
            }
        }
    }

    @Override
    public void checkRuleAndUserVm(final FirewallRule rule, final UserVm userVm, final Account caller) {
        if (userVm == null || rule == null) {
            return;
        }

        _accountMgr.checkAccess(caller, null, true, rule, userVm);

        if (userVm.getState() == VirtualMachine.State.Destroyed || userVm.getState() == VirtualMachine.State.Expunging) {
            throw new InvalidParameterValueException("Invalid user vm: " + userVm.getId());
        }

        // This same owner check is actually not needed, since multiple entities OperateEntry trick guarantee that
        if (rule.getAccountId() != userVm.getAccountId()) {
            throw new InvalidParameterValueException("New rule " + rule + " and vm id=" + userVm.getId() + " belong to different accounts");
        }
    }

    @Override
    public Pair<List<? extends FirewallRule>, Integer> searchStaticNatRules(final Long ipId, final Long id, final Long vmId, final Long start, final Long size, final String
            accountName, Long domainId,
                                                                            final Long projectId, boolean isRecursive, final boolean listAll) {
        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();

        if (ipId != null) {
            final IPAddressVO ipAddressVO = _ipAddressDao.findById(ipId);
            if (ipAddressVO == null || !ipAddressVO.readyToUse()) {
                throw new InvalidParameterValueException("Ip address id=" + ipId + " not ready for port forwarding rules yet");
            }
            _accountMgr.checkAccess(caller, null, true, ipAddressVO);
        }

        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(domainId, isRecursive, null);
        _accountMgr.buildACLSearchParameters(caller, id, accountName, projectId, permittedAccounts, domainIdRecursiveListProject, listAll, false);
        domainId = domainIdRecursiveListProject.first();
        isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();

        final Filter filter = new Filter(PortForwardingRuleVO.class, "id", false, start, size);
        final SearchBuilder<FirewallRuleVO> sb = _firewallDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("ip", sb.entity().getSourceIpAddressId(), Op.EQ);
        sb.and("purpose", sb.entity().getPurpose(), Op.EQ);
        sb.and("id", sb.entity().getId(), Op.EQ);

        if (vmId != null) {
            final SearchBuilder<IPAddressVO> ipSearch = _ipAddressDao.createSearchBuilder();
            ipSearch.and("associatedWithVmId", ipSearch.entity().getAssociatedWithVmId(), Op.EQ);
            sb.join("ipSearch", ipSearch, sb.entity().getSourceIpAddressId(), ipSearch.entity().getId(), JoinBuilder.JoinType.INNER);
        }

        final SearchCriteria<FirewallRuleVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        sc.setParameters("purpose", Purpose.StaticNat);

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (ipId != null) {
            sc.setParameters("ip", ipId);
        }

        if (vmId != null) {
            sc.setJoinParameters("ipSearch", "associatedWithVmId", vmId);
        }

        final Pair<List<FirewallRuleVO>, Integer> result = _firewallDao.searchAndCount(sc, filter);
        return new Pair<>(result.first(), result.second());
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_ADD, eventDescription = "creating forwarding rule", create = true)
    public PortForwardingRule createPortForwardingRule(final PortForwardingRule rule, final Long vmId, final Ip vmIp, final boolean openFirewall, final Boolean forDisplay)
            throws NetworkRuleConflictException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();

        final Long ipAddrId = rule.getSourceIpAddressId();

        IPAddressVO ipAddress = _ipAddressDao.findById(ipAddrId);

        // Validate ip address
        if (ipAddress == null) {
            throw new InvalidParameterValueException("Unable to create port forwarding rule; ip id=" + ipAddrId + " doesn't exist in the system");
        } else if (ipAddress.isOneToOneNat()) {
            throw new InvalidParameterValueException("Unable to create port forwarding rule; ip id=" + ipAddrId + " has static nat enabled");
        }

        final Long networkId = rule.getNetworkId();
        final Network network = _networkModel.getNetwork(networkId);
        //associate ip address to network (if needed)
        boolean performedIpAssoc = false;
        final Nic guestNic;
        if (ipAddress.getAssociatedWithNetworkId() == null) {
            final boolean assignToVpcNtwk = network.getVpcId() != null && ipAddress.getVpcId() != null && ipAddress.getVpcId().longValue() == network.getVpcId();
            if (assignToVpcNtwk) {
                _networkModel.checkIpForService(ipAddress, Service.PortForwarding, networkId);

                s_logger.debug("The ip is not associated with the VPC network id=" + networkId + ", so assigning");
                try {
                    ipAddress = _ipAddrMgr.associateIPToGuestNetwork(ipAddrId, networkId, false);
                    performedIpAssoc = true;
                } catch (final Exception ex) {
                    throw new CloudRuntimeException("Failed to associate ip to VPC network as " + "a part of port forwarding rule creation");
                }
            }
        } else {
            _networkModel.checkIpForService(ipAddress, Service.PortForwarding, null);
        }

        if (ipAddress.getAssociatedWithNetworkId() == null) {
            throw new InvalidParameterValueException("Ip address " + ipAddress + " is not assigned to the network " + network);
        }

        try {
            _firewallMgr.validateFirewallRule(caller, ipAddress, rule.getSourcePortStart(), rule.getSourcePortEnd(), rule.getProtocol(), Purpose.PortForwarding,
                    FirewallRuleType.User, networkId, rule.getTrafficType());

            final Long accountId = ipAddress.getAllocatedToAccountId();
            final Long domainId = ipAddress.getAllocatedInDomainId();

            // start port can't be bigger than end port
            if (rule.getDestinationPortStart() > rule.getDestinationPortEnd()) {
                throw new InvalidParameterValueException("Start port can't be bigger than end port");
            }

            // check that the port ranges are of equal size
            if ((rule.getDestinationPortEnd() - rule.getDestinationPortStart()) != (rule.getSourcePortEnd() - rule.getSourcePortStart())) {
                throw new InvalidParameterValueException("Source port and destination port ranges should be of equal sizes.");
            }

            // validate user VM exists
            final UserVm vm = _vmDao.findById(vmId);
            if (vm == null) {
                throw new InvalidParameterValueException("Unable to create port forwarding rule on address " + ipAddress + ", invalid virtual machine id specified (" +
                        vmId + ").");
            } else if (vm.getState() == VirtualMachine.State.Destroyed || vm.getState() == VirtualMachine.State.Expunging) {
                throw new InvalidParameterValueException("Invalid user vm: " + vm.getId());
            }

            // Verify that vm has nic in the network
            Ip dstIp = rule.getDestinationIpAddress();
            guestNic = _networkModel.getNicInNetwork(vmId, networkId);
            if (guestNic == null || guestNic.getIPv4Address() == null) {
                throw new InvalidParameterValueException("Vm doesn't belong to network associated with ipAddress");
            } else {
                dstIp = new Ip(guestNic.getIPv4Address());
            }

            if (vmIp != null) {
                //vm ip is passed so it can be primary or secondary ip addreess.
                if (!dstIp.equals(vmIp)) {
                    //the vm ip is secondary ip to the nic.
                    // is vmIp is secondary ip or not
                    final NicSecondaryIp secondaryIp = _nicSecondaryDao.findByIp4AddressAndNicId(vmIp.toString(), guestNic.getId());
                    if (secondaryIp == null) {
                        throw new InvalidParameterValueException("IP Address is not in the VM nic's network ");
                    }
                    dstIp = vmIp;
                }
            }

            //if start port and end port are passed in, and they are not equal to each other, perform the validation
            boolean validatePortRange = false;
            if (rule.getSourcePortStart().intValue() != rule.getSourcePortEnd().intValue() || rule.getDestinationPortStart() != rule.getDestinationPortEnd()) {
                validatePortRange = true;
            }

            if (validatePortRange) {
                //source start port and source dest port should be the same. The same applies to dest ports
                if (rule.getSourcePortStart().intValue() != rule.getDestinationPortStart()) {
                    throw new InvalidParameterValueException("Private port start should be equal to public port start");
                }

                if (rule.getSourcePortEnd().intValue() != rule.getDestinationPortEnd()) {
                    throw new InvalidParameterValueException("Private port end should be equal to public port end");
                }
            }

            final Ip dstIpFinal = dstIp;
            final IPAddressVO ipAddressFinal = ipAddress;
            return Transaction.execute(new TransactionCallbackWithException<PortForwardingRuleVO, NetworkRuleConflictException>() {
                @Override
                public PortForwardingRuleVO doInTransaction(final TransactionStatus status) throws NetworkRuleConflictException {
                    PortForwardingRuleVO newRule =
                            new PortForwardingRuleVO(rule.getXid(), rule.getSourceIpAddressId(), rule.getSourcePortStart(), rule.getSourcePortEnd(), dstIpFinal,
                                    rule.getDestinationPortStart(), rule.getDestinationPortEnd(), rule.getProtocol().toLowerCase(), networkId, accountId, domainId, vmId);

                    if (forDisplay != null) {
                        newRule.setDisplay(forDisplay);
                    }
                    newRule = _portForwardingDao.persist(newRule);

                    // create firewallRule for 0.0.0.0/0 cidr
                    if (openFirewall) {
                        _firewallMgr.createRuleForAllCidrs(ipAddrId, caller, rule.getSourcePortStart(), rule.getSourcePortEnd(), rule.getProtocol(), null, null,
                                newRule.getId(), networkId);
                    }

                    try {
                        _firewallMgr.detectRulesConflict(newRule);
                        if (!_firewallDao.setStateToAdd(newRule)) {
                            throw new CloudRuntimeException("Unable to update the state to add for " + newRule);
                        }
                        CallContext.current().setEventDetails("Rule Id: " + newRule.getId());

                        return newRule;
                    } catch (final Exception e) {
                        if (newRule != null) {
                            // no need to apply the rule as it wasn't programmed on the backend yet
                            _firewallMgr.revokeRelatedFirewallRule(newRule.getId(), false);
                            removePFRule(newRule);
                        }

                        if (e instanceof NetworkRuleConflictException) {
                            throw (NetworkRuleConflictException) e;
                        }

                        throw new CloudRuntimeException("Unable to add rule for the ip id=" + ipAddrId, e);
                    }
                }
            });
        } finally {
            // release ip address if ipassoc was perfored
            if (performedIpAssoc) {
                //if the rule is the last one for the ip address assigned to VPC, unassign it from the network
                final IpAddress ip = _ipAddressDao.findById(ipAddress.getId());
                _vpcMgr.unassignIPFromVpcNetwork(ip.getId(), networkId);
            }
        }
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_DELETE, eventDescription = "revoking forwarding rule", async = true)
    public boolean revokePortForwardingRule(final long ruleId, final boolean apply) {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();

        final PortForwardingRuleVO rule = _portForwardingDao.findById(ruleId);
        if (rule == null) {
            throw new InvalidParameterValueException("Unable to find " + ruleId);
        }

        _accountMgr.checkAccess(caller, null, true, rule);

        if (!revokePortForwardingRuleInternal(ruleId, caller, ctx.getCallingUserId(), apply)) {
            throw new CloudRuntimeException("Failed to delete port forwarding rule");
        }
        return true;
    }

    private boolean revokePortForwardingRuleInternal(final long ruleId, final Account caller, final long userId, final boolean apply) {
        final PortForwardingRuleVO rule = _portForwardingDao.findById(ruleId);

        _firewallMgr.revokeRule(rule, caller, userId, true);

        boolean success = false;

        if (apply) {
            success = applyPortForwardingRules(rule.getSourceIpAddressId(), true, caller);
        } else {
            success = true;
        }

        return success;
    }

    protected boolean applyPortForwardingRules(final long ipId, final boolean continueOnError, final Account caller) {
        final List<PortForwardingRuleVO> rules = _portForwardingDao.listForApplication(ipId);

        if (rules.size() == 0) {
            s_logger.debug("There are no port forwarding rules to apply for ip id=" + ipId);
            return true;
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, rules.toArray(new PortForwardingRuleVO[rules.size()]));
        }

        try {
            if (!_firewallMgr.applyRules(rules, continueOnError, true)) {
                return false;
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Failed to apply port forwarding rules for ip due to ", ex);
            return false;
        }

        return true;
    }

    @Override
    public Pair<List<? extends PortForwardingRule>, Integer> listPortForwardingRules(final ListPortForwardingRulesCmd cmd) {
        final Long ipId = cmd.getIpAddressId();
        final Long id = cmd.getId();
        final Map<String, String> tags = cmd.getTags();
        final Long networkId = cmd.getNetworkId();
        final Boolean display = cmd.getDisplay();

        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();

        if (ipId != null) {
            final IPAddressVO ipAddressVO = _ipAddressDao.findById(ipId);
            if (ipAddressVO == null || !ipAddressVO.readyToUse()) {
                throw new InvalidParameterValueException("Ip address id=" + ipId + " not ready for port forwarding rules yet");
            }
            _accountMgr.checkAccess(caller, null, true, ipAddressVO);
        }

        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(cmd.getDomainId(), cmd.isRecursive(), null);
        _accountMgr.buildACLSearchParameters(caller, id, cmd.getAccountName(), cmd.getProjectId(), permittedAccounts, domainIdRecursiveListProject, cmd.listAll(), false);
        final Long domainId = domainIdRecursiveListProject.first();
        final Boolean isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();

        final Filter filter = new Filter(PortForwardingRuleVO.class, "id", false, cmd.getStartIndex(), cmd.getPageSizeVal());
        final SearchBuilder<PortForwardingRuleVO> sb = _portForwardingDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("id", sb.entity().getId(), Op.EQ);
        sb.and("ip", sb.entity().getSourceIpAddressId(), Op.EQ);
        sb.and("purpose", sb.entity().getPurpose(), Op.EQ);
        sb.and("networkId", sb.entity().getNetworkId(), Op.EQ);
        sb.and("display", sb.entity().isDisplay(), Op.EQ);

        if (tags != null && !tags.isEmpty()) {
            final SearchBuilder<ResourceTagVO> tagSearch = _resourceTagDao.createSearchBuilder();
            for (int count = 0; count < tags.size(); count++) {
                tagSearch.or().op("key" + String.valueOf(count), tagSearch.entity().getKey(), SearchCriteria.Op.EQ);
                tagSearch.and("value" + String.valueOf(count), tagSearch.entity().getValue(), SearchCriteria.Op.EQ);
                tagSearch.cp();
            }
            tagSearch.and("resourceType", tagSearch.entity().getResourceType(), SearchCriteria.Op.EQ);
            sb.groupBy(sb.entity().getId());
            sb.join("tagSearch", tagSearch, sb.entity().getId(), tagSearch.entity().getResourceId(), JoinBuilder.JoinType.INNER);
        }

        final SearchCriteria<PortForwardingRuleVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (display != null) {
            sc.setParameters("display", display);
        }

        if (tags != null && !tags.isEmpty()) {
            int count = 0;
            sc.setJoinParameters("tagSearch", "resourceType", ResourceObjectType.PortForwardingRule.toString());
            for (final String key : tags.keySet()) {
                sc.setJoinParameters("tagSearch", "key" + String.valueOf(count), key);
                sc.setJoinParameters("tagSearch", "value" + String.valueOf(count), tags.get(key));
                count++;
            }
        }

        if (ipId != null) {
            sc.setParameters("ip", ipId);
        }

        if (networkId != null) {
            sc.setParameters("networkId", networkId);
        }

        sc.setParameters("purpose", Purpose.PortForwarding);

        final Pair<List<PortForwardingRuleVO>, Integer> result = _portForwardingDao.searchAndCount(sc, filter);
        return new Pair<>(result.first(), result.second());
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_ADD, eventDescription = "applying port forwarding rule", async = true)
    public boolean applyPortForwardingRules(final long ipId, final Account caller) throws ResourceUnavailableException {
        if (!applyPortForwardingRules(ipId, false, caller)) {
            throw new CloudRuntimeException("Failed to apply port forwarding rule");
        }
        return true;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_ENABLE_STATIC_NAT, eventDescription = "enabling static nat")
    public boolean enableStaticNat(final long ipId, final long vmId, final long networkId, final String vmGuestIp) throws NetworkRuleConflictException,
            ResourceUnavailableException {
        return enableStaticNat(ipId, vmId, networkId, false, vmGuestIp);
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_ADD, eventDescription = "creating static nat rule", create = true)
    public StaticNatRule createStaticNatRule(final StaticNatRule rule, final boolean openFirewall) throws NetworkRuleConflictException {
        final Account caller = CallContext.current().getCallingAccount();

        final Long ipAddrId = rule.getSourceIpAddressId();

        final IPAddressVO ipAddress = _ipAddressDao.findById(ipAddrId);

        // Validate ip address
        if (ipAddress == null) {
            throw new InvalidParameterValueException("Unable to create static nat rule; ip id=" + ipAddrId + " doesn't exist in the system");
        } else if (ipAddress.isSourceNat() || !ipAddress.isOneToOneNat() || ipAddress.getAssociatedWithVmId() == null) {
            throw new NetworkRuleConflictException("Can't do static nat on ip address: " + ipAddress.getAddress());
        }

        _firewallMgr.validateFirewallRule(caller, ipAddress, rule.getSourcePortStart(), rule.getSourcePortEnd(), rule.getProtocol(), Purpose.StaticNat,
                FirewallRuleType.User, null, rule.getTrafficType());

        final Long networkId = ipAddress.getAssociatedWithNetworkId();
        final Long accountId = ipAddress.getAllocatedToAccountId();
        final Long domainId = ipAddress.getAllocatedInDomainId();

        _networkModel.checkIpForService(ipAddress, Service.StaticNat, null);

        final Network network = _networkModel.getNetwork(networkId);
        final NetworkOffering off = _entityMgr.findById(NetworkOffering.class, network.getNetworkOfferingId());
        if (off.getElasticIp()) {
            throw new InvalidParameterValueException("Can't create ip forwarding rules for the network where elasticIP service is enabled");
        }

        //String dstIp = _networkModel.getIpInNetwork(ipAddress.getAssociatedWithVmId(), networkId);
        final String dstIp = ipAddress.getVmIp();
        return Transaction.execute(new TransactionCallbackWithException<StaticNatRule, NetworkRuleConflictException>() {
            @Override
            public StaticNatRule doInTransaction(final TransactionStatus status) throws NetworkRuleConflictException {

                FirewallRuleVO newRule =
                        new FirewallRuleVO(rule.getXid(), rule.getSourceIpAddressId(), rule.getSourcePortStart(), rule.getSourcePortEnd(), rule.getProtocol().toLowerCase(),
                                networkId, accountId, domainId, rule.getPurpose(), null, null, null, null, null);

                newRule = _firewallDao.persist(newRule);

                // create firewallRule for 0.0.0.0/0 cidr
                if (openFirewall) {
                    _firewallMgr.createRuleForAllCidrs(ipAddrId, caller, rule.getSourcePortStart(), rule.getSourcePortEnd(), rule.getProtocol(), null, null,
                            newRule.getId(), networkId);
                }

                try {
                    _firewallMgr.detectRulesConflict(newRule);
                    if (!_firewallDao.setStateToAdd(newRule)) {
                        throw new CloudRuntimeException("Unable to update the state to add for " + newRule);
                    }
                    CallContext.current().setEventDetails("Rule Id: " + newRule.getId());

                    final StaticNatRule staticNatRule = new StaticNatRuleImpl(newRule, dstIp);

                    return staticNatRule;
                } catch (final Exception e) {
                    if (newRule != null) {
                        // no need to apply the rule as it wasn't programmed on the backend yet
                        _firewallMgr.revokeRelatedFirewallRule(newRule.getId(), false);
                        _firewallMgr.removeRule(newRule);
                    }

                    if (e instanceof NetworkRuleConflictException) {
                        throw (NetworkRuleConflictException) e;
                    }
                    throw new CloudRuntimeException("Unable to add static nat rule for the ip id=" + newRule.getSourceIpAddressId(), e);
                }
            }
        });
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_DELETE, eventDescription = "revoking forwarding rule", async = true)
    public boolean revokeStaticNatRule(final long ruleId, final boolean apply) {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();

        final FirewallRuleVO rule = _firewallDao.findById(ruleId);
        if (rule == null) {
            throw new InvalidParameterValueException("Unable to find " + ruleId);
        }

        _accountMgr.checkAccess(caller, null, true, rule);

        if (!revokeStaticNatRuleInternal(ruleId, caller, ctx.getCallingUserId(), apply)) {
            throw new CloudRuntimeException("Failed to revoke forwarding rule");
        }
        return true;
    }

    private boolean revokeStaticNatRuleInternal(final long ruleId, final Account caller, final long userId, final boolean apply) {
        final FirewallRuleVO rule = _firewallDao.findById(ruleId);

        _firewallMgr.revokeRule(rule, caller, userId, true);

        boolean success = false;

        if (apply) {
            success = applyStaticNatRulesForIp(rule.getSourceIpAddressId(), true, caller, true);
        } else {
            success = true;
        }

        return success;
    }

    @Override
    public boolean revokePortForwardingRulesForVm(final long vmId) {
        boolean success = true;
        final UserVmVO vm = _vmDao.findByIdIncludingRemoved(vmId);
        if (vm == null) {
            return false;
        }

        final List<PortForwardingRuleVO> rules = _portForwardingDao.listByVm(vmId);
        final Set<Long> ipsToReprogram = new HashSet<>();

        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No port forwarding rules are found for vm id=" + vmId);
            return true;
        }

        for (final PortForwardingRuleVO rule : rules) {
            // Mark port forwarding rule as Revoked, but don't revoke it yet (apply=false)
            revokePortForwardingRuleInternal(rule.getId(), _accountMgr.getSystemAccount(), Account.ACCOUNT_ID_SYSTEM, false);
            ipsToReprogram.add(rule.getSourceIpAddressId());
        }

        // apply rules for all ip addresses
        for (final Long ipId : ipsToReprogram) {
            s_logger.debug("Applying port forwarding rules for ip address id=" + ipId + " as a part of vm expunge");
            if (!applyPortForwardingRules(ipId, true, _accountMgr.getSystemAccount())) {
                s_logger.warn("Failed to apply port forwarding rules for ip id=" + ipId);
                success = false;
            }
        }

        return success;
    }

    protected boolean applyStaticNatRulesForIp(final long sourceIpId, final boolean continueOnError, final Account caller, final boolean forRevoke) {
        final List<? extends FirewallRule> rules = _firewallDao.listByIpAndPurpose(sourceIpId, Purpose.StaticNat);
        final List<StaticNatRule> staticNatRules = new ArrayList<>();

        if (rules.size() == 0) {
            s_logger.debug("There are no static nat rules to apply for ip id=" + sourceIpId);
            return true;
        }

        for (final FirewallRule rule : rules) {
            staticNatRules.add(buildStaticNatRule(rule, forRevoke));
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, staticNatRules.toArray(new StaticNatRule[staticNatRules.size()]));
        }

        try {
            if (!_firewallMgr.applyRules(staticNatRules, continueOnError, true)) {
                return false;
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Failed to apply static nat rules for ip due to ", ex);
            return false;
        }

        return true;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_ADD, eventDescription = "applying static nat rule", async = true)
    public boolean applyStaticNatRules(final long ipId, final Account caller) throws ResourceUnavailableException {
        if (!applyStaticNatRulesForIp(ipId, false, caller, false)) {
            throw new CloudRuntimeException("Failed to apply static nat rule");
        }
        return true;
    }

    @Override
    public StaticNatRule buildStaticNatRule(final FirewallRule rule, final boolean forRevoke) {
        final IpAddress ip = _ipAddressDao.findById(rule.getSourceIpAddressId());
        final FirewallRuleVO ruleVO = _firewallDao.findById(rule.getId());

        if (ip == null || !ip.isOneToOneNat() || ip.getAssociatedWithVmId() == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("Source ip address of the specified firewall rule id is not static nat enabled");
            ex.addProxyObject(ruleVO.getUuid(), "ruleId");
            throw ex;
        }

        final String dstIp = ip.getVmIp();
        if (dstIp == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("VM ip address of the specified public ip is not set ");
            ex.addProxyObject(ruleVO.getUuid(), "ruleId");
            throw ex;
        }

        return new StaticNatRuleImpl(ruleVO, dstIp);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_DISABLE_STATIC_NAT, eventDescription = "disabling static nat", async = true)
    public boolean disableStaticNat(final long ipId) throws ResourceUnavailableException, NetworkRuleConflictException, InsufficientAddressCapacityException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        final IPAddressVO ipAddress = _ipAddressDao.findById(ipId);
        checkIpAndUserVm(ipAddress, null, caller, false);

        if (ipAddress.getSystem()) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("Can't disable static nat for system IP address with specified id");
            ex.addProxyObject(ipAddress.getUuid(), "ipId");
            throw ex;
        }

        final Long vmId = ipAddress.getAssociatedWithVmId();
        if (vmId == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("Specified IP address id is not associated with any vm Id");
            ex.addProxyObject(ipAddress.getUuid(), "ipId");
            throw ex;
        }

        // if network has elastic IP functionality supported, we first have to disable static nat on old ip in order to
        // re-enable it on the new one enable static nat takes care of that
        final Network guestNetwork = _networkModel.getNetwork(ipAddress.getAssociatedWithNetworkId());
        final NetworkOffering offering = _entityMgr.findById(NetworkOffering.class, guestNetwork.getNetworkOfferingId());
        if (offering.getElasticIp()) {
            if (offering.getAssociatePublicIP()) {
                getSystemIpAndEnableStaticNatForVm(_vmDao.findById(vmId), true);
                return true;
            }
        }

        return disableStaticNat(ipId, caller, ctx.getCallingUserId(), false);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_RULE_MODIFY, eventDescription = "updating forwarding rule", async = true)
    public PortForwardingRule updatePortForwardingRule(final long id, final Integer privatePort, final Long virtualMachineId, final Ip vmGuestIp, final String customId, final
    Boolean forDisplay) {
        final Account caller = CallContext.current().getCallingAccount();
        PortForwardingRuleVO rule = _portForwardingDao.findById(id);
        if (rule == null) {
            throw new InvalidParameterValueException("Unable to find " + id);
        }
        _accountMgr.checkAccess(caller, null, true, rule);

        if (customId != null) {
            rule.setUuid(customId);
        }

        if (forDisplay != null) {
            rule.setDisplay(forDisplay);
        }

        if (!rule.getSourcePortStart().equals(rule.getSourcePortEnd()) && privatePort != null) {
            throw new InvalidParameterValueException("Unable to update the private port of port forwarding rule as  the rule has port range : " + rule.getSourcePortStart() + " " +
                    "to " + rule.getSourcePortEnd());
        }
        if (virtualMachineId == null && vmGuestIp != null) {
            throw new InvalidParameterValueException("vmguestip should be set along with virtualmachineid");
        }
        Ip dstIp = rule.getDestinationIpAddress();
        if (virtualMachineId != null) {
            // Verify that vm has nic in the network
            final Nic guestNic = _networkModel.getNicInNetwork(virtualMachineId, rule.getNetworkId());
            if (guestNic == null || guestNic.getIPv4Address() == null) {
                throw new InvalidParameterValueException("Vm doesn't belong to network associated with ipAddress");
            } else {
                dstIp = new Ip(guestNic.getIPv4Address());
            }

            if (vmGuestIp != null) {
                //vm ip is passed so it can be primary or secondary ip addreess.
                if (!dstIp.equals(vmGuestIp)) {
                    //the vm ip is secondary ip to the nic.
                    // is vmIp is secondary ip or not
                    final NicSecondaryIp secondaryIp = _nicSecondaryDao.findByIp4AddressAndNicId(vmGuestIp.toString(), guestNic.getId());
                    if (secondaryIp == null) {
                        throw new InvalidParameterValueException("IP Address is not in the VM nic's network ");
                    }
                    dstIp = vmGuestIp;
                }
            }
        }

        // revoke old rules at first
        final List<PortForwardingRuleVO> rules = new ArrayList<>();
        rule.setState(State.Revoke);
        _portForwardingDao.update(id, rule);
        rules.add(rule);
        try {
            if (!_firewallMgr.applyRules(rules, true, false)) {
                throw new CloudRuntimeException("Failed to revoke the existing port forwarding rule:" + id);
            }
        } catch (final ResourceUnavailableException ex) {
            throw new CloudRuntimeException("Failed to revoke the existing port forwarding rule:" + id + " due to ", ex);
        }

        rule = _portForwardingDao.findById(id);
        rule.setState(State.Add);
        if (privatePort != null) {
            rule.setDestinationPortStart(privatePort.intValue());
            rule.setDestinationPortEnd(privatePort.intValue());
        }
        if (virtualMachineId != null) {
            rule.setVirtualMachineId(virtualMachineId);
            rule.setDestinationIpAddress(dstIp);
        }
        _portForwardingDao.update(id, rule);

        //apply new rules
        if (!applyPortForwardingRules(rule.getSourceIpAddressId(), false, caller)) {
            throw new CloudRuntimeException("Failed to apply the new port forwarding rule:" + id);
        }

        return _portForwardingDao.findById(id);
    }

    @Override
    public boolean applyPortForwardingRulesForNetwork(final long networkId, final boolean continueOnError, final Account caller) {
        final List<PortForwardingRuleVO> rules = listByNetworkId(networkId);
        if (rules.size() == 0) {
            s_logger.debug("There are no port forwarding rules to apply for network id=" + networkId);
            return true;
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, rules.toArray(new PortForwardingRuleVO[rules.size()]));
        }

        try {
            if (!_firewallMgr.applyRules(rules, continueOnError, true)) {
                return false;
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Failed to apply port forwarding rules for network due to ", ex);
            return false;
        }

        return true;
    }

    protected void removePFRule(final PortForwardingRuleVO rule) {
        _portForwardingDao.remove(rule.getId());
    }

    protected boolean applyStaticNatForIp(final long sourceIpId, final boolean continueOnError, final Account caller, final boolean forRevoke) {
        final IpAddress sourceIp = _ipAddressDao.findById(sourceIpId);

        final List<StaticNat> staticNats = createStaticNatForIp(sourceIp, caller, forRevoke);

        if (staticNats != null && !staticNats.isEmpty()) {
            try {
                if (!_ipAddrMgr.applyStaticNats(staticNats, continueOnError, forRevoke)) {
                    return false;
                }
            } catch (final ResourceUnavailableException ex) {
                s_logger.warn("Failed to create static nat rule due to ", ex);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean applyStaticNatRulesForNetwork(final long networkId, final boolean continueOnError, final Account caller) {
        final List<FirewallRuleVO> rules = _firewallDao.listByNetworkAndPurpose(networkId, Purpose.StaticNat);
        final List<StaticNatRule> staticNatRules = new ArrayList<>();

        if (rules.size() == 0) {
            s_logger.debug("There are no static nat rules to apply for network id=" + networkId);
            return true;
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, rules.toArray(new FirewallRule[rules.size()]));
        }

        for (final FirewallRuleVO rule : rules) {
            staticNatRules.add(buildStaticNatRule(rule, false));
        }

        try {
            if (!_firewallMgr.applyRules(staticNatRules, continueOnError, true)) {
                return false;
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Failed to apply static nat rules for network due to ", ex);
            return false;
        }

        return true;
    }

    @Override
    public boolean applyStaticNatsForNetwork(final long networkId, final boolean continueOnError, final Account caller) {
        final List<IPAddressVO> ips = _ipAddressDao.listStaticNatPublicIps(networkId);
        if (ips.isEmpty()) {
            s_logger.debug("There are no static nat to apply for network id=" + networkId);
            return true;
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, ips.toArray(new IPAddressVO[ips.size()]));
        }

        final List<StaticNat> staticNats = new ArrayList<>();
        for (final IPAddressVO ip : ips) {
            // Get nic IP4 address
            //String dstIp = _networkModel.getIpInNetwork(ip.getAssociatedWithVmId(), networkId);
            final StaticNatImpl staticNat = new StaticNatImpl(ip.getAllocatedToAccountId(), ip.getAllocatedInDomainId(), networkId, ip.getId(), ip.getVmIp(), false);
            staticNats.add(staticNat);
        }

        try {
            if (!_ipAddrMgr.applyStaticNats(staticNats, continueOnError, false)) {
                return false;
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Failed to create static nat for network due to ", ex);
            return false;
        }

        return true;
    }

    @Override
    public boolean revokeAllPFAndStaticNatRulesForIp(final long ipId, final long userId, final Account caller) throws ResourceUnavailableException {
        final List<FirewallRule> rules = new ArrayList<>();

        final List<PortForwardingRuleVO> pfRules = _portForwardingDao.listByIpAndNotRevoked(ipId);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Releasing " + pfRules.size() + " port forwarding rules for ip id=" + ipId);
        }

        for (final PortForwardingRuleVO rule : pfRules) {
            // Mark all PF rules as Revoke, but don't revoke them yet
            revokePortForwardingRuleInternal(rule.getId(), caller, userId, false);
        }

        final List<FirewallRuleVO> staticNatRules = _firewallDao.listByIpAndPurposeAndNotRevoked(ipId, Purpose.StaticNat);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Releasing " + staticNatRules.size() + " static nat rules for ip id=" + ipId);
        }

        for (final FirewallRuleVO rule : staticNatRules) {
            // Mark all static nat rules as Revoke, but don't revoke them yet
            revokeStaticNatRuleInternal(rule.getId(), caller, userId, false);
        }

        boolean success = true;

        // revoke all port forwarding rules
        success = success && applyPortForwardingRules(ipId, true, caller);

        // revoke all all static nat rules
        success = success && applyStaticNatRulesForIp(ipId, true, caller, true);

        // revoke static nat for the ip address
        success = success && applyStaticNatForIp(ipId, false, caller, true);

        // Now we check again in case more rules have been inserted.
        rules.addAll(_portForwardingDao.listByIpAndNotRevoked(ipId));
        rules.addAll(_firewallDao.listByIpAndPurposeAndNotRevoked(ipId, Purpose.StaticNat));

        if (s_logger.isDebugEnabled() && success) {
            s_logger.debug("Successfully released rules for ip id=" + ipId + " and # of rules now = " + rules.size());
        }

        return (rules.size() == 0 && success);
    }

    @Override
    public boolean revokeAllPFStaticNatRulesForNetwork(final long networkId, final long userId, final Account caller) throws ResourceUnavailableException {
        final List<FirewallRule> rules = new ArrayList<>();

        final List<PortForwardingRuleVO> pfRules = _portForwardingDao.listByNetwork(networkId);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Releasing " + pfRules.size() + " port forwarding rules for network id=" + networkId);
        }

        final List<FirewallRuleVO> staticNatRules = _firewallDao.listByNetworkAndPurpose(networkId, Purpose.StaticNat);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Releasing " + staticNatRules.size() + " static nat rules for network id=" + networkId);
        }

        // Mark all pf rules (Active and non-Active) to be revoked, but don't revoke it yet - pass apply=false
        for (final PortForwardingRuleVO rule : pfRules) {
            revokePortForwardingRuleInternal(rule.getId(), caller, userId, false);
        }

        // Mark all static nat rules (Active and non-Active) to be revoked, but don't revoke it yet - pass apply=false
        for (final FirewallRuleVO rule : staticNatRules) {
            revokeStaticNatRuleInternal(rule.getId(), caller, userId, false);
        }

        boolean success = true;
        // revoke all PF rules for the network
        success = success && applyPortForwardingRulesForNetwork(networkId, true, caller);

        // revoke all all static nat rules for the network
        success = success && applyStaticNatRulesForNetwork(networkId, true, caller);

        // Now we check again in case more rules have been inserted.
        rules.addAll(_portForwardingDao.listByNetworkAndNotRevoked(networkId));
        rules.addAll(_firewallDao.listByNetworkAndPurposeAndNotRevoked(networkId, Purpose.StaticNat));

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Successfully released rules for network id=" + networkId + " and # of rules now = " + rules.size());
        }

        return success && rules.size() == 0;
    }

    @Override
    @DB
    public FirewallRuleVO[] reservePorts(final IpAddress ip, final String protocol, final FirewallRule.Purpose purpose, final boolean openFirewall, final Account caller,
                                         final int... ports) throws NetworkRuleConflictException {
        final FirewallRuleVO[] rules = new FirewallRuleVO[ports.length];

        Transaction.execute(new TransactionCallbackWithExceptionNoReturn<NetworkRuleConflictException>() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) throws NetworkRuleConflictException {
                for (int i = 0; i < ports.length; i++) {

                    rules[i] =
                            new FirewallRuleVO(null, ip.getId(), ports[i], protocol, ip.getAssociatedWithNetworkId(), ip.getAllocatedToAccountId(),
                                    ip.getAllocatedInDomainId(), purpose, null, null, null, null);
                    rules[i] = _firewallDao.persist(rules[i]);

                    if (openFirewall) {
                        _firewallMgr.createRuleForAllCidrs(ip.getId(), caller, ports[i], ports[i], protocol, null, null, rules[i].getId(),
                                ip.getAssociatedWithNetworkId());
                    }
                }
            }
        });

        boolean success = false;
        try {
            for (final FirewallRuleVO newRule : rules) {
                _firewallMgr.detectRulesConflict(newRule);
            }
            success = true;
            return rules;
        } finally {
            if (!success) {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(final TransactionStatus status) {
                        for (final FirewallRuleVO newRule : rules) {
                            _firewallMgr.removeRule(newRule);
                        }
                    }
                });
            }
        }
    }

    private List<PortForwardingRuleVO> listByNetworkId(final long networkId) {
        return _portForwardingDao.listByNetwork(networkId);
    }

    @Override
    public boolean disableStaticNat(final long ipId, final Account caller, final long callerUserId, final boolean releaseIpIfElastic) throws ResourceUnavailableException {
        boolean success = true;

        final IPAddressVO ipAddress = _ipAddressDao.findById(ipId);
        checkIpAndUserVm(ipAddress, null, caller, false);
        final long networkId = ipAddress.getAssociatedWithNetworkId();

        if (!ipAddress.isOneToOneNat()) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("One to one nat is not enabled for the specified ip id");
            ex.addProxyObject(ipAddress.getUuid(), "ipId");
            throw ex;
        }

        // Revoke all firewall rules for the ip
        try {
            s_logger.debug("Revoking all " + Purpose.Firewall + "rules as a part of disabling static nat for public IP id=" + ipId);
            if (!_firewallMgr.revokeFirewallRulesForIp(ipId, callerUserId, caller)) {
                s_logger.warn("Unable to revoke all the firewall rules for ip id=" + ipId + " as a part of disable statis nat");
                success = false;
            }
        } catch (final ResourceUnavailableException e) {
            s_logger.warn("Unable to revoke all firewall rules for ip id=" + ipId + " as a part of ip release", e);
            success = false;
        }

        if (!revokeAllPFAndStaticNatRulesForIp(ipId, callerUserId, caller)) {
            s_logger.warn("Unable to revoke all static nat rules for ip " + ipAddress);
            success = false;
        }

        if (success) {
            final boolean isIpSystem = ipAddress.getSystem();
            ipAddress.setOneToOneNat(false);
            ipAddress.setAssociatedWithVmId(null);
            ipAddress.setVmIp(null);
            if (isIpSystem && !releaseIpIfElastic) {
                ipAddress.setSystem(false);
            }
            _ipAddressDao.update(ipAddress.getId(), ipAddress);
            _vpcMgr.unassignIPFromVpcNetwork(ipAddress.getId(), networkId);

            if (isIpSystem && releaseIpIfElastic && !_ipAddrMgr.handleSystemIpRelease(ipAddress)) {
                s_logger.warn("Failed to release system ip address " + ipAddress);
                success = false;
            }

            return true;
        } else {
            s_logger.warn("Failed to disable one to one nat for the ip address id" + ipId);
            return false;
        }
    }

    @Override
    public boolean applyStaticNatForNetwork(final long networkId, final boolean continueOnError, final Account caller, final boolean forRevoke) {
        final List<? extends IpAddress> staticNatIps = _ipAddressDao.listStaticNatPublicIps(networkId);

        final List<StaticNat> staticNats = new ArrayList<>();
        for (final IpAddress staticNatIp : staticNatIps) {
            staticNats.addAll(createStaticNatForIp(staticNatIp, caller, forRevoke));
        }

        if (staticNats != null && !staticNats.isEmpty()) {
            if (forRevoke) {
                s_logger.debug("Found " + staticNats.size() + " static nats to disable for network id " + networkId);
            }
            try {
                if (!_ipAddrMgr.applyStaticNats(staticNats, continueOnError, forRevoke)) {
                    return false;
                }
            } catch (final ResourceUnavailableException ex) {
                s_logger.warn("Failed to create static nat rule due to ", ex);
                return false;
            }
        } else {
            s_logger.debug("Found 0 static nat rules to apply for network id " + networkId);
        }

        return true;
    }

    protected List<StaticNat> createStaticNatForIp(final IpAddress sourceIp, final Account caller, final boolean forRevoke) {
        final List<StaticNat> staticNats = new ArrayList<>();
        if (!sourceIp.isOneToOneNat()) {
            s_logger.debug("Source ip id=" + sourceIp + " is not one to one nat");
            return staticNats;
        }

        final Long networkId = sourceIp.getAssociatedWithNetworkId();
        if (networkId == null) {
            throw new CloudRuntimeException("Ip address is not associated with any network");
        }

        final VMInstanceVO vm = _vmInstanceDao.findByIdIncludingRemoved(sourceIp.getAssociatedWithVmId());
        final Network network = _networkModel.getNetwork(networkId);
        if (network == null) {
            final CloudRuntimeException ex = new CloudRuntimeException("Unable to find an ip address to map to specified vm id");
            ex.addProxyObject(vm.getUuid(), "vmId");
            throw ex;
        }

        if (caller != null) {
            _accountMgr.checkAccess(caller, null, true, sourceIp);
        }

        // create new static nat rule
        // Get nic IP4 address
        final Nic guestNic = _networkModel.getNicInNetworkIncludingRemoved(vm.getId(), networkId);
        if (guestNic == null) {
            throw new InvalidParameterValueException("Vm doesn't belong to the network with specified id");
        }

        final String dstIp;

        dstIp = sourceIp.getVmIp();
        if (dstIp == null) {
            throw new InvalidParameterValueException("Vm ip is not set as dnat ip for this public ip");
        }

        String srcMac = null;
        try {
            srcMac = _networkModel.getNextAvailableMacAddressInNetwork(networkId);
        } catch (final InsufficientAddressCapacityException e) {
            throw new CloudRuntimeException("Insufficient MAC address for static NAT instantiation.");
        }

        final StaticNatImpl staticNat = new StaticNatImpl(sourceIp.getAllocatedToAccountId(), sourceIp.getAllocatedInDomainId(), networkId, sourceIp.getId(), dstIp, srcMac,
                forRevoke);
        staticNats.add(staticNat);
        return staticNats;
    }

    @Override
    public void getSystemIpAndEnableStaticNatForVm(final VirtualMachine vm, final boolean getNewIp) throws InsufficientAddressCapacityException {
        boolean success = true;

        // enable static nat if eIp capability is supported
        final List<? extends Nic> nics = _nicDao.listByVmId(vm.getId());
        for (final Nic nic : nics) {
            final Network guestNetwork = _networkModel.getNetwork(nic.getNetworkId());
            final NetworkOffering offering = _entityMgr.findById(NetworkOffering.class, guestNetwork.getNetworkOfferingId());
            if (offering.getElasticIp()) {
                final boolean isSystemVM = (vm.getType() == Type.ConsoleProxy || vm.getType() == Type.SecondaryStorageVm);
                // for user VM's associate public IP only if offering is marked to associate a public IP by default on start of VM
                if (!isSystemVM && !offering.getAssociatePublicIP()) {
                    continue;
                }
                // check if there is already static nat enabled
                if (_ipAddressDao.findByAssociatedVmId(vm.getId()) != null && !getNewIp) {
                    s_logger.debug("Vm " + vm + " already has ip associated with it in guest network " + guestNetwork);
                    continue;
                }

                s_logger.debug("Allocating system ip and enabling static nat for it for the vm " + vm + " in guest network " + guestNetwork);
                final IpAddress ip = _ipAddrMgr.assignSystemIp(guestNetwork.getId(), _accountMgr.getAccount(vm.getAccountId()), false, true);
                if (ip == null) {
                    throw new CloudRuntimeException("Failed to allocate system ip for vm " + vm + " in guest network " + guestNetwork);
                }

                s_logger.debug("Allocated system ip " + ip + ", now enabling static nat on it for vm " + vm);

                try {
                    success = enableStaticNat(ip.getId(), vm.getId(), guestNetwork.getId(), isSystemVM, null);
                } catch (final NetworkRuleConflictException ex) {
                    s_logger.warn("Failed to enable static nat as a part of enabling elasticIp and staticNat for vm " + vm + " in guest network " + guestNetwork +
                            " due to exception ", ex);
                    success = false;
                } catch (final ResourceUnavailableException ex) {
                    s_logger.warn("Failed to enable static nat as a part of enabling elasticIp and staticNat for vm " + vm + " in guest network " + guestNetwork +
                            " due to exception ", ex);
                    success = false;
                }

                if (!success) {
                    s_logger.warn("Failed to enable static nat on system ip " + ip + " for the vm " + vm + ", releasing the ip...");
                    _ipAddrMgr.handleSystemIpRelease(ip);
                    throw new CloudRuntimeException("Failed to enable static nat on system ip for the vm " + vm);
                } else {
                    s_logger.warn("Succesfully enabled static nat on system ip " + ip + " for the vm " + vm);
                }
            }
        }
    }

    @Override
    public List<FirewallRuleVO> listAssociatedRulesForGuestNic(final Nic nic) {
        s_logger.debug("Checking if PF/StaticNat/LoadBalancer rules are configured for nic " + nic.getId());
        final List<FirewallRuleVO> result = new ArrayList<>();
        // add PF rules
        result.addAll(_portForwardingDao.listByNetworkAndDestIpAddr(nic.getIPv4Address(), nic.getNetworkId()));
        if (result.size() > 0) {
            s_logger.debug("Found " + result.size() + " portforwarding rule configured for the nic in the network " + nic.getNetworkId());
        }
        // add static NAT rules
        final List<FirewallRuleVO> staticNatRules = _firewallDao.listStaticNatByVmId(nic.getInstanceId());
        for (final FirewallRuleVO rule : staticNatRules) {
            if (rule.getNetworkId() == nic.getNetworkId()) {
                result.add(rule);
                s_logger.debug("Found rule " + rule.getId() + " " + rule.getPurpose() + " configured");
            }
        }
        final List<? extends IpAddress> staticNatIps = _ipAddressDao.listStaticNatPublicIps(nic.getNetworkId());
        for (final IpAddress ip : staticNatIps) {
            if (ip.getVmIp() != null && ip.getVmIp().equals(nic.getIPv4Address())) {
                final VMInstanceVO vm = _vmInstanceDao.findById(nic.getInstanceId());
                // generate a static Nat rule on the fly because staticNATrule does not persist into db anymore
                // FIX ME
                final FirewallRuleVO staticNatRule =
                        new FirewallRuleVO(null, ip.getId(), 0, 65535, NetUtils.ALL_PROTO.toString(), nic.getNetworkId(), vm.getAccountId(), vm.getDomainId(),
                                Purpose.StaticNat, null, null, null, null, null);
                result.add(staticNatRule);
                s_logger.debug("Found rule " + staticNatRule.getId() + " " + staticNatRule.getPurpose() + " configured");
            }
        }
        // add LB rules
        final List<LoadBalancerVMMapVO> lbMapList = _loadBalancerVMMapDao.listByInstanceId(nic.getInstanceId());
        for (final LoadBalancerVMMapVO lb : lbMapList) {
            final FirewallRuleVO lbRule = _firewallDao.findById(lb.getLoadBalancerId());
            if (lbRule.getNetworkId() == nic.getNetworkId()) {
                result.add(lbRule);
                s_logger.debug("Found rule " + lbRule.getId() + " " + lbRule.getPurpose() + " configured");
            }
        }
        return result;
    }
}
