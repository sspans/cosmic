package com.cloud.network.rules;

import com.cloud.context.CallContext;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.topology.NetworkTopologyVisitor;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.NicIpAlias;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicIpAliasDao;
import com.cloud.vm.dao.NicIpAliasVO;
import com.cloud.vm.dao.UserVmDao;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpSubNetRules extends RuleApplier {

    private static final Logger s_logger = LoggerFactory.getLogger(DhcpSubNetRules.class);

    private final NicProfile _nic;
    private final VirtualMachineProfile _profile;

    private NicIpAliasVO _nicAlias;
    private String _routerAliasIp;

    public DhcpSubNetRules(final Network network, final NicProfile nic, final VirtualMachineProfile profile) {
        super(network);

        _nic = nic;
        _profile = profile;
    }

    @Override
    public boolean accept(final NetworkTopologyVisitor visitor, final VirtualRouter router) throws ResourceUnavailableException {
        _router = router;

        final UserVmDao userVmDao = visitor.getVirtualNetworkApplianceFactory().getUserVmDao();
        final UserVmVO vm = userVmDao.findById(_profile.getId());
        userVmDao.loadDetails(vm);

        final NicDao nicDao = visitor.getVirtualNetworkApplianceFactory().getNicDao();
        // check if this is not the primary subnet.
        final NicVO domrGuestNic = nicDao.findByInstanceIdAndIpAddressAndVmtype(_router.getId(), nicDao.getIpAddress(_nic.getNetworkId(), _router.getId()),
                VirtualMachine.Type.DomainRouter);
        // check if the router ip address and the vm ip address belong to same
        // subnet.
        // if they do not belong to same netwoek check for the alias ips. if not
        // create one.
        // This should happen only in case of Basic and Advanced SG enabled
        // networks.
        if (!NetUtils.sameSubnet(domrGuestNic.getIPv4Address(), _nic.getIPv4Address(), _nic.getIPv4Netmask())) {
            final NicIpAliasDao nicIpAliasDao = visitor.getVirtualNetworkApplianceFactory().getNicIpAliasDao();
            final List<NicIpAliasVO> aliasIps = nicIpAliasDao.listByNetworkIdAndState(domrGuestNic.getNetworkId(), NicIpAlias.State.active);
            boolean ipInVmsubnet = false;
            for (final NicIpAliasVO alias : aliasIps) {
                // check if any of the alias ips belongs to the Vm's subnet.
                if (NetUtils.sameSubnet(alias.getIp4Address(), _nic.getIPv4Address(), _nic.getIPv4Netmask())) {
                    ipInVmsubnet = true;
                    break;
                }
            }

            PublicIp routerPublicIP = null;
            if (ipInVmsubnet == false) {
                // this means we did not create an IP alias on the router.
                _nicAlias = new NicIpAliasVO(domrGuestNic.getId(), _routerAliasIp, _router.getId(), CallContext.current().getCallingAccountId(), _network.getDomainId(),
                        _nic.getNetworkId(), _nic.getIPv4Gateway(), _nic.getIPv4Netmask());
                _nicAlias.setAliasCount(routerPublicIP.getIpMacAddress());
                nicIpAliasDao.persist(_nicAlias);

                final boolean result = visitor.visit(this);

                if (result == false) {
                    final NicIpAliasVO ipAliasVO = nicIpAliasDao.findByInstanceIdAndNetworkId(_network.getId(), _router.getId());
                    final PublicIp routerPublicIPFinal = routerPublicIP;
                    Transaction.execute(new TransactionCallbackNoReturn() {
                        @Override
                        public void doInTransactionWithoutResult(final TransactionStatus status) {
                            nicIpAliasDao.expunge(ipAliasVO.getId());

                            final IPAddressDao ipAddressDao = visitor.getVirtualNetworkApplianceFactory().getIpAddressDao();
                            ipAddressDao.unassignIpAddress(routerPublicIPFinal.getId());
                        }
                    });
                    throw new CloudRuntimeException("failed to configure ip alias on the router as a part of dhcp config");
                }
            }
            return true;
        }
        return true;
    }

    public NicIpAliasVO getNicAlias() {
        return _nicAlias;
    }

    public String getRouterAliasIp() {
        return _routerAliasIp;
    }
}
