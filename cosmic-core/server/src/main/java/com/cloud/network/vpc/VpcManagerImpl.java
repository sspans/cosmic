package com.cloud.network.vpc;

import com.cloud.api.ApiErrorCode;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.user.vpc.ListPrivateGatewaysCmd;
import com.cloud.api.command.user.vpc.ListStaticRoutesCmd;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.db.model.Zone;
import com.cloud.db.repository.ZoneRepository;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.engine.orchestration.service.NetworkOrchestrationService;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.framework.config.ConfigDepot;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.legacymodel.acl.ControlledEntity.ACLType;
import com.cloud.legacymodel.configuration.Resource.ResourceType;
import com.cloud.legacymodel.dc.DataCenter;
import com.cloud.legacymodel.dc.Vlan.VlanType;
import com.cloud.legacymodel.user.Account;
import com.cloud.legacymodel.user.User;
import com.cloud.managed.context.ManagedContextRunnable;
import com.cloud.model.enumeration.AllocationState;
import com.cloud.network.IpAddress;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.Site2SiteVpnGatewayDao;
import com.cloud.network.element.NetworkElement;
import com.cloud.network.element.VpcProvider;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.router.VpcVirtualNetworkApplianceManager;
import com.cloud.network.vpc.VpcOffering.State;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.network.vpc.dao.PrivateIpDao;
import com.cloud.network.vpc.dao.StaticRouteDao;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.network.vpc.dao.VpcGatewayDao;
import com.cloud.network.vpc.dao.VpcOfferingDao;
import com.cloud.network.vpc.dao.VpcOfferingServiceMapDao;
import com.cloud.network.vpc.dao.VpcServiceMapDao;
import com.cloud.network.vpn.Site2SiteVpnManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.offerings.NetworkOfferingServiceMapVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.server.ConfigurationServer;
import com.cloud.server.ResourceTag.ResourceObjectType;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.tags.ResourceTagVO;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.user.AccountManager;
import com.cloud.user.ResourceLimitService;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.StringUtils;
import com.cloud.utils.Ternary;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.ExceptionUtil;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.DomainRouterDao;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.naming.ConfigurationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpcManagerImpl extends ManagerBase implements VpcManager, VpcProvisioningService, VpcService {
    public static final String SERVICE = "service";
    public static final String CAPABILITYTYPE = "capabilitytype";
    public static final String CAPABILITYVALUE = "capabilityvalue";
    public static final String TRUE_VALUE = "true";
    public static final String FALSE_VALUE = "false";
    private static final Logger s_logger = LoggerFactory.getLogger(VpcManagerImpl.class);
    protected final List<HypervisorType> hTypes = new ArrayList<>();
    private final ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1,
            new NamedThreadFactory("VpcChecker"));
    private final List<Service> nonSupportedServices = Arrays.asList(Service.Firewall);
    private final List<Provider> supportedProviders = Arrays.asList(Provider.VPCVirtualRouter, Provider.NiciraNvp);
    @Inject
    EntityManager _entityMgr;
    @Inject
    VpcOfferingDao _vpcOffDao;
    @Inject
    VpcOfferingServiceMapDao _vpcOffSvcMapDao;
    @Inject
    VpcDao _vpcDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    ConfigurationManager _configMgr;
    @Inject
    AccountManager _accountMgr;
    @Inject
    NetworkDao _ntwkDao;
    @Inject
    NetworkOfferingDao _ntwkOffDao;
    @Inject
    NetworkOrchestrationService _ntwkMgr;
    @Inject
    NetworkModel _ntwkModel;
    @Inject
    NetworkService _ntwkSvc;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    DomainRouterDao _routerDao;
    @Inject
    VpcGatewayDao _vpcGatewayDao;
    @Inject
    PrivateIpDao _privateIpDao;
    @Inject
    StaticRouteDao _staticRouteDao;
    @Inject
    NetworkOfferingServiceMapDao _ntwkOffServiceDao;
    @Inject
    VpcOfferingServiceMapDao _vpcOffServiceDao;
    @Inject
    PhysicalNetworkDao _pNtwkDao;
    @Inject
    ResourceTagDao _resourceTagDao;
    @Inject
    FirewallRulesDao _firewallDao;
    @Inject
    Site2SiteVpnGatewayDao _vpnGatewayDao;
    @Inject
    Site2SiteVpnManager _s2sVpnMgr;
    @Inject
    VlanDao _vlanDao = null;
    @Inject
    ResourceLimitService _resourceLimitMgr;
    @Inject
    VpcServiceMapDao _vpcSrvcDao;
    @Inject
    DataCenterDao _dcDao;
    @Inject
    ConfigurationServer _configServer;
    @Inject
    NetworkACLDao _networkAclDao;
    @Inject
    NetworkACLItemDao _networkACLItemDao;
    @Inject
    NetworkACLManager _networkAclMgr;
    @Inject
    IpAddressManager _ipAddrMgr;
    @Inject
    ConfigDepot _configDepot;
    @Inject
    ServiceOfferingDao _serviceOfferingDao;
    @Inject
    VpcVirtualNetworkApplianceManager _routerMgr;
    @Inject
    private VpcPrivateGatewayTransactionCallable vpcTxCallable;
    @Inject
    ZoneRepository zoneRepository;

    int _cleanupInterval;
    int _maxNetworks;
    SearchBuilder<IPAddressVO> IpAddressSearch;
    private List<VpcProvider> vpcElements = null;

    @PostConstruct
    protected void setupSupportedVpcHypervisorsList() {
        hTypes.add(HypervisorType.XenServer);
        hTypes.add(HypervisorType.KVM);
    }

    @Override
    @DB
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        // configure default vpc offering
        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) {

                if (_vpcOffDao.findByUniqueName(VpcOffering.defaultVPCOfferingName) == null) {
                    s_logger.debug("Creating VPC offering " + VpcOffering.defaultVPCOfferingName);

                    final Map<Service, Set<Provider>> svcProviderMap = getServiceSetMap(DEFAULT_SERVICES);
                    createVpcOffering(VpcOffering.defaultVPCOfferingName, VpcOffering.defaultVPCOfferingName, svcProviderMap,
                            true, State.Enabled, null, null, false);
                }

                if (_vpcOffDao.findByUniqueName(VpcOffering.defaultRemoteGatewayVPCOfferingName) == null) {
                    s_logger.debug("Creating VPC offering " + VpcOffering.defaultRemoteGatewayVPCOfferingName);

                    final Map<Service, Set<Provider>> svcProviderMap = getServiceSetMap(REMOTE_GATEWAY_SERVICES);
                    createVpcOffering(VpcOffering.defaultRemoteGatewayVPCOfferingName, VpcOffering.defaultRemoteGatewayVPCOfferingName, svcProviderMap,
                            true, State.Enabled, null, null, false);
                }

                if (_vpcOffDao.findByUniqueName(VpcOffering.defaultRemoteGatewayWithVPNVPCOfferingName) == null) {
                    s_logger.debug("Creating VPC offering " + VpcOffering.defaultRemoteGatewayWithVPNVPCOfferingName);

                    final Map<Service, Set<Provider>> svcProviderMap = getServiceSetMap(REMOTE_GATEWAY_WITH_VPN_SERVICES);
                    createVpcOffering(VpcOffering.defaultRemoteGatewayWithVPNVPCOfferingName, VpcOffering.defaultRemoteGatewayWithVPNVPCOfferingName, svcProviderMap,
                            true, State.Enabled, null, null, false);
                }

                if (_vpcOffDao.findByUniqueName(VpcOffering.defaultInternalVPCOfferingName) == null) {
                    s_logger.debug("Creating VPC offering " + VpcOffering.defaultInternalVPCOfferingName);

                    final Map<Service, Set<Provider>> svcProviderMap = getServiceSetMap(INTERNAL_VPC_SERVICES);
                    createVpcOffering(VpcOffering.defaultInternalVPCOfferingName, VpcOffering.defaultInternalVPCOfferingName, svcProviderMap,
                            true, State.Enabled, null, null, false);
                }

                if (_vpcOffDao.findByUniqueName(VpcOffering.redundantVPCOfferingName) == null) {
                    s_logger.debug("Creating VPC offering " + VpcOffering.redundantVPCOfferingName);

                    // Link the default Redundant VPC offering to the two default router offerings
                    final ServiceOffering serviceOffering = _serviceOfferingDao.findByName(ServiceOffering.routerDefaultOffUniqueName);
                    final ServiceOffering secondaryServiceOffering = _serviceOfferingDao.findByName(ServiceOffering.routerDefaultSecondaryOffUniqueName);
                    Long serviceOfferingId = null;
                    Long secondaryServiceOfferingId = null;
                    if (serviceOffering != null) {
                        serviceOfferingId = serviceOffering.getId();
                    }
                    if (secondaryServiceOffering != null) {
                        secondaryServiceOfferingId = secondaryServiceOffering.getId();
                    }

                    final Map<Service, Set<Provider>> svcProviderMap = getServiceSetMap(DEFAULT_SERVICES);
                    createVpcOffering(VpcOffering.redundantVPCOfferingName, VpcOffering.redundantVPCOfferingName, svcProviderMap,
                            true, State.Enabled, serviceOfferingId, secondaryServiceOfferingId, true);
                }
            }
        });

        final Map<String, String> configs = _configDao.getConfiguration(params);
        final String value = configs.get(Config.VpcCleanupInterval.key());
        _cleanupInterval = NumbersUtil.parseInt(value, 60 * 60); // 1 hour

        final String maxNtwks = configs.get(Config.VpcMaxNetworks.key());
        _maxNetworks = NumbersUtil.parseInt(maxNtwks, 3); // max=3 is default

        IpAddressSearch = _ipAddressDao.createSearchBuilder();
        IpAddressSearch.and("accountId", IpAddressSearch.entity().getAllocatedToAccountId(), Op.EQ);
        IpAddressSearch.and("dataCenterId", IpAddressSearch.entity().getDataCenterId(), Op.EQ);
        IpAddressSearch.and("vpcId", IpAddressSearch.entity().getVpcId(), Op.EQ);
        IpAddressSearch.and("associatedWithNetworkId", IpAddressSearch.entity().getAssociatedWithNetworkId(), Op.EQ);
        final SearchBuilder<VlanVO> virtualNetworkVlanSB = _vlanDao.createSearchBuilder();
        virtualNetworkVlanSB.and("vlanType", virtualNetworkVlanSB.entity().getVlanType(), Op.EQ);
        IpAddressSearch.join("virtualNetworkVlanSB", virtualNetworkVlanSB, IpAddressSearch.entity().getVlanId(),
                virtualNetworkVlanSB.entity().getId(), JoinBuilder.JoinType.INNER);
        IpAddressSearch.done();

        return true;
    }

    private Map<Service, Set<Provider>> getServiceSetMap(final List<Service> serviceToAdd) {
        final Map<Service, Set<Provider>> svcProviderMap = new HashMap<>();
        final Set<Provider> defaultProviders = new HashSet<>();
        defaultProviders.add(Provider.VPCVirtualRouter);
        for (final Service svc : serviceToAdd) {
            if (svc == Service.Lb) {
                final Set<Provider> lbProviders = new HashSet<>();
                lbProviders.add(Provider.VPCVirtualRouter);
                svcProviderMap.put(svc, lbProviders);
            } else {
                svcProviderMap.put(svc, defaultProviders);
            }
        }
        return svcProviderMap;
    }

    @Override
    public boolean start() {
        _executor.scheduleAtFixedRate(new VpcCleanupTask(), _cleanupInterval, _cleanupInterval, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    private final List<Service> DEFAULT_SERVICES = new ArrayList<Service>() {{
        add(Network.Service.Dhcp);
        add(Network.Service.Dns);
        add(Network.Service.UserData);
        add(Network.Service.NetworkACL);
        add(Network.Service.PortForwarding);
        add(Network.Service.Lb);
        add(Network.Service.SourceNat);
        add(Network.Service.StaticNat);
        add(Network.Service.Gateway);
        add(Network.Service.Vpn);
    }};

    private final List<Service> REMOTE_GATEWAY_SERVICES = new ArrayList<Service>() {{
        add(Network.Service.Dhcp);
        add(Network.Service.Dns);
        add(Network.Service.UserData);
        add(Network.Service.NetworkACL);
    }};

    private final List<Service> REMOTE_GATEWAY_WITH_VPN_SERVICES = new ArrayList<Service>() {{
        add(Network.Service.Dhcp);
        add(Network.Service.Dns);
        add(Network.Service.UserData);
        add(Network.Service.NetworkACL);
        add(Network.Service.Vpn);
    }};

    private final List<Service> INTERNAL_VPC_SERVICES = new ArrayList<Service>() {{
        add(Network.Service.Dhcp);
        add(Network.Service.Dns);
        add(Network.Service.UserData);
        add(Network.Service.NetworkACL);
        add(Network.Service.Gateway);
    }};

    @DB
    private VpcOffering createVpcOffering(final String name, final String displayText,
                                          final Map<Network.Service, Set<Network.Provider>> svcProviderMap,
                                          final boolean isDefault, final State state, final Long serviceOfferingId, final Long secondaryServiceOfferingId,
                                          final boolean redundantRouter) {

        return Transaction.execute(new TransactionCallback<VpcOffering>() {
            @Override
            public VpcOffering doInTransaction(final TransactionStatus status) {
                // create vpc offering object
                VpcOfferingVO offering = new VpcOfferingVO(name, displayText, isDefault, serviceOfferingId, secondaryServiceOfferingId,
                        redundantRouter);

                if (state != null) {
                    offering.setState(state);
                }
                s_logger.debug("Adding vpc offering " + offering);
                offering = _vpcOffDao.persist(offering);
                // populate services and providers
                if (svcProviderMap != null) {
                    for (final Network.Service service : svcProviderMap.keySet()) {
                        final Set<Provider> providers = svcProviderMap.get(service);
                        if (providers != null && !providers.isEmpty()) {
                            for (final Network.Provider provider : providers) {
                                final VpcOfferingServiceMapVO offService = new VpcOfferingServiceMapVO(offering.getId(), service,
                                        provider);
                                _vpcOffSvcMapDao.persist(offService);
                                s_logger.trace(
                                        "Added service for the vpc offering: " + offService + " with provider " + provider.getName());
                            }
                        } else {
                            throw new InvalidParameterValueException(
                                    "Provider is missing for the VPC offering service " + service.getName());
                        }
                    }
                }

                return offering;
            }
        });
    }

    @Override
    public List<? extends Network> getVpcNetworks(final long vpcId) {
        return _ntwkDao.listByVpc(vpcId);
    }

    @Override
    public VpcOffering getVpcOffering(final long vpcOffId) {
        return _vpcOffDao.findById(vpcOffId);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_OFFERING_CREATE, eventDescription = "creating vpc offering",
            create = true)
    public VpcOffering createVpcOffering(final String name, final String displayText,
                                         final List<String> supportedServices, final Map<String, List<String>> serviceProviders,
                                         final Map serviceCapabilitystList, final Long serviceOfferingId, final Long secondaryServiceOfferingId) {

        final Map<Network.Service, Set<Network.Provider>> svcProviderMap = new HashMap<>();
        final Set<Network.Provider> defaultProviders = new HashSet<>();
        defaultProviders.add(Provider.VPCVirtualRouter);
        // Just here for 4.1, replaced by commit 836ce6c1 in newer versions
        final Set<Network.Provider> sdnProviders = new HashSet<>();
        sdnProviders.add(Provider.NiciraNvp);

        boolean firewallSvs = false;
        // populate the services first
        for (final String serviceName : supportedServices) {
            // validate if the service is supported
            final Service service = Network.Service.getService(serviceName);
            if (service == null || nonSupportedServices.contains(service)) {
                throw new InvalidParameterValueException("Service " + serviceName + " is not supported in VPC");
            }

            if (service == Service.Connectivity) {
                s_logger.debug("Applying Connectivity workaround, setting provider to NiciraNvp");
                svcProviderMap.put(service, sdnProviders);
            } else {
                svcProviderMap.put(service, defaultProviders);
            }
            if (service == Service.NetworkACL) {
                firewallSvs = true;
            }
        }

        if (!firewallSvs) {
            s_logger.debug("Automatically adding network ACL service to the list of VPC services");
            svcProviderMap.put(Service.NetworkACL, defaultProviders);
        }

        // we should make the gateway service addition explicit
        // will probably break the tests!
        //svcProviderMap.put(Service.Gateway, defaultProviders);

        if (serviceProviders != null) {
            for (final Entry<String, List<String>> serviceEntry : serviceProviders.entrySet()) {
                final Network.Service service = Network.Service.getService(serviceEntry.getKey());
                if (svcProviderMap.containsKey(service)) {
                    final Set<Provider> providers = new HashSet<>();
                    for (final String prvNameStr : serviceEntry.getValue()) {
                        // check if provider is supported
                        final Network.Provider provider = Network.Provider.getProvider(prvNameStr);
                        if (provider == null) {
                            throw new InvalidParameterValueException("Invalid service provider: " + prvNameStr);
                        }

                        providers.add(provider);
                    }
                    svcProviderMap.put(service, providers);
                } else {
                    throw new InvalidParameterValueException("Service " + serviceEntry.getKey()
                            + " is not enabled for the network " + "offering, can't add a provider to it");
                }
            }
        }

        validateConnectivtyServiceCapabilities(svcProviderMap.get(Service.Connectivity), serviceCapabilitystList);

        final boolean redundantRouter = isVpcOfferingRedundantRouter(serviceCapabilitystList);
        final VpcOffering offering = createVpcOffering(name, displayText, svcProviderMap, false, null, serviceOfferingId, secondaryServiceOfferingId,
                redundantRouter);
        CallContext.current().setEventDetails(" Id: " + offering.getId() + " Name: " + name);

        return offering;
    }

    private void validateConnectivtyServiceCapabilities(final Set<Provider> providers,
                                                        final Map serviceCapabilitystList) {
        if (serviceCapabilitystList != null && !serviceCapabilitystList.isEmpty()) {
            final Collection serviceCapabilityCollection = serviceCapabilitystList.values();
            final Iterator iter = serviceCapabilityCollection.iterator();

            while (iter.hasNext()) {
                final HashMap<String, String> svcCapabilityMap = (HashMap<String, String>) iter.next();
                Capability capability = null;
                final String svc = svcCapabilityMap.get(SERVICE);
                final String capabilityName = svcCapabilityMap.get(CAPABILITYTYPE);
                final String capabilityValue = svcCapabilityMap.get(CAPABILITYVALUE);
                if (capabilityName != null) {
                    capability = Capability.getCapability(capabilityName);
                }

                if (capability == null || capabilityValue == null) {
                    throw new InvalidParameterValueException(
                            "Invalid capability:" + capabilityName + " capability value:" + capabilityValue);
                }
                final Service usedService = Service.getService(svc);

                checkCapabilityPerServiceProvider(providers, capability, usedService);

                if (!capabilityValue.equalsIgnoreCase(TRUE_VALUE) && !capabilityValue.equalsIgnoreCase(FALSE_VALUE)) {
                    throw new InvalidParameterValueException("Invalid Capability value:" + capabilityValue + " specified.");
                }
            }
        }
    }

    private boolean isVpcOfferingRedundantRouter(final Map serviceCapabilitystList) {
        return findCapabilityForService(serviceCapabilitystList, Capability.RedundantRouter, Service.SourceNat);
    }

    protected void checkCapabilityPerServiceProvider(final Set<Provider> providers, final Capability capability,
                                                     final Service service) {
        // TODO Shouldn't it fail it there are no providers?
        if (providers != null) {
            for (final Provider provider : providers) {
                final NetworkElement element = _ntwkModel.getElementImplementingProvider(provider.getName());
                final Map<Service, Map<Capability, String>> capabilities = element.getCapabilities();
                if (capabilities != null && !capabilities.isEmpty()) {
                    final Map<Capability, String> connectivityCapabilities = capabilities.get(service);
                    if (connectivityCapabilities == null
                            || connectivityCapabilities != null && !connectivityCapabilities.keySet().contains(capability)) {
                        throw new InvalidParameterValueException(String.format("Provider %s does not support %s  capability.",
                                provider.getName(), capability.getName()));
                    }
                }
            }
        }
    }

    private boolean findCapabilityForService(final Map serviceCapabilitystList, final Capability capability,
                                             final Service service) {
        boolean foundCapability = false;
        if (serviceCapabilitystList != null && !serviceCapabilitystList.isEmpty()) {
            final Iterator iter = serviceCapabilitystList.values().iterator();
            while (iter.hasNext()) {
                final HashMap<String, String> currentCapabilityMap = (HashMap<String, String>) iter.next();
                final String currentCapabilityService = currentCapabilityMap.get(SERVICE);
                final String currentCapabilityName = currentCapabilityMap.get(CAPABILITYTYPE);
                final String currentCapabilityValue = currentCapabilityMap.get(CAPABILITYVALUE);

                if (currentCapabilityName == null || currentCapabilityService == null || currentCapabilityValue == null) {
                    throw new InvalidParameterValueException(
                            String.format("Invalid capability with name %s, value %s and service %s", currentCapabilityName,
                                    currentCapabilityValue, currentCapabilityService));
                }

                if (currentCapabilityName.equalsIgnoreCase(capability.getName())) {
                    foundCapability = currentCapabilityValue.equalsIgnoreCase(TRUE_VALUE);

                    if (!currentCapabilityService.equalsIgnoreCase(service.getName())) {
                        throw new InvalidParameterValueException(
                                String.format("Invalid Service: %s specified. Capability %s can be specified only for service %s",
                                        currentCapabilityService, service.getName(), currentCapabilityName));
                    }

                    break;
                }
            }
        }
        return foundCapability;
    }

    @Override
    public Pair<List<? extends VpcOffering>, Integer> listVpcOfferings(final Long id, final String name,
                                                                       final String displayText, final List<String> supportedServicesStr,
                                                                       final Boolean isDefault, final String keyword, final String state, final Long startIndex,
                                                                       final Long pageSizeVal) {
        final Filter searchFilter = new Filter(VpcOfferingVO.class, "created", false, null, null);
        final SearchCriteria<VpcOfferingVO> sc = _vpcOffDao.createSearchCriteria();

        if (keyword != null) {
            final SearchCriteria<VpcOfferingVO> ssc = _vpcOffDao.createSearchCriteria();
            ssc.addOr("displayText", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            ssc.addOr("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");

            sc.addAnd("name", SearchCriteria.Op.SC, ssc);
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }

        if (displayText != null) {
            sc.addAnd("displayText", SearchCriteria.Op.LIKE, "%" + displayText + "%");
        }

        if (isDefault != null) {
            sc.addAnd("isDefault", SearchCriteria.Op.EQ, isDefault);
        }

        if (state != null) {
            sc.addAnd("state", SearchCriteria.Op.EQ, state);
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        final List<VpcOfferingVO> offerings = _vpcOffDao.search(sc, searchFilter);

        // filter by supported services
        final boolean listBySupportedServices = supportedServicesStr != null && !supportedServicesStr.isEmpty()
                && !offerings.isEmpty();

        if (listBySupportedServices) {
            final List<VpcOfferingVO> supportedOfferings = new ArrayList<>();
            Service[] supportedServices = null;

            if (listBySupportedServices) {
                supportedServices = getServices(supportedServicesStr);
            }

            for (final VpcOfferingVO offering : offerings) {
                if (areServicesSupportedByVpcOffering(offering.getId(), supportedServices)) {
                    supportedOfferings.add(offering);
                }
            }

            final List<? extends VpcOffering> wPagination = StringUtils.applyPagination(supportedOfferings, startIndex,
                    pageSizeVal);
            if (wPagination != null) {
                final Pair<List<? extends VpcOffering>, Integer> listWPagination = new Pair<>(
                        wPagination, supportedOfferings.size());
                return listWPagination;
            }
            return new Pair<>(supportedOfferings, supportedOfferings.size());
        } else {
            final List<? extends VpcOffering> wPagination = StringUtils.applyPagination(offerings, startIndex, pageSizeVal);
            if (wPagination != null) {
                final Pair<List<? extends VpcOffering>, Integer> listWPagination = new Pair<>(
                        wPagination, offerings.size());
                return listWPagination;
            }
            return new Pair<>(offerings, offerings.size());
        }
    }

    protected boolean areServicesSupportedByVpcOffering(final long vpcOffId, final Service... services) {
        return _vpcOffSvcMapDao.areServicesSupportedByNetworkOffering(vpcOffId, services);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_OFFERING_DELETE, eventDescription = "deleting vpc offering")
    public boolean deleteVpcOffering(final long offId) {
        CallContext.current().setEventDetails(" Id: " + offId);

        // Verify vpc offering id
        final VpcOfferingVO offering = _vpcOffDao.findById(offId);
        if (offering == null) {
            throw new InvalidParameterValueException("unable to find vpc offering " + offId);
        }

        // Don't allow to delete default vpc offerings
        if (offering.isDefault() == true) {
            throw new InvalidParameterValueException("Default network offering can't be deleted");
        }

        // don't allow to delete vpc offering if it's in use by existing vpcs
        // (the offering can be disabled though)
        final int vpcCount = _vpcDao.getVpcCountByOfferingId(offId);
        if (vpcCount > 0) {
            throw new InvalidParameterValueException(
                    "Can't delete vpc offering " + offId + " as its used by " + vpcCount + " vpcs. "
                            + "To make the network offering unavaiable, disable it");
        }

        if (_vpcOffDao.remove(offId)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Vpc getActiveVpc(final long vpcId) {
        return _vpcDao.getActiveVpcById(vpcId);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_OFFERING_UPDATE, eventDescription = "updating vpc offering")
    public VpcOffering updateVpcOffering(final long vpcOffId, final String vpcOfferingName, final String displayText,
                                         final String state) {
        CallContext.current().setEventDetails(" Id: " + vpcOffId);

        // Verify input parameters
        final VpcOfferingVO offeringToUpdate = _vpcOffDao.findById(vpcOffId);
        if (offeringToUpdate == null) {
            throw new InvalidParameterValueException("Unable to find vpc offering " + vpcOffId);
        }

        final VpcOfferingVO offering = _vpcOffDao.createForUpdate(vpcOffId);

        if (vpcOfferingName != null) {
            offering.setName(vpcOfferingName);
        }

        if (displayText != null) {
            offering.setDisplayText(displayText);
        }

        if (state != null) {
            boolean validState = false;
            for (final VpcOffering.State st : VpcOffering.State.values()) {
                if (st.name().equalsIgnoreCase(state)) {
                    validState = true;
                    offering.setState(st);
                }
            }
            if (!validState) {
                throw new InvalidParameterValueException("Incorrect state value: " + state);
            }
        }

        if (_vpcOffDao.update(vpcOffId, offering)) {
            s_logger.debug("Updated VPC offeirng id=" + vpcOffId);
            return _vpcOffDao.findById(vpcOffId);
        } else {
            return null;
        }
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_CREATE, eventDescription = "creating vpc", create = true)
    public Vpc createVpc(final long zoneId, final long vpcOffId, final long vpcOwnerId, final String vpcName,
                         final String displayText, final String cidr, String networkDomain,
                         final Boolean displayVpc, final String sourceNatList, final String syslogServerList) throws ResourceAllocationException {
        final Account caller = CallContext.current().getCallingAccount();
        final Account owner = _accountMgr.getAccount(vpcOwnerId);

        // Verify that caller can perform actions in behalf of vpc owner
        _accountMgr.checkAccess(caller, null, false, owner);

        // check resource limit
        _resourceLimitMgr.checkResourceLimit(owner, ResourceType.vpc);

        // Validate vpc offering
        final VpcOfferingVO vpcOff = _vpcOffDao.findById(vpcOffId);
        if (vpcOff == null || vpcOff.getState() != State.Enabled) {
            final InvalidParameterValueException ex = new InvalidParameterValueException(
                    "Unable to find vpc offering in " + State.Enabled + " state by specified id");
            if (vpcOff == null) {
                ex.addProxyObject(String.valueOf(vpcOffId), "vpcOfferingId");
            } else {
                ex.addProxyObject(vpcOff.getUuid(), "vpcOfferingId");
            }
            throw ex;
        }

        // Validate zone
        final DataCenter zone = _entityMgr.findById(DataCenter.class, zoneId);
        if (zone == null) {
            throw new InvalidParameterValueException("Can't find zone by id specified");
        }

        if (AllocationState.Disabled == zone.getAllocationState() && !_accountMgr.isRootAdmin(caller.getId())) {
            // See DataCenterVO.java
            final PermissionDeniedException ex = new PermissionDeniedException(
                    "Cannot perform this operation since specified Zone is currently disabled");
            ex.addProxyObject(zone.getUuid(), "zoneId");
            throw ex;
        }

        if (networkDomain == null) {
            // 1) Get networkDomain from the corresponding account
            networkDomain = _ntwkModel.getAccountNetworkDomain(owner.getId(), zoneId);

            // 2) If null, generate networkDomain using domain suffix from the
            // global config variables
            if (networkDomain == null) {
                networkDomain = "cs" + Long.toHexString(owner.getId())
                        + NetworkOrchestrationService.GuestDomainSuffix.valueIn(zoneId);
            }
        }

        final VpcVO vpc = new VpcVO(zoneId, vpcName, displayText, owner.getId(), owner.getDomainId(), vpcOffId, cidr,
                networkDomain, vpcOff.getRedundantRouter(), sourceNatList, syslogServerList);

        return createVpc(displayVpc, vpc);
    }

    @Override
    public Map<Service, Set<Provider>> getVpcOffSvcProvidersMap(final long vpcOffId) {
        final Map<Service, Set<Provider>> serviceProviderMap = new HashMap<>();
        final List<VpcOfferingServiceMapVO> map = _vpcOffSvcMapDao.listByVpcOffId(vpcOffId);

        for (final VpcOfferingServiceMapVO instance : map) {
            final Service service = Service.getService(instance.getService());
            Set<Provider> providers;
            providers = serviceProviderMap.get(service);
            if (providers == null) {
                providers = new HashSet<>();
            }
            providers.add(Provider.getProvider(instance.getProvider()));
            serviceProviderMap.put(service, providers);
        }

        return serviceProviderMap;
    }

    @DB
    protected Vpc createVpc(final Boolean displayVpc, final VpcVO vpc) {
        final String cidr = vpc.getCidr();
        // Validate CIDR
        if (!NetUtils.isValidIp4Cidr(cidr)) {
            throw new InvalidParameterValueException("Invalid CIDR specified " + cidr);
        }

        // cidr has to be RFC 1918 complient
        if (!NetUtils.validateGuestCidr(cidr)) {
            throw new InvalidParameterValueException("Guest Cidr " + cidr + " is not RFC1918 compliant");
        }

        // validate network domain
        if (!NetUtils.verifyDomainName(vpc.getNetworkDomain())) {
            throw new InvalidParameterValueException(
                    "Invalid network domain. Total length shouldn't exceed 190 chars. Each domain "
                            + "label must be between 1 and 63 characters long, can contain ASCII letters 'a' through 'z', "
                            + "the digits '0' through '9', "
                            + "and the hyphen ('-'); can't start or end with \"-\"");
        }

        return Transaction.execute(new TransactionCallback<VpcVO>() {
            @Override
            public VpcVO doInTransaction(final TransactionStatus status) {
                if (displayVpc != null) {
                    vpc.setDisplay(displayVpc);
                }

                final VpcVO persistedVpc = _vpcDao.persist(vpc,
                        finalizeServicesAndProvidersForVpc(vpc.getZoneId(), vpc.getVpcOfferingId()));
                _resourceLimitMgr.incrementResourceCount(vpc.getAccountId(), ResourceType.vpc);
                s_logger.debug("Created VPC " + persistedVpc);

                return persistedVpc;
            }
        });
    }

    private Map<String, List<String>> finalizeServicesAndProvidersForVpc(final long zoneId, final long offeringId) {
        final Map<String, List<String>> svcProviders = new HashMap<>();
        final List<VpcOfferingServiceMapVO> servicesMap = _vpcOffSvcMapDao.listByVpcOffId(offeringId);

        for (final VpcOfferingServiceMapVO serviceMap : servicesMap) {
            final String service = serviceMap.getService();
            String provider = serviceMap.getProvider();

            if (provider == null) {
                // Default to VPCVirtualRouter
                provider = Provider.VPCVirtualRouter.getName();
            }

            if (!_ntwkModel.isProviderEnabledInZone(zoneId, provider)) {
                throw new InvalidParameterValueException(
                        "Provider " + provider + " should be enabled in at least one physical network of the zone specified");
            }

            final List<String> providers;
            if (svcProviders.get(service) == null) {
                providers = new ArrayList<>();
            } else {
                providers = svcProviders.get(service);
            }
            providers.add(provider);
            svcProviders.put(service, providers);
        }

        return svcProviders;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_DELETE, eventDescription = "deleting VPC")
    public boolean deleteVpc(final long vpcId) throws ConcurrentOperationException, ResourceUnavailableException {
        CallContext.current().setEventDetails(" Id: " + vpcId);
        final CallContext ctx = CallContext.current();

        // Verify vpc id
        final Vpc vpc = _vpcDao.findById(vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("unable to find VPC id=" + vpcId);
        }

        // verify permissions
        _accountMgr.checkAccess(ctx.getCallingAccount(), null, false, vpc);

        return destroyVpc(vpc, ctx.getCallingAccount(), ctx.getCallingUserId());
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_UPDATE, eventDescription = "updating vpc")
    public Vpc updateVpc(final long vpcId, final String vpcName, final String displayText, final String customId, final Boolean displayVpc, final Long vpcOfferingId, final
    String sourceNatList, final String syslogServerList) {
        CallContext.current().setEventDetails(" Id: " + vpcId);
        final Account caller = CallContext.current().getCallingAccount();

        // Verify input parameters
        final VpcVO vpcToUpdate = _vpcDao.findById(vpcId);
        if (vpcToUpdate == null) {
            throw new InvalidParameterValueException("Unable to find vpc by id " + vpcId);
        }

        _accountMgr.checkAccess(caller, null, false, vpcToUpdate);

        final VpcVO vpc = _vpcDao.createForUpdate(vpcId);
        boolean restartWithCleanupRequired = false;

        if (vpcName != null) {
            vpc.setName(vpcName);
        }

        if (displayText != null) {
            vpc.setDisplayText(displayText);
        }

        if (customId != null) {
            vpc.setUuid(customId);
        }

        if (displayVpc != null) {
            vpc.setDisplay(displayVpc);
        }

        if (syslogServerList != null) {
            vpc.setSyslogServerList(syslogServerList);
        }

        if (vpcOfferingId != null) {
            final VpcOfferingVO newVpcOffering = _vpcOffDao.findById(vpcOfferingId);
            if (newVpcOffering == null) {
                throw new InvalidParameterValueException("Unable to find vpc offering by id " + vpcOfferingId);
            }

            if (vpcOfferingId == vpcToUpdate.getVpcOfferingId()) {
                throw new InvalidParameterValueException("The vpc already has the specified offering, so not upgrading. Use restart+cleanup to rebuild.");
            }

            // check if the new VPC offering matches the network offerings in use
            checkVpcOfferingServicesWithCurrentNetworkOfferings(vpcOfferingId, vpcToUpdate);

            vpc.setVpcOfferingId(vpcOfferingId);
            vpc.setRedundant(newVpcOffering.getRedundantRouter());
            restartWithCleanupRequired = true;

            // disassociate the public IPs if not required anymore
            if (!hasSourceNatService(vpc)) {
                boolean success = true;
                final List<IPAddressVO> ipsToRelease = _ipAddressDao.listByVpc(vpcId, null);
                s_logger.debug("Releasing ips for vpc id=" + vpcId + " as a part of vpc cleanup");
                for (final IPAddressVO ipToRelease : ipsToRelease) {
                    success = success && _ipAddrMgr.disassociatePublicIpAddress(ipToRelease.getId(), CallContext.current().getCallingUserId(), caller);
                    if (!success) {
                        s_logger.warn("Failed to cleanup ip " + ipToRelease + " as a part of vpc id=" + vpcId + " cleanup");
                    }
                }
            }
        }
        vpc.setRestartRequired(restartWithCleanupRequired);

        if (sourceNatList != null && !sourceNatList.isEmpty() && vpcOfferingId != null && !hasSourceNatService(vpc)) {
            throw new InvalidParameterValueException("Source NAT is not enabled on the VPC, so source NAT list is not allowed!");
        }

        if (sourceNatList != null) {
            vpc.setSourceNatList(sourceNatList);
        }

        if (vpcOfferingId != null && !hasSourceNatService(vpc) && hasSourceNatService(vpcToUpdate)) {
            s_logger.warn("SourceNat service not available on VPC " + vpc.getName() + " so setting SourceNatList to null!");
            vpc.setSourceNatList(null);
        }

        // Save the new config
        if (_vpcDao.update(vpcId, vpc)) {
            s_logger.debug("Updated VPC id=" + vpcId);
        } else {
            return null;
        }

        final Account callerAccount = CallContext.current().getCallingAccount();
        final User callerUser = _accountMgr.getActiveUser(CallContext.current().getCallingUserId());
        final ReservationContext context = new ReservationContextImpl(null, null, callerUser, callerAccount);

        if (!vpc.isRedundant()) {
            final List<DomainRouterVO> routers = _routerDao.listByVpcId(vpc.getId());
            for (final DomainRouterVO router : routers) {
                // Delete any non-MASTER router since we are supposed to run a single setup according to the new VPC offering
                if (router.getRedundantState() != VirtualRouter.RedundantState.MASTER) {
                    try {
                        s_logger.warn("Deleting router " + router.getInstanceName() + " as we don't need it any more");
                        _routerMgr.destroyRouter(router.getId(), context.getAccount(), context.getCaller().getId());
                    } catch (final ResourceUnavailableException ex) {
                        s_logger.warn("Exception: ", ex);
                        throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
                    }
                }
            }
            return _vpcDao.findById(vpcId);
        }

        // Restart the VPC when required
        if (restartWithCleanupRequired) {
            s_logger.debug("Will now restart+cleanup VPC id=" + vpcId);
            try {
                final boolean result = restartVpc(vpcId, true);
                if (!result) {
                    throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to restart VPC");
                }
            } catch (final ResourceUnavailableException ex) {
                s_logger.warn("Exception: ", ex);
                throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
            } catch (final ConcurrentOperationException ex) {
                s_logger.warn("Exception: ", ex);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
            } catch (final InsufficientCapacityException ex) {
                s_logger.info(ex.toString());
                throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
            }
        } else {
            //LOOP over all routers
            final List<DomainRouterVO> routers = _routerDao.listByVpcId(vpc.getId());
            if (routers != null && !routers.isEmpty()) {
                s_logger.debug("Updating routers of VPC " + vpc + " as a part of VPC update process");
                for (final DomainRouterVO router : routers) {
                    // Validate that the router is running
                    if (router.getState() == VirtualMachine.State.Running) {
                        if (!_routerMgr.updateVR(vpc, router)) {
                            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update VPC config");
                        }
                    }
                }
            }
        }
        return _vpcDao.findById(vpcId);
    }

    private void checkVpcOfferingServicesWithCurrentNetworkOfferings(final Long vpcOfferingId, final VpcVO currentVpc) {
        // List of VPC networks
        final List<NetworkVO> networks = _ntwkDao.listByVpc(currentVpc.getId());

        // Services that the new offering supports
        final List<String> newOfferingSupportedServicesStr = _vpcOffSvcMapDao.listServicesForVpcOffering(vpcOfferingId);

        final List<String> notSupportedServices = new LinkedList<>();

        for (final NetworkVO network : networks) {
            final List<String> networkOfferingSupportedServicesStr = _ntwkOffServiceDao.listServicesForNetworkOffering(network.getNetworkOfferingId());

            for (final String serviceName : networkOfferingSupportedServicesStr) {
                if (!newOfferingSupportedServicesStr.contains(serviceName) && !notSupportedServices.contains(serviceName)) {
                    notSupportedServices.add(serviceName);
                }
            }
        }

        if (!notSupportedServices.isEmpty()) {
            throw new InvalidParameterValueException("The new vpc offering does not support these service(s) that this vpc requires for proper operation: " +
                    notSupportedServices + " based on the network offerings used. Please select an offering with compatible services.");
        }
    }

    @Override
    public Pair<List<? extends Vpc>, Integer> listVpcs(final Long id, final String vpcName, final String displayText,
                                                       final List<String> supportedServicesStr, final String cidr,
                                                       final Long vpcOffId, final String state, final String accountName, Long domainId, final String keyword,
                                                       final Long startIndex, final Long pageSizeVal,
                                                       final Long zoneId, Boolean isRecursive, final Boolean listAll, final Boolean restartRequired,
                                                       final Map<String, String> tags, final Long projectId,
                                                       final Boolean display) {
        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();
        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(
                domainId, isRecursive,
                null);
        _accountMgr.buildACLSearchParameters(caller, id, accountName, projectId, permittedAccounts,
                domainIdRecursiveListProject, listAll, false);
        domainId = domainIdRecursiveListProject.first();
        isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
        final Filter searchFilter = new Filter(VpcVO.class, "created", false, null, null);

        final SearchBuilder<VpcVO> sb = _vpcDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("displayText", sb.entity().getDisplayText(), SearchCriteria.Op.LIKE);
        sb.and("vpcOfferingId", sb.entity().getVpcOfferingId(), SearchCriteria.Op.EQ);
        sb.and("zoneId", sb.entity().getZoneId(), SearchCriteria.Op.EQ);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);
        sb.and("restartRequired", sb.entity().isRestartRequired(), SearchCriteria.Op.EQ);
        sb.and("cidr", sb.entity().getCidr(), SearchCriteria.Op.EQ);
        sb.and("display", sb.entity().isDisplay(), SearchCriteria.Op.EQ);

        if (tags != null && !tags.isEmpty()) {
            final SearchBuilder<ResourceTagVO> tagSearch = _resourceTagDao.createSearchBuilder();
            for (int count = 0; count < tags.size(); count++) {
                tagSearch.or().op("key" + String.valueOf(count), tagSearch.entity().getKey(), SearchCriteria.Op.EQ);
                tagSearch.and("value" + String.valueOf(count), tagSearch.entity().getValue(), SearchCriteria.Op.EQ);
                tagSearch.cp();
            }
            tagSearch.and("resourceType", tagSearch.entity().getResourceType(), SearchCriteria.Op.EQ);
            sb.groupBy(sb.entity().getId());
            sb.join("tagSearch", tagSearch, sb.entity().getId(), tagSearch.entity().getResourceId(),
                    JoinBuilder.JoinType.INNER);
        }

        // now set the SC criteria...
        final SearchCriteria<VpcVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        if (keyword != null) {
            final SearchCriteria<VpcVO> ssc = _vpcDao.createSearchCriteria();
            ssc.addOr("displayText", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            ssc.addOr("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
            sc.addAnd("name", SearchCriteria.Op.SC, ssc);
        }

        if (vpcName != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + vpcName + "%");
        }

        if (displayText != null) {
            sc.addAnd("displayText", SearchCriteria.Op.LIKE, "%" + displayText + "%");
        }

        if (tags != null && !tags.isEmpty()) {
            int count = 0;
            sc.setJoinParameters("tagSearch", "resourceType", ResourceObjectType.Vpc.toString());
            for (final Map.Entry<String, String> entry : tags.entrySet()) {
                sc.setJoinParameters("tagSearch", "key" + String.valueOf(count), entry.getKey());
                sc.setJoinParameters("tagSearch", "value" + String.valueOf(count), entry.getValue());
                count++;
            }
        }

        if (display != null) {
            sc.setParameters("display", display);
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (vpcOffId != null) {
            sc.addAnd("vpcOfferingId", SearchCriteria.Op.EQ, vpcOffId);
        }

        if (zoneId != null) {
            sc.addAnd("zoneId", SearchCriteria.Op.EQ, zoneId);
        }

        if (state != null) {
            sc.addAnd("state", SearchCriteria.Op.EQ, state);
        }

        if (cidr != null) {
            sc.addAnd("cidr", SearchCriteria.Op.EQ, cidr);
        }

        if (restartRequired != null) {
            sc.addAnd("restartRequired", SearchCriteria.Op.EQ, restartRequired);
        }

        final List<VpcVO> vpcs = _vpcDao.search(sc, searchFilter);

        // filter by supported services
        final boolean listBySupportedServices = supportedServicesStr != null && !supportedServicesStr.isEmpty()
                && !vpcs.isEmpty();

        if (listBySupportedServices) {
            final List<VpcVO> supportedVpcs = new ArrayList<>();
            Service[] supportedServices = null;

            if (listBySupportedServices) {
                supportedServices = getServices(supportedServicesStr);
            }

            for (final VpcVO vpc : vpcs) {
                if (areServicesSupportedByVpcOffering(vpc.getVpcOfferingId(), supportedServices)) {
                    supportedVpcs.add(vpc);
                }
            }

            final List<? extends Vpc> wPagination = StringUtils.applyPagination(supportedVpcs, startIndex, pageSizeVal);
            if (wPagination != null) {
                final Pair<List<? extends Vpc>, Integer> listWPagination = new Pair<>(wPagination,
                        supportedVpcs.size());
                return listWPagination;
            }
            return new Pair<>(supportedVpcs, supportedVpcs.size());
        } else {
            final List<? extends Vpc> wPagination = StringUtils.applyPagination(vpcs, startIndex, pageSizeVal);
            if (wPagination != null) {
                final Pair<List<? extends Vpc>, Integer> listWPagination = new Pair<>(wPagination,
                        vpcs.size());
                return listWPagination;
            }
            return new Pair<>(vpcs, vpcs.size());
        }
    }

    private Service[] getServices(final List<String> supportedServicesStr) {
        final Service[] supportedServices;
        supportedServices = new Service[supportedServicesStr.size()];
        int i = 0;
        for (final String supportedServiceStr : supportedServicesStr) {
            final Service service = Service.getService(supportedServiceStr);
            if (service == null) {
                throw new InvalidParameterValueException("Invalid service specified " + supportedServiceStr);
            } else {
                supportedServices[i] = service;
            }
            i++;
        }
        return supportedServices;
    }

    @Override
    public boolean startVpc(final long vpcId, final boolean destroyOnFailure)
            throws ConcurrentOperationException, ResourceUnavailableException, InsufficientCapacityException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        final User callerUser = _accountMgr.getActiveUser(ctx.getCallingUserId());

        // check if vpc exists
        final Vpc vpc = getActiveVpc(vpcId);
        if (vpc == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException(
                    "Unable to find Enabled VPC by id specified");
            ex.addProxyObject(String.valueOf(vpcId), "VPC");
            throw ex;
        }

        // permission check
        _accountMgr.checkAccess(caller, null, false, vpc);

        final Zone zone = zoneRepository.findById(vpc.getZoneId()).orElse(null);

        final DeployDestination dest = new DeployDestination(zone, null, null, null);
        final ReservationContext context = new ReservationContextImpl(null, null, callerUser,
                _accountMgr.getAccount(vpc.getAccountId()));

        boolean result = true;
        try {
            if (!startVpc(vpc, dest, context)) {
                s_logger.warn("Failed to start vpc " + vpc);
                result = false;
            }
        } catch (final Exception ex) {
            s_logger.warn("Failed to start vpc " + vpc + " due to ", ex);
            result = false;
        } finally {
            // do cleanup
            if (!result && destroyOnFailure) {
                s_logger.debug("Destroying vpc " + vpc + " that failed to start");
                if (destroyVpc(vpc, caller, callerUser.getId())) {
                    s_logger.warn("Successfully destroyed vpc " + vpc + " that failed to start");
                } else {
                    s_logger.warn("Failed to destroy vpc " + vpc + " that failed to start");
                }
            }
        }
        return result;
    }

    @Override
    public boolean shutdownVpc(final long vpcId) throws ConcurrentOperationException, ResourceUnavailableException {
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();

        // check if vpc exists
        final Vpc vpc = _vpcDao.findById(vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("Unable to find vpc by id " + vpcId);
        }

        // permission check
        _accountMgr.checkAccess(caller, null, false, vpc);

        // shutdown provider
        s_logger.debug("Shutting down vpc " + vpc);
        // TODO - shutdown all vpc resources here (ACLs, gateways, etc)

        boolean success = true;
        final List<Provider> providersToImplement = getVpcProviders(vpc.getId());
        final ReservationContext context = new ReservationContextImpl(null, null,
                _accountMgr.getActiveUser(ctx.getCallingUserId()), caller);
        for (final VpcProvider element : getVpcElements()) {
            if (providersToImplement.contains(element.getProvider())) {
                if (element.shutdownVpc(vpc, context)) {
                    s_logger.debug("Vpc " + vpc + " has been shutdown succesfully");
                } else {
                    s_logger.warn("Vpc " + vpc + " failed to shutdown");
                    success = false;
                }
            }
        }

        return success;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_VPC_RESTART, eventDescription = "restarting vpc")
    public boolean restartVpc(final long vpcId, final boolean cleanUp)
            throws ConcurrentOperationException, ResourceUnavailableException,
            InsufficientCapacityException {

        final Account callerAccount = CallContext.current().getCallingAccount();
        final User callerUser = _accountMgr.getActiveUser(CallContext.current().getCallingUserId());
        final ReservationContext context = new ReservationContextImpl(null, null, callerUser, callerAccount);

        // Verify input parameters
        final Vpc vpc = getActiveVpc(vpcId);
        if (vpc == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException(
                    "Unable to find Enabled VPC by id specified");
            ex.addProxyObject(String.valueOf(vpcId), "VPC");
            throw ex;
        }

        _accountMgr.checkAccess(callerAccount, null, false, vpc);

        s_logger.debug("Restarting VPC " + vpc);
        boolean restartRequired = false;
        try {
            if (cleanUp) {
                List<DomainRouterVO> routers = _routerDao.listByVpcId(vpc.getId());
                if (routers != null && !routers.isEmpty()) {
                    s_logger.debug("Shutting down VPC " + vpc + " as a part of VPC restart process");
                    // Get rid of any non-Running routers
                    for (final DomainRouterVO router : routers) {
                        if (router.getState() != VirtualMachine.State.Running) {
                            s_logger.debug("Destroying " + router + " as it is not in Running state anyway");
                            _routerMgr.destroyRouter(router.getId(), context.getAccount(), context.getCaller().getId());
                        }
                    }
                    // Refresh the list of routers
                    routers = _routerDao.listByVpcId(vpc.getId());
                    if (routers != null && !routers.isEmpty()) {
                        if (!rollingRestartVpc(vpc, routers, context)) {
                            s_logger.warn("Failed to execute a rolling restart as a part of VPC " + vpc + " restart process");
                            restartRequired = true;
                            return false;
                        }
                    }
                }
            } else {
                s_logger.info("Will not shutdown vpc as a part of VPC " + vpc + " restart process.");
            }

            s_logger.debug("Starting VPC " + vpc + " as a part of VPC restart process");
            if (!startVpc(vpcId, false)) {
                s_logger.warn("Failed to start vpc as a part of VPC " + vpc + " restart process");
                restartRequired = true;
                return false;
            }
            s_logger.debug("VPC " + vpc + " was restarted successfully");
            return true;
        } finally {
            s_logger.debug("Updating VPC " + vpc + " with restartRequired=" + restartRequired);
            final VpcVO vo = _vpcDao.findById(vpcId);
            vo.setRestartRequired(restartRequired);
            _vpcDao.update(vpc.getId(), vo);
        }
    }

    private boolean rollingRestartVpc(final Vpc vpc, final List<DomainRouterVO> routers, final ReservationContext context) throws ResourceUnavailableException,
            ConcurrentOperationException,
            InsufficientCapacityException {
        final int sleepTimeInMsAfterRouterStart = 10000;
        final int numberOfRoutersWhenSingle = 1;
        final int numberOfRoutersWhenRedundant = 2;

        // check the master and backup redundant state
        DomainRouterVO mainRouter = null;
        DomainRouterVO secondaryRouter = null;
        if (routers != null && routers.size() == numberOfRoutersWhenSingle) {
            mainRouter = routers.get(0);
            s_logger.debug("Rolling restart found a single router " + mainRouter.getInstanceName() + " as part of rolling restart of VPC " + vpc);
        }
        if (routers != null && routers.size() == numberOfRoutersWhenRedundant) {
            final DomainRouterVO router1 = routers.get(0);
            final DomainRouterVO router2 = routers.get(1);
            if (router1.getRedundantState() == VirtualRouter.RedundantState.MASTER || router2.getRedundantState() == VirtualRouter.RedundantState.BACKUP) {
                mainRouter = router1;
                secondaryRouter = router2;
            } else if (router1.getRedundantState() == VirtualRouter.RedundantState.BACKUP || router2.getRedundantState() == VirtualRouter.RedundantState.MASTER) {
                mainRouter = router2;
                secondaryRouter = router1;
            } else {
                // both routers are in UNKNOWN state or in the same state. Order doesn't matter.
                mainRouter = router1;
                secondaryRouter = router2;
            }
            s_logger.debug("Rolling restart of VPC " + vpc + " will first replace router " + secondaryRouter.getInstanceName() + " and then router " + mainRouter.getInstanceName
                    ());
        }

        final DeployDestination dest = new DeployDestination(zoneRepository.findById(vpc.getZoneId()).orElse(null), null, null, null);

        // If we are supposed to be redundant, let's replace the backup router
        // We do this even when backupRouter is null, so we first spin a new router before replacing the other router
        if (vpc.isRedundant()) {
            if (!replaceRouter(vpc, context, sleepTimeInMsAfterRouterStart, secondaryRouter, dest)) {
                s_logger.debug("Recreating the secondary router for VPC " + vpc + " failed.");
                return false;
            }
        }

        // If we have a single router, replace it here
        if (mainRouter != null) {
            if (!replaceRouter(vpc, context, sleepTimeInMsAfterRouterStart, mainRouter, dest)) {
                s_logger.debug("Recreating the main router for VPC " + vpc + " failed.");
                return false;
            }
        }

        return true;
    }

    private boolean replaceRouter(final Vpc vpc, final ReservationContext context, final int sleepTimeInMsAfterRouterStart, final DomainRouterVO routerToReplace, final
    DeployDestination dest) throws ResourceUnavailableException, InsufficientCapacityException {
        if (routerToReplace != null) {
            s_logger.debug("Destroying router " + routerToReplace.getInstanceName() + " as part of rolling restart of VPC " + vpc);
            _routerMgr.destroyRouter(routerToReplace.getId(), context.getAccount(), context.getCaller().getId());
        }
        s_logger.debug("Triggering new router create as part of rolling restart of VPC " + vpc);
        startVpc(vpc, dest, context);
        try {
            // wait for the keepalived/conntrackd on router
            Thread.sleep(sleepTimeInMsAfterRouterStart);
        } catch (final InterruptedException e) {
            s_logger.trace("Ignoring InterruptedException.", e);
        }

        // Routers after this action
        final List<DomainRouterVO> routers = _routerDao.listByVpcId(vpc.getId());
        for (final DomainRouterVO router : routers) {
            // Both should be in state Running, or else the provisioning went wrong somehow as we started with destroying non-Running routers
            // In order not to kill both routers, we'll stop the procedure.
            if (router.getState() != VirtualMachine.State.Running) {
                s_logger.debug("Found router " + router.getInstanceName() + " part of VPC " + vpc + " to be in non-Running state " + router.getState() + ", so not proceeding " +
                        "with" +
                        "next router to prevent downtime. Please try again.");
                return false;
            }
        }
        return true;
    }

    protected boolean startVpc(final Vpc vpc, final DeployDestination dest, final ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException,
            InsufficientCapacityException {
        // deploy provider
        boolean success = true;
        final List<Provider> providersToImplement = getVpcProviders(vpc.getId());
        for (final VpcProvider element : getVpcElements()) {
            if (providersToImplement.contains(element.getProvider())) {
                if (element.implementVpc(vpc, dest, context)) {
                    s_logger.debug("Vpc " + vpc + " has started succesfully");
                } else {
                    s_logger.warn("Vpc " + vpc + " failed to start");
                    success = false;
                }
            }
        }
        return success;
    }

    @Override
    public PrivateGateway getVpcPrivateGateway(final long id) {
        final VpcGateway gateway = _vpcGatewayDao.findById(id);

        if (gateway == null || gateway.getType() != VpcGateway.Type.Private) {
            return null;
        }
        return getPrivateGatewayProfile(gateway);
    }

    @Override
    @DB
    public boolean destroyVpc(final Vpc vpc, final Account caller, final Long callerUserId)
            throws ConcurrentOperationException, ResourceUnavailableException {
        s_logger.debug("Destroying vpc " + vpc);

        // don't allow to delete vpc if it's in use by existing non system
        // networks (system networks are networks of a private gateway of the
        // VPC,
        // and they will get removed as a part of VPC cleanup
        final int networksCount = _ntwkDao.getNonSystemNetworkCountByVpcId(vpc.getId());
        if (networksCount > 0) {
            throw new InvalidParameterValueException(
                    "Can't delete VPC " + vpc + " as its used by " + networksCount + " networks");
        }

        // mark VPC as inactive
        if (vpc.getState() != Vpc.State.Inactive) {
            s_logger.debug("Updating VPC " + vpc + " with state " + Vpc.State.Inactive + " as a part of vpc delete");
            final VpcVO vpcVO = _vpcDao.findById(vpc.getId());
            vpcVO.setState(Vpc.State.Inactive);

            Transaction.execute(new TransactionCallbackNoReturn() {
                @Override
                public void doInTransactionWithoutResult(final TransactionStatus status) {
                    _vpcDao.update(vpc.getId(), vpcVO);

                    // decrement resource count
                    _resourceLimitMgr.decrementResourceCount(vpc.getAccountId(), ResourceType.vpc);
                }
            });
        }

        // shutdown VPC
        if (!shutdownVpc(vpc.getId())) {
            s_logger.warn("Failed to shutdown vpc " + vpc + " as a part of vpc destroy process");
            return false;
        }

        // cleanup vpc resources
        if (!cleanupVpcResources(vpc.getId(), caller, callerUserId)) {
            s_logger.warn("Failed to cleanup resources for vpc " + vpc);
            return false;
        }

        // update the instance with removed flag only when the cleanup is
        // executed successfully
        if (_vpcDao.remove(vpc.getId())) {
            s_logger.debug("Vpc " + vpc + " is destroyed succesfully");
            return true;
        } else {
            s_logger.warn("Vpc " + vpc + " failed to destroy");
            return false;
        }
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_PRIVATE_GATEWAY_CREATE, eventDescription = "creating VPC private gateway",
            create = true)
    public PrivateGateway createVpcPrivateGateway(final long vpcId, final String ipAddress, final String gateway,
                                                  final String netmask, final long gatewayDomainId, final Long networkId, final Boolean isSourceNat, final Long aclId)
            throws ResourceAllocationException, ConcurrentOperationException, InsufficientCapacityException {

        // Validate parameters
        final Vpc vpc = getActiveVpc(vpcId);
        if (vpc == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("Unable to find Enabled VPC by id specified");
            ex.addProxyObject(String.valueOf(vpcId), "VPC");
            throw ex;
        }

        // permission check on the VPC
        final CallContext ctx = CallContext.current();
        final Account caller = ctx.getCallingAccount();
        _accountMgr.checkAccess(caller, null, false, vpc);

        if (gateway != null || netmask != null) {
            throw new InvalidParameterValueException("Gateway/netmask fields are not supported anymore");
        }

        final Network privateNtwk = _ntwkDao.findById(networkId);
        if (privateNtwk == null) {
            throw new InvalidParameterValueException("The private network specified could not be found.");
        }

        if (privateNtwk.getDomainId() != vpc.getDomainId() && !_accountMgr.isRootAdmin(caller.getId())) {
            throw new InvalidParameterValueException("VPC '" + vpc.getName() + "' does not have permission to operate on private network '" + privateNtwk.getName()
                    + "' as they need to belong to the same domain.");
        }

        if (NetUtils.isNetworkAWithinNetworkB(privateNtwk.getCidr(), vpc.getCidr())) {
            throw new InvalidParameterValueException(
                    "CIDR of the private network to be connected " + privateNtwk.getCidr() +
                            " should be outside of the VPC super CIDR " + vpc.getCidr());
        }

        if (!NetUtils.isIpWithtInCidrRange(ipAddress, privateNtwk.getCidr())) {
            throw new InvalidParameterValueException(
                    "The specified ip address for the private network " + ipAddress +
                            " should be within the CIDR of the private network " + privateNtwk.getCidr());
        }

        final SortedSet<Long> availableIps = _ntwkModel.getAvailableIps(privateNtwk, ipAddress);

        if (availableIps == null || availableIps.isEmpty()) {
            throw new InvalidParameterValueException("The requested ip address " + ipAddress + " is not available in private network " + privateNtwk.getName());
        }

        final Long privateNetworkId = privateNtwk.getId();
        final List<PrivateGateway> privateGateways = getVpcPrivateGateways(vpcId);
        for (final PrivateGateway privateGateway : privateGateways) {
            if (privateNetworkId == privateGateway.getNetworkId()) {
                throw new InvalidParameterValueException(
                        "VPC with uuid " + vpc.getUuid() + " is already connected to network '"
                                + privateNtwk.getName() + "'");
            }
        }

        final VpcGatewayVO gatewayVO;
        try {
            gatewayVO = Transaction.execute(new TransactionCallbackWithException<VpcGatewayVO, Exception>() {
                @Override
                public VpcGatewayVO doInTransaction(final TransactionStatus status)
                        throws ResourceAllocationException, ConcurrentOperationException,
                        InsufficientCapacityException {

                    // create the nic/ip as createPrivateNetwork doesn't do that work for us now
                    s_logger.info("found and using existing network for vpc " + vpc + ": " + privateNtwk.getBroadcastUri());
                    final DataCenterVO dc = _dcDao.lockRow(vpc.getZoneId(), true);

                    // add entry to private_ip_address table
                    PrivateIpVO privateIp = _privateIpDao.findByIpAndSourceNetworkId(privateNtwk.getId(), ipAddress);
                    if (privateIp != null) {
                        throw new InvalidParameterValueException(
                                "Private IP address " + ipAddress + " already used for private gateway in zone "
                                        + _entityMgr.findById(DataCenter.class, vpc.getZoneId()).getName());
                    }

                    final Long mac = dc.getMacAddress();
                    final Long nextMac = mac + 1;
                    dc.setMacAddress(nextMac);

                    s_logger.info("creating private IP address for VPC (" + ipAddress + ", " + privateNtwk.getId() + ", "
                            + nextMac + ", " + vpcId + ", " + isSourceNat + ")");
                    privateIp = new PrivateIpVO(ipAddress, privateNtwk.getId(), nextMac, vpcId, isSourceNat);
                    _privateIpDao.persist(privateIp);

                    _dcDao.update(dc.getId(), dc);

                    long networkAclId = NetworkACL.DEFAULT_DENY;
                    if (aclId != null) {
                        final NetworkACLVO aclVO = _networkAclDao.findById(aclId);
                        if (aclVO == null) {
                            throw new InvalidParameterValueException("Invalid network acl id passed ");
                        }
                        if (aclVO.getVpcId() != vpcId && !(aclId == NetworkACL.DEFAULT_DENY || aclId == NetworkACL.DEFAULT_ALLOW)) {
                            throw new InvalidParameterValueException("Private gateway and network acl are not in the same vpc");
                        }

                        networkAclId = aclId;
                    }

                    // 2) create gateway entry
                    final VpcGatewayVO gatewayVO = new VpcGatewayVO(ipAddress, VpcGateway.Type.Private, vpcId,
                            privateNtwk.getDataCenterId(), privateNtwk.getId(),
                            vpc.getAccountId(), vpc.getDomainId(), isSourceNat, networkAclId);
                    _vpcGatewayDao.persist(gatewayVO);

                    s_logger.debug("Created vpc gateway entry " + gatewayVO);

                    return gatewayVO;
                }
            });
        } catch (final Exception e) {
            ExceptionUtil.rethrowRuntime(e);
            ExceptionUtil.rethrow(e, InsufficientCapacityException.class);
            ExceptionUtil.rethrow(e, ResourceAllocationException.class);
            throw new IllegalStateException(e);
        }

        CallContext.current().setEventDetails("Private Gateway Id: " + gatewayVO.getId());
        return getVpcPrivateGateway(gatewayVO.getId());
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_PRIVATE_GATEWAY_CREATE, eventDescription = "Applying VPC private gateway",
            async = true)
    public PrivateGateway applyVpcPrivateGateway(final long gatewayId, final boolean destroyOnFailure)
            throws ConcurrentOperationException, ResourceUnavailableException {
        final VpcGatewayVO vo = _vpcGatewayDao.findById(gatewayId);

        boolean success = true;
        try {
            final List<Provider> providersToImplement = getVpcProviders(vo.getVpcId());

            final PrivateGateway gateway = getVpcPrivateGateway(gatewayId);
            for (final VpcProvider provider : getVpcElements()) {
                if (providersToImplement.contains(provider.getProvider())) {
                    if (!provider.createPrivateGateway(gateway)) {
                        success = false;
                    }
                }
            }
            if (success) {
                s_logger.debug("Private gateway " + gateway + " was applied succesfully on the backend");
                if (vo.getState() != VpcGateway.State.Ready) {
                    vo.setState(VpcGateway.State.Ready);
                    _vpcGatewayDao.update(vo.getId(), vo);
                    s_logger.debug("Marke gateway " + gateway + " with state " + VpcGateway.State.Ready);
                }
                CallContext.current().setEventDetails("Private Gateway Id: " + gatewayId);
                return getVpcPrivateGateway(gatewayId);
            } else {
                s_logger.warn("Private gateway " + gateway + " failed to apply on the backend");
                return null;
            }
        } finally {
            if (!success) {
                if (destroyOnFailure) {
                    s_logger.debug("Destroying private gateway " + vo + " that failed to start");
                    // calling deleting from db because on createprivategateway
                    // fail, destroyPrivateGateway is already called
                    if (deletePrivateGatewayFromTheDB(getVpcPrivateGateway(gatewayId))) {
                        s_logger.warn("Successfully destroyed vpc " + vo + " that failed to start");
                    } else {
                        s_logger.warn("Failed to destroy vpc " + vo + " that failed to start");
                    }
                }
            }
        }
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_PRIVATE_GATEWAY_DELETE, eventDescription = "deleting private gateway")
    @DB
    public boolean deleteVpcPrivateGateway(final long gatewayId) throws ConcurrentOperationException, ResourceUnavailableException {
        final VpcGatewayVO gatewayVO = _vpcGatewayDao.acquireInLockTable(gatewayId);
        if (gatewayVO == null || gatewayVO.getType() != VpcGateway.Type.Private) {
            throw new ConcurrentOperationException("Unable to lock gateway " + gatewayId);
        }

        try {
            Transaction.execute(new TransactionCallbackNoReturn() {
                @Override
                public void doInTransactionWithoutResult(final TransactionStatus status) {
                    // don't allow to remove gateway when there are static routes pointing to an ipaddress in the private gateway CIDR.
                    final List<? extends StaticRoute> routes = _staticRouteDao.listByVpcIdAndNotRevoked(gatewayVO.getVpcId());
                    final NetworkVO network = _ntwkDao.findById(gatewayVO.getNetworkId());
                    final List<String> wrongCidrs = new LinkedList<>();
                    for (final StaticRoute route : routes) {
                        if (NetUtils.isIpWithtInCidrRange(route.getGwIpAddress(), network.getCidr())) {
                            wrongCidrs.add(route.getCidr());
                        }
                    }

                    if (!wrongCidrs.isEmpty()) {
                        throw new InvalidParameterValueException("Unable to delete Private Gateway. Please remove these static routes pointing to the private gateway CIDR" +
                                " before attempting to delete the gateway: " + wrongCidrs);
                    }
                    gatewayVO.setState(VpcGateway.State.Deleting);
                    _vpcGatewayDao.update(gatewayVO.getId(), gatewayVO);
                    s_logger.debug("Marked gateway " + gatewayVO + " with state " + VpcGateway.State.Deleting);
                }
            });

            // Delete the gateway on the backend
            final List<Provider> providersToImplement = getVpcProviders(gatewayVO.getVpcId());
            final PrivateGateway gateway = getVpcPrivateGateway(gatewayId);
            for (final VpcProvider provider : getVpcElements()) {
                if (providersToImplement.contains(provider.getProvider())) {
                    if (provider.deletePrivateGateway(gateway)) {
                        s_logger.debug("Private gateway " + gateway + " was applied succesfully on the backend");
                    } else {
                        s_logger.warn("Private gateway " + gateway + " failed to apply on the backend");
                        gatewayVO.setState(VpcGateway.State.Ready);
                        _vpcGatewayDao.update(gatewayVO.getId(), gatewayVO);
                        s_logger.debug("Marked gateway " + gatewayVO + " with state " + VpcGateway.State.Ready);

                        return false;
                    }
                }
            }

            return deletePrivateGatewayFromTheDB(gateway);
        } finally {
            _vpcGatewayDao.releaseFromLockTable(gatewayId);
        }
    }

    private boolean deletePrivateGatewayFromTheDB(final PrivateGateway gateway) {
        try {
            vpcTxCallable.setGateway(gateway);
            final ExecutorService txExecutor = Executors.newSingleThreadExecutor();
            final Future<Boolean> futureResult = txExecutor.submit(vpcTxCallable);

            final boolean deleted = futureResult.get();
            s_logger.info("Delete Private Gateway succeeded? " + deleted);
        } catch (InterruptedException | ExecutionException e) {
            s_logger.error("Delete Private Gateway failed due to => ", e);
        }
        return true;
    }

    @Override
    public Pair<List<PrivateGateway>, Integer> listPrivateGateway(final ListPrivateGatewaysCmd cmd) {
        final String ipAddress = cmd.getIpAddress();
        final String networkId = cmd.getNetworkId();
        final Long vpcId = cmd.getVpcId();
        final Long id = cmd.getId();
        Boolean isRecursive = cmd.isRecursive();
        final Boolean listAll = cmd.listAll();
        Long domainId = cmd.getDomainId();
        final String accountName = cmd.getAccountName();
        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();
        final String state = cmd.getState();
        final Long projectId = cmd.getProjectId();

        final Filter searchFilter = new Filter(VpcGatewayVO.class, "id", false, cmd.getStartIndex(), cmd.getPageSizeVal());
        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(
                domainId, isRecursive,
                null);
        _accountMgr.buildACLSearchParameters(caller, id, accountName, projectId, permittedAccounts,
                domainIdRecursiveListProject, listAll, false);
        domainId = domainIdRecursiveListProject.first();
        isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();

        final SearchBuilder<VpcGatewayVO> sb = _vpcGatewayDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        final SearchCriteria<VpcGatewayVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        if (id != null) {
            sc.addAnd("id", Op.EQ, id);
        }

        if (ipAddress != null) {
            sc.addAnd("ip4Address", Op.EQ, ipAddress);
        }

        if (state != null) {
            sc.addAnd("state", Op.EQ, state);
        }

        if (vpcId != null) {
            sc.addAnd("vpcId", Op.EQ, vpcId);
        }

        if (networkId != null) {
            sc.addAnd("networkId", Op.EQ, networkId);
        }

        final Pair<List<VpcGatewayVO>, Integer> vos = _vpcGatewayDao.searchAndCount(sc, searchFilter);
        final List<PrivateGateway> privateGtws = new ArrayList<>(vos.first().size());
        for (final VpcGateway vo : vos.first()) {
            privateGtws.add(getPrivateGatewayProfile(vo));
        }

        return new Pair<>(privateGtws, vos.second());
    }

    @Override
    public StaticRoute getStaticRoute(final long routeId) {
        return _staticRouteDao.findById(routeId);
    }

    @Override
    public boolean applyStaticRoutesForVpc(final long vpcId) throws ResourceUnavailableException {
        final Account caller = CallContext.current().getCallingAccount();
        final List<? extends StaticRoute> routes = _staticRouteDao.listByVpcId(vpcId);
        return applyStaticRoutes(routes, caller, true);
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_STATIC_ROUTE_DELETE, eventDescription = "deleting static route")
    public boolean revokeStaticRoute(final long routeId) throws ResourceUnavailableException {
        final Account caller = CallContext.current().getCallingAccount();

        final StaticRouteVO route = _staticRouteDao.findById(routeId);
        if (route == null) {
            throw new InvalidParameterValueException("Unable to find static route by id");
        }

        _accountMgr.checkAccess(caller, null, false, route);

        markStaticRouteForRevoke(route, caller);

        return applyStaticRoutesForVpc(route.getVpcId());
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_STATIC_ROUTE_CREATE, eventDescription = "creating static route",
            create = true)
    public StaticRoute createStaticRoute(final long vpcId, final String cidr, final String gwIpAddress) throws NetworkRuleConflictException {
        final Account caller = CallContext.current().getCallingAccount();

        final Vpc vpc = getActiveVpc(vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("Can't add static route to VPC that is being deleted");
        }
        _accountMgr.checkAccess(caller, null, false, vpc);

        if (!NetUtils.isValidIp4Cidr(cidr)) {
            throw new InvalidParameterValueException("Invalid format for cidr " + cidr);
        }

        if (!NetUtils.isValidIp4(gwIpAddress)) {
            throw new InvalidParameterValueException("Invalid format for ip address " + gwIpAddress);
        }

        // CIDR should be outside of link-local cidr
        if (NetUtils.isNetworkAWithinNetworkB(cidr, NetUtils.getLinkLocalCIDR())) {
            throw new InvalidParameterValueException(
                    "CIDR should be outside of link local cidr " + NetUtils.getLinkLocalCIDR());
        }

        // Verify against blacklisted routes
        if (isCidrBlacklisted(cidr, vpc.getZoneId())) {
            throw new InvalidParameterValueException(
                    "The static gateway cidr overlaps with one of the blacklisted routes of the zone the VPC belongs to");
        }

        return Transaction.execute(new TransactionCallbackWithException<StaticRouteVO, NetworkRuleConflictException>() {
            @Override
            public StaticRouteVO doInTransaction(final TransactionStatus status) throws NetworkRuleConflictException {
                StaticRouteVO newRoute = new StaticRouteVO(cidr, vpc.getId(), vpc.getAccountId(),
                        vpc.getDomainId(), gwIpAddress);
                s_logger.debug("Adding static route " + newRoute);
                newRoute = _staticRouteDao.persist(newRoute);

                detectDuplicateCidr(newRoute);

                if (!_staticRouteDao.setStateToAdd(newRoute)) {
                    throw new CloudRuntimeException("Unable to update the state to add for " + newRoute);
                }
                CallContext.current().setEventDetails("Static route Id: " + newRoute.getId());

                return newRoute;
            }
        });
    }

    @DB
    @Override
    public void validateNtwkOffForNtwkInVpc(final Long networkId, final long newNtwkOffId, final String newCidr,
                                            final String newNetworkDomain, final Vpc vpc,
                                            final String gateway, final Account networkOwner, final Long aclId) {

        final NetworkOffering guestNtwkOff = _entityMgr.findById(NetworkOffering.class, newNtwkOffId);

        if (guestNtwkOff == null) {
            throw new InvalidParameterValueException("Can't find network offering by id specified");
        }

        if (networkId == null) {
            // 1) Validate attributes that has to be passed in when create new
            // guest network
            validateNewVpcGuestNetwork(newCidr, gateway, networkOwner, vpc, newNetworkDomain);
        }

        // 2) validate network offering attributes
        final List<Service> svcs = _ntwkModel.listNetworkOfferingServices(guestNtwkOff.getId());
        validateNtwkOffForVpc(guestNtwkOff, svcs);

        // 3) Check services/providers against VPC providers
        final List<NetworkOfferingServiceMapVO> networkProviders = _ntwkOffServiceDao.listByNetworkOfferingId(
                guestNtwkOff.getId());

        for (final NetworkOfferingServiceMapVO nSvcVO : networkProviders) {
            final String pr = nSvcVO.getProvider();
            final String service = nSvcVO.getService();
            if (_vpcOffServiceDao.findByServiceProviderAndOfferingId(service, pr, vpc.getVpcOfferingId()) == null) {
                throw new InvalidParameterValueException(
                        "Service/provider combination " + service + "/" + pr + " is not supported by VPC " + vpc);
            }
        }

        // 4) Only one network in the VPC can support public LB inside the VPC.
        // Internal LB can be supported on multiple VPC tiers
        if (_ntwkModel.areServicesSupportedByNetworkOffering(guestNtwkOff.getId(), Service.Lb)
                && guestNtwkOff.getPublicLb()) {
            final List<? extends Network> networks = getVpcNetworks(vpc.getId());
            for (final Network network : networks) {
                if (networkId != null && network.getId() == networkId.longValue()) {
                    // skip my own network
                    continue;
                } else {
                    final NetworkOffering otherOff = _entityMgr.findById(NetworkOffering.class, network.getNetworkOfferingId());
                    // throw only if networks have different offerings with
                    // public lb support
                    if (_ntwkModel.areServicesSupportedInNetwork(network.getId(), Service.Lb) && otherOff.getPublicLb()
                            && guestNtwkOff.getId() != otherOff.getId()) {
                        throw new InvalidParameterValueException(
                                "Public LB service is already supported " + "by network " + network + " in VPC " + vpc);
                    }
                }
            }
        }

        // 5) When aclId is provided, verify that ACLProvider is supported by
        // network offering
        if (aclId != null && !_ntwkModel.areServicesSupportedByNetworkOffering(guestNtwkOff.getId(), Service.NetworkACL)) {
            throw new InvalidParameterValueException(
                    "Cannot apply NetworkACL. Network Offering does not support NetworkACL service");
        }
    }

    private boolean isCidrBlacklisted(final String cidr, final long zoneId) {
        final String routesStr = NetworkOrchestrationService.BlacklistedRoutes.valueIn(zoneId);
        if (routesStr != null && !routesStr.isEmpty()) {
            final String[] cidrBlackList = routesStr.split(",");

            if (cidrBlackList.length > 0) {
                for (final String blackListedRoute : cidrBlackList) {
                    if (NetUtils.isNetworksOverlap(blackListedRoute, cidr)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void detectDuplicateCidr(final StaticRoute newRoute) throws NetworkRuleConflictException {
        final List<? extends StaticRoute> routes = _staticRouteDao.listByVpcIdAndNotRevoked(newRoute.getVpcId());
        assert routes.size() >= 1 : "For static routes, we now always first persist the route and then check for "
                + "network conflicts so we should at least have one rule at this point.";
        for (final StaticRoute route : routes) {
            if (route.getId() == newRoute.getId()) {
                continue; // Skips my own route.
            }
            if (newRoute.getCidr().equals(route.getCidr())) {
                throw new NetworkRuleConflictException("New static route cidr already exists in VPC. UUID of existing static route is " + route.getUuid());
            }
        }
    }

    @Override
    public void validateNtwkOffForVpc(final NetworkOffering guestNtwkOff, final List<Service> supportedSvcs) {
        // 1) in current release, only vpc provider is supported by Vpc offering
        final List<Provider> providers = _ntwkModel.getNtwkOffDistinctProviders(guestNtwkOff.getId());
        for (final Provider provider : providers) {
            if (!supportedProviders.contains(provider)) {
                throw new InvalidParameterValueException("Provider of type " + provider.getName()
                        + " is not supported for network offerings that can be used in VPC");
            }
        }

        // 2) Conserve mode should be off
        if (guestNtwkOff.isConserveMode()) {
            throw new InvalidParameterValueException("Only networks with conserve mode Off can belong to VPC");
        }
    }

    @Override
    public Pair<List<? extends StaticRoute>, Integer> listStaticRoutes(final ListStaticRoutesCmd cmd) {
        final Long id = cmd.getId();
        final String nextHop = cmd.getNextHop();
        final String cidr = cmd.getCidr();
        final Long vpcId = cmd.getVpcId();
        Long domainId = cmd.getDomainId();
        Boolean isRecursive = cmd.isRecursive();
        final Boolean listAll = cmd.listAll();
        final String accountName = cmd.getAccountName();
        final Account caller = CallContext.current().getCallingAccount();
        final List<Long> permittedAccounts = new ArrayList<>();
        final Map<String, String> tags = cmd.getTags();
        final Long projectId = cmd.getProjectId();

        final Ternary<Long, Boolean, ListProjectResourcesCriteria> domainIdRecursiveListProject = new Ternary<>(
                domainId, isRecursive,
                null);
        _accountMgr.buildACLSearchParameters(caller, id, accountName, projectId, permittedAccounts,
                domainIdRecursiveListProject, listAll, false);
        domainId = domainIdRecursiveListProject.first();
        isRecursive = domainIdRecursiveListProject.second();
        final ListProjectResourcesCriteria listProjectResourcesCriteria = domainIdRecursiveListProject.third();
        final Filter searchFilter = new Filter(StaticRouteVO.class, "created", false, cmd.getStartIndex(),
                cmd.getPageSizeVal());

        final SearchBuilder<StaticRouteVO> sb = _staticRouteDao.createSearchBuilder();
        _accountMgr.buildACLSearchBuilder(sb, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);

        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("vpcId", sb.entity().getVpcId(), SearchCriteria.Op.EQ);
        sb.and("gwIpAddress", sb.entity().getGwIpAddress(), SearchCriteria.Op.EQ);
        sb.and("cidr", sb.entity().getCidr(), SearchCriteria.Op.EQ);

        if (tags != null && !tags.isEmpty()) {
            final SearchBuilder<ResourceTagVO> tagSearch = _resourceTagDao.createSearchBuilder();
            for (int count = 0; count < tags.size(); count++) {
                tagSearch.or().op("key" + String.valueOf(count), tagSearch.entity().getKey(), SearchCriteria.Op.EQ);
                tagSearch.and("value" + String.valueOf(count), tagSearch.entity().getValue(), SearchCriteria.Op.EQ);
                tagSearch.cp();
            }
            tagSearch.and("resourceType", tagSearch.entity().getResourceType(), SearchCriteria.Op.EQ);
            sb.groupBy(sb.entity().getId());
            sb.join("tagSearch", tagSearch, sb.entity().getId(), tagSearch.entity().getResourceId(),
                    JoinBuilder.JoinType.INNER);
        }

        final SearchCriteria<StaticRouteVO> sc = sb.create();
        _accountMgr.buildACLSearchCriteria(sc, domainId, isRecursive, permittedAccounts, listProjectResourcesCriteria);
        if (id != null) {
            sc.addAnd("id", Op.EQ, id);
        }

        if (vpcId != null) {
            sc.addAnd("vpcId", Op.EQ, vpcId);
        }

        if (nextHop != null) {
            sc.addAnd("gwIpAddress", Op.EQ, nextHop);
        }

        if (cidr != null) {
            sc.addAnd("cidr", Op.EQ, cidr);
        }

        if (tags != null && !tags.isEmpty()) {
            int count = 0;
            sc.setJoinParameters("tagSearch", "resourceType", ResourceObjectType.StaticRoute.toString());
            for (final String key : tags.keySet()) {
                sc.setJoinParameters("tagSearch", "key" + String.valueOf(count), key);
                sc.setJoinParameters("tagSearch", "value" + String.valueOf(count), tags.get(key));
                count++;
            }
        }

        final Pair<List<StaticRouteVO>, Integer> result = _staticRouteDao.searchAndCount(sc, searchFilter);
        return new Pair<>(result.first(), result.second());
    }

    @DB
    @Override
    @ActionEvent(eventType = EventTypes.EVENT_NET_IP_ASSIGN, eventDescription = "associating Ip", async = true)
    public IpAddress associateIPToVpc(final long ipId, final long vpcId)
            throws ResourceAllocationException, ResourceUnavailableException, InsufficientAddressCapacityException,
            ConcurrentOperationException {
        final Account caller = CallContext.current().getCallingAccount();
        final Account owner;

        final IpAddress ipToAssoc = _ntwkModel.getIp(ipId);
        if (ipToAssoc != null) {
            _accountMgr.checkAccess(caller, null, true, ipToAssoc);
            owner = _accountMgr.getAccount(ipToAssoc.getAllocatedToAccountId());
        } else {
            s_logger.debug("Unable to find ip address by id: " + ipId);
            return null;
        }

        final Vpc vpc = _vpcDao.findById(vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("Invalid VPC id provided");
        }

        // check permissions
        _accountMgr.checkAccess(caller, null, true, owner, vpc);

        if (!hasSourceNatService(vpc)) {
            throw new InvalidParameterValueException("VPC does not support SourceNat service so no public ip addresses can be assigned.");
        }

        boolean isSourceNat = false;
        if (getExistingSourceNatInVpc(owner.getId(), vpcId) == null) {
            isSourceNat = true;
        }

        s_logger.debug("Associating ip " + ipToAssoc + " to vpc " + vpc);

        final boolean isSourceNatFinal = isSourceNat;
        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) {
                final IPAddressVO ip = _ipAddressDao.findById(ipId);
                // update ip address with networkId
                ip.setVpcId(vpcId);
                ip.setSourceNat(isSourceNatFinal);

                _ipAddressDao.update(ipId, ip);

                // mark ip as allocated
                _ipAddrMgr.markPublicIpAsAllocated(ip);
            }
        });

        s_logger.debug("Successfully assigned ip " + ipToAssoc + " to vpc " + vpc);

        return _ipAddressDao.findById(ipId);
    }

    @DB
    private void validateNewVpcGuestNetwork(final String cidr, final String gateway, final Account networkOwner,
                                            final Vpc vpc, final String networkDomain) {

        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(final TransactionStatus status) {
                final Vpc locked = _vpcDao.acquireInLockTable(vpc.getId());
                if (locked == null) {
                    throw new CloudRuntimeException("Unable to acquire lock on " + vpc);
                }

                try {
                    // check number of active networks in vpc
                    if (_ntwkDao.countVpcNetworks(vpc.getId()) >= _maxNetworks) {
                        throw new CloudRuntimeException("Number of networks per VPC can't extend " + _maxNetworks
                                + "; increase it using global config " + Config.VpcMaxNetworks);
                    }

                    // 1) CIDR is required
                    if (cidr == null) {
                        throw new InvalidParameterValueException("Gateway/netmask are required when create network for VPC");
                    }

                    // 2) Network cidr should be within vpcCidr
                    if (!NetUtils.isNetworkAWithinNetworkB(cidr, vpc.getCidr())) {
                        throw new InvalidParameterValueException("Network cidr " + cidr + " is not within vpc " + vpc + " cidr");
                    }

                    // 3) Network cidr shouldn't cross the cidr of other vpc
                    // network cidrs
                    final List<? extends Network> ntwks = _ntwkDao.listByVpc(vpc.getId());
                    for (final Network ntwk : ntwks) {
                        if (NetUtils.isNetworkAWithinNetworkB(ntwk.getCidr(), cidr)
                                || NetUtils.isNetworkAWithinNetworkB(cidr, ntwk.getCidr())) {
                            throw new InvalidParameterValueException(
                                    "Network cidr " + cidr + " crosses other network cidr " + ntwk + " belonging to the same vpc " + vpc);
                        }
                    }

                    // 4) vpc and network should belong to the same owner
                    if (vpc.getAccountId() != networkOwner.getId()) {
                        throw new InvalidParameterValueException(
                                "Vpc " + vpc + " owner is different from the network owner " + networkOwner);
                    }

                    // 5) network domain should be the same as VPC's
                    if (!networkDomain.equalsIgnoreCase(vpc.getNetworkDomain())) {
                        throw new InvalidParameterValueException(
                                "Network domain of the new network should match network" + " domain of vpc " + vpc);
                    }

                    // 6) gateway should never be equal to the cidr subnet
                    if (NetUtils.getCidrSubNet(cidr).equalsIgnoreCase(gateway)) {
                        throw new InvalidParameterValueException(
                                "Invalid gateway specified. It should never be equal to the cidr subnet value");
                    }
                } finally {
                    s_logger.debug("Releasing lock for " + locked);
                    _vpcDao.releaseFromLockTable(locked.getId());
                }
            }
        });
    }

    private boolean hasSourceNatService(final Vpc vpc) {
        final Map<Network.Service, Set<Network.Provider>> vpcOffSvcProvidersMap = getVpcOffSvcProvidersMap(vpc.getVpcOfferingId());

        return vpcOffSvcProvidersMap.containsKey(Network.Service.SourceNat) &&
                vpcOffSvcProvidersMap.get(Network.Service.SourceNat).contains(Network.Provider.VPCVirtualRouter);
    }

    private IPAddressVO getExistingSourceNatInVpc(final long ownerId, final long vpcId) {

        final List<IPAddressVO> addrs = listPublicIpsAssignedToVpc(ownerId, true, vpcId);

        IPAddressVO sourceNatIp = null;
        if (addrs.isEmpty()) {
            return null;
        } else {
            // Account already has ip addresses
            for (final IPAddressVO addr : addrs) {
                if (addr.isSourceNat()) {
                    sourceNatIp = addr;
                    return sourceNatIp;
                }
            }

            assert sourceNatIp != null : "How do we get a bunch of ip addresses but none of them are source nat? "
                    + "account=" + ownerId + "; vpcId=" + vpcId;
        }

        return sourceNatIp;
    }

    private List<IPAddressVO> listPublicIpsAssignedToVpc(final long accountId, final Boolean sourceNat,
                                                         final long vpcId) {
        final SearchCriteria<IPAddressVO> sc = IpAddressSearch.create();
        sc.setParameters("accountId", accountId);
        sc.setParameters("vpcId", vpcId);

        if (sourceNat != null) {
            sc.addAnd("sourceNat", SearchCriteria.Op.EQ, sourceNat);
        }
        sc.setJoinParameters("virtualNetworkVlanSB", "vlanType", VlanType.VirtualNetwork);

        return _ipAddressDao.search(sc, null);
    }

    @Override
    public List<? extends Vpc> getVpcsForAccount(final long accountId) {
        final List<Vpc> vpcs = new ArrayList<>();
        vpcs.addAll(_vpcDao.listByAccountId(accountId));
        return vpcs;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_STATIC_ROUTE_CREATE, eventDescription = "Applying static route",
            async = true)
    public boolean applyStaticRoute(final long routeId) throws ResourceUnavailableException {
        final StaticRoute route = _staticRouteDao.findById(routeId);
        return applyStaticRoutesForVpc(route.getVpcId());
    }

    private boolean cleanupVpcResources(final long vpcId, final Account caller, final long callerUserId)
            throws ResourceUnavailableException, ConcurrentOperationException {
        s_logger.debug("Cleaning up resources for vpc id=" + vpcId);
        boolean success = true;

        // 1) Remove VPN connections and VPN gateway
        s_logger.debug("Cleaning up existed site to site VPN connections");
        _s2sVpnMgr.cleanupVpnConnectionByVpc(vpcId);
        s_logger.debug("Cleaning up existed site to site VPN gateways");
        _s2sVpnMgr.cleanupVpnGatewayByVpc(vpcId);

        // 2) release all ip addresses
        final List<IPAddressVO> ipsToRelease = _ipAddressDao.listByVpc(vpcId, null);
        s_logger.debug("Releasing ips for vpc id=" + vpcId + " as a part of vpc cleanup");
        for (final IPAddressVO ipToRelease : ipsToRelease) {
            success = success && _ipAddrMgr.disassociatePublicIpAddress(ipToRelease.getId(), callerUserId, caller);
            if (!success) {
                s_logger.warn("Failed to cleanup ip " + ipToRelease + " as a part of vpc id=" + vpcId + " cleanup");
            }
        }

        if (success) {
            s_logger.debug("Released ip addresses for vpc id=" + vpcId + " as a part of cleanup vpc process");
        } else {
            s_logger.warn("Failed to release ip addresses for vpc id=" + vpcId + " as a part of cleanup vpc process");
            // although it failed, proceed to the next cleanup step as it
            // doesn't depend on the public ip release
        }

        // 3) Delete all static route rules
        if (!revokeStaticRoutesForVpc(vpcId, caller)) {
            s_logger.warn("Failed to revoke static routes for vpc " + vpcId + " as a part of cleanup vpc process");
            return false;
        }

        // 4) Delete private gateways
        final List<PrivateGateway> gateways = getVpcPrivateGateways(vpcId);
        if (gateways != null) {
            for (final PrivateGateway gateway : gateways) {
                if (gateway != null) {
                    s_logger.debug("Deleting private gateway " + gateway + " as a part of vpc " + vpcId + " resources cleanup");
                    if (!deleteVpcPrivateGateway(gateway.getId())) {
                        success = false;
                        s_logger.debug(
                                "Failed to delete private gateway " + gateway + " as a part of vpc " + vpcId + " resources cleanup");
                    } else {
                        s_logger.debug("Deleted private gateway " + gateway + " as a part of vpc " + vpcId + " resources cleanup");
                    }
                }
            }
        }

        // 5) Delete ACLs
        final SearchBuilder<NetworkACLVO> searchBuilder = _networkAclDao.createSearchBuilder();

        searchBuilder.and("vpcId", searchBuilder.entity().getVpcId(), Op.IN);
        final SearchCriteria<NetworkACLVO> searchCriteria = searchBuilder.create();
        searchCriteria.setParameters("vpcId", vpcId, 0);

        final Filter filter = new Filter(NetworkACLVO.class, "id", false, null, null);
        final Pair<List<NetworkACLVO>, Integer> aclsCountPair = _networkAclDao.searchAndCount(searchCriteria, filter);

        final List<NetworkACLVO> acls = aclsCountPair.first();
        acls.forEach(networkAcl -> {
            if (networkAcl.getId() != NetworkACL.DEFAULT_ALLOW && networkAcl.getId() != NetworkACL.DEFAULT_DENY) {
                _networkAclMgr.deleteNetworkACL(networkAcl);
            }
        });

        // 6) Deleting sync networks
        final List<NetworkVO> syncNetworks = _ntwkDao.listSyncNetworksByVpc(vpcId);
        syncNetworks.forEach(syncNetwork -> _ntwkMgr.removeAndShutdownSyncNetwork(syncNetwork.getId()));

        return success;
    }

    private List<Provider> getVpcProviders(final long vpcId) {
        final List<String> providerNames = _vpcSrvcDao.getDistinctProviders(vpcId);
        final Map<String, Provider> providers = new HashMap<>();
        providerNames.forEach(providerName -> {
            if (!providers.containsKey(providerName)) {
                providers.put(providerName, Network.Provider.getProvider(providerName));
            }
        });

        return new ArrayList<>(providers.values());
    }

    private List<VpcProvider> getVpcElements() {
        if (vpcElements == null) {
            vpcElements = new ArrayList<>();
            vpcElements.add((VpcProvider) _ntwkModel.getElementImplementingProvider(Provider.VPCVirtualRouter.getName()));
        }

        if (vpcElements == null) {
            throw new CloudRuntimeException("Failed to initialize vpc elements");
        }

        return vpcElements;
    }

    @Override
    public List<PrivateGateway> getVpcPrivateGateways(final long vpcId) {
        final List<VpcGatewayVO> gateways = _vpcGatewayDao.listByVpcIdAndType(vpcId, VpcGateway.Type.Private);

        if (gateways != null) {
            final List<PrivateGateway> pvtGateway = new ArrayList<>();
            for (final VpcGatewayVO gateway : gateways) {
                pvtGateway.add(getPrivateGatewayProfile(gateway));
            }
            return pvtGateway;
        } else {
            return null;
        }
    }

    @DB
    private boolean revokeStaticRoutesForVpc(final long vpcId, final Account caller)
            throws ResourceUnavailableException {
        // get all static routes for the vpc
        final List<StaticRouteVO> routes = _staticRouteDao.listByVpcId(vpcId);
        s_logger.debug("Found " + routes.size() + " to revoke for the vpc " + vpcId);
        if (!routes.isEmpty()) {
            // mark all of them as revoke
            Transaction.execute(new TransactionCallbackNoReturn() {
                @Override
                public void doInTransactionWithoutResult(final TransactionStatus status) {
                    for (final StaticRouteVO route : routes) {
                        markStaticRouteForRevoke(route, caller);
                    }
                }
            });
            return applyStaticRoutesForVpc(vpcId);
        }

        return true;
    }

    private void markStaticRouteForRevoke(final StaticRouteVO route, final Account caller) {
        s_logger.debug("Revoking static route " + route);
        if (caller != null) {
            _accountMgr.checkAccess(caller, null, false, route);
        }

        if (route.getState() == StaticRoute.State.Staged) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Found a static route that is still in stage state so just removing it: " + route);
            }
            _staticRouteDao.remove(route.getId());
        } else if (route.getState() == StaticRoute.State.Add || route.getState() == StaticRoute.State.Active) {
            route.setState(StaticRoute.State.Revoke);
            _staticRouteDao.update(route.getId(), route);
            s_logger.debug("Marked static route " + route + " with state " + StaticRoute.State.Revoke);
        }
    }

    private PrivateGateway getPrivateGatewayProfile(final VpcGateway gateway) {
        return new PrivateGatewayProfile(gateway);
    }

    private boolean applyStaticRoutes(final List<? extends StaticRoute> routes, final Account caller, final boolean updateRoutesInDB) throws ResourceUnavailableException {
        final List<StaticRouteProfile> staticRouteProfiles = new ArrayList<>(routes.size());

        for (final StaticRoute route : routes) {
            staticRouteProfiles.add(new StaticRouteProfile(route));
        }
        if (!applyStaticRoutes(staticRouteProfiles)) {
            s_logger.warn("Routes are not completely applied");
            return false;
        } else {
            if (updateRoutesInDB) {
                for (final StaticRoute route : routes) {
                    if (route.getState() == StaticRoute.State.Revoke) {
                        _staticRouteDao.remove(route.getId());
                        s_logger.debug("Removed route " + route + " from the DB");
                    } else if (route.getState() == StaticRoute.State.Add) {
                        final StaticRouteVO ruleVO = _staticRouteDao.findById(route.getId());
                        ruleVO.setState(StaticRoute.State.Active);
                        _staticRouteDao.update(ruleVO.getId(), ruleVO);
                        s_logger.debug("Marked route " + route + " with state " + StaticRoute.State.Active);
                    }
                }
            }
        }

        return true;
    }

    private boolean applyStaticRoutes(final List<StaticRouteProfile> routes) throws ResourceUnavailableException {
        if (routes.isEmpty()) {
            s_logger.debug("No static routes to apply");
            return true;
        }
        final Vpc vpc = _vpcDao.findById(routes.get(0).getVpcId());

        s_logger.debug("Applying static routes for vpc " + vpc);
        final List<Provider> providersToImplement = getVpcProviders(vpc.getId());

        for (final VpcProvider element : getVpcElements()) {
            if (providersToImplement.contains(element.getProvider())) {
                if (element.applyStaticRoutes(vpc, routes)) {
                    s_logger.debug("Applied static routes for vpc " + vpc);
                    return true;
                }
            }
        }

        s_logger.warn("Failed to apply static routes for vpc " + vpc);
        return false;
    }

    @Inject
    public void setVpcElements(final List<VpcProvider> vpcElements) {
        this.vpcElements = vpcElements;
    }

    protected class VpcCleanupTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            try {
                final GlobalLock lock = GlobalLock.getInternLock("VpcCleanup");
                if (lock == null) {
                    s_logger.debug("Couldn't get the global lock");
                    return;
                }

                if (!lock.lock(30)) {
                    s_logger.debug("Couldn't lock the db");
                    return;
                }

                try {
                    // Cleanup inactive VPCs
                    final List<VpcVO> inactiveVpcs = _vpcDao.listInactiveVpcs();
                    if (inactiveVpcs != null) {
                        s_logger.info("Found " + inactiveVpcs.size() + " removed VPCs to cleanup");
                        for (final VpcVO vpc : inactiveVpcs) {
                            s_logger.debug("Cleaning up " + vpc);
                            destroyVpc(vpc, _accountMgr.getAccount(Account.ACCOUNT_ID_SYSTEM), User.UID_SYSTEM);
                        }
                    }
                } catch (final Exception e) {
                    s_logger.error("Exception ", e);
                } finally {
                    lock.unlock();
                }
            } catch (final Exception e) {
                s_logger.error("Exception ", e);
            }
        }
    }

    @Override
    public void unassignIPFromVpcNetwork(final long ipId, final long networkId) {
        final IPAddressVO ip = _ipAddressDao.findById(ipId);
        if (isIpAllocatedToVpc(ip)) {
            return;
        }

        if (ip == null || ip.getVpcId() == null) {
            return;
        }

        s_logger.debug("Releasing VPC ip address " + ip + " from vpc network id=" + networkId);

        final long vpcId = ip.getVpcId();
        final boolean success;
        try {
            // unassign ip from the VPC router
            success = _ipAddrMgr.applyIpAssociations(_ntwkModel.getNetwork(networkId), true);
        } catch (final ResourceUnavailableException ex) {
            throw new CloudRuntimeException("Failed to apply ip associations for network id=" + networkId
                    + " as a part of unassigning ip " + ipId + " from vpc", ex);
        }

        if (success) {
            ip.setAssociatedWithNetworkId(null);
            _ipAddressDao.update(ipId, ip);
            s_logger.debug("IP address " + ip + " is no longer associated with the network inside vpc id=" + vpcId);
        } else {
            throw new CloudRuntimeException("Failed to apply ip associations for network id=" + networkId
                    + " as a part of unassigning ip " + ipId + " from vpc");
        }
        s_logger.debug("Successfully released VPC ip address " + ip + " back to VPC pool ");
    }

    @Override
    public boolean isIpAllocatedToVpc(final IpAddress ip) {
        return ip != null && ip.getVpcId() != null && (ip.isOneToOneNat() || !_firewallDao.listByIp(ip.getId()).isEmpty());
    }

    @DB
    @Override
    public Network createVpcGuestNetwork(final long ntwkOffId, final String name, final String displayText, final String gateway, final String cidr, final String vlanId,
                                         String networkDomain, final Account owner, final Long domainId, final PhysicalNetwork pNtwk, final long zoneId, final ACLType aclType,
                                         final Boolean subdomainAccess, final long vpcId, final Long aclId, final Account caller, final Boolean isDisplayNetworkEnabled,
                                         final String dns1, final String dns2, final String ipExclusionList)
            throws ConcurrentOperationException, InsufficientCapacityException,
            ResourceAllocationException {

        final Vpc vpc = getActiveVpc(vpcId);

        if (vpc == null) {
            final InvalidParameterValueException ex = new InvalidParameterValueException("Unable to find Enabled VPC ");
            ex.addProxyObject(String.valueOf(vpcId), "VPC");
            throw ex;
        }
        _accountMgr.checkAccess(caller, null, false, vpc);

        if (networkDomain == null) {
            networkDomain = vpc.getNetworkDomain();
        }

        // 1) Validate if network can be created for VPC
        validateNtwkOffForNtwkInVpc(null, ntwkOffId, cidr, networkDomain, vpc, gateway, owner, aclId);

        // 2) Create network
        final Network guestNetwork = _ntwkMgr.createGuestNetwork(ntwkOffId, name, displayText, gateway, cidr, vlanId,
                networkDomain, owner, domainId, pNtwk, zoneId, aclType, subdomainAccess, vpcId, null, null,
                isDisplayNetworkEnabled, null, dns1, dns2, ipExclusionList);

        if (guestNetwork != null) {
            guestNetwork.setNetworkACLId(aclId);
            _ntwkDao.update(guestNetwork.getId(), (NetworkVO) guestNetwork);
        }
        return guestNetwork;
    }

    @Override
    public PublicIp assignSourceNatIpAddressToVpc(final Account owner, final Vpc vpc)
            throws InsufficientAddressCapacityException, ConcurrentOperationException {
        final long dcId = vpc.getZoneId();

        final IPAddressVO sourceNatIp = getExistingSourceNatInVpc(owner.getId(), vpc.getId());

        final PublicIp ipToReturn;

        if (sourceNatIp != null) {
            ipToReturn = PublicIp.createFromAddrAndVlan(sourceNatIp, _vlanDao.findById(sourceNatIp.getVlanId()));
        } else {
            ipToReturn = _ipAddrMgr.assignDedicateIpAddress(owner, null, vpc.getId(), dcId, true);
        }

        return ipToReturn;
    }

    @Override
    public List<HypervisorType> getSupportedVpcHypervisors() {
        return Collections.unmodifiableList(hTypes);
    }
}
