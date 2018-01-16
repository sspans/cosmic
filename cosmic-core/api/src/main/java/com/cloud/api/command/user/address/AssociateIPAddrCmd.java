package com.cloud.api.command.user.address;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.api.response.NetworkResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.context.CallContext;
import com.cloud.dc.DataCenter;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.model.enumeration.NetworkType;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.vpc.Vpc;
import com.cloud.offering.NetworkOffering;
import com.cloud.projects.Project;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "associateIpAddress", group = "Public IP Address", description = "Acquires and associates a public IP to an account.", responseObject = IPAddressResponse.class, responseView =
        ResponseView.Restricted,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class AssociateIPAddrCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(AssociateIPAddrCmd.class.getName());
    private static final String s_name = "associateipaddressresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "the account to associate with this IP address")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "the ID of the domain to associate with this IP address")
    private Long domainId;

    @Parameter(name = ApiConstants.ZONE_ID,
            type = CommandType.UUID,
            entityType = ZoneResponse.class,
            description = "the ID of the availability zone you want to acquire an public IP address from")
    private Long zoneId;

    @Parameter(name = ApiConstants.NETWORK_ID,
            type = CommandType.UUID,
            entityType = NetworkResponse.class,
            description = "The network this IP address should be associated to.")
    private Long networkId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Deploy VM for the project")
    private Long projectId;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class, description = "the VPC you want the IP address to "
            + "be associated with")
    private Long vpcId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the IP to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public static String getResultObjectName() {
        return "addressinfo";
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_NET_IP_ASSIGN;
    }

    @Override
    public String getEventDescription() {
        return "associating IP to network ID: " + getNetworkId() + " in zone " + getZoneId();
    }

    public Long getNetworkId() {
        if (vpcId != null) {
            return null;
        }

        if (networkId != null) {
            return networkId;
        }
        final Long zoneId = getZoneId();

        final DataCenter zone = _entityMgr.findById(DataCenter.class, zoneId);
        if (zone.getNetworkType() == NetworkType.Advanced) {
            final List<? extends Network> networks = _networkService.getIsolatedNetworksOwnedByAccountInZone(getZoneId(), _accountService.getAccount(getEntityOwnerId()));
            if (networks.size() == 0) {
                final String domain = _domainService.getDomain(getDomainId()).getName();
                throw new InvalidParameterValueException("Account name=" + getAccountName() + " domain=" + domain + " doesn't have virtual networks in zone=" +
                        zone.getName());
            }

            if (networks.size() < 1) {
                throw new InvalidParameterValueException("Account doesn't have any isolated networks in the zone");
            } else if (networks.size() > 1) {
                throw new InvalidParameterValueException("Account has more than one isolated network in the zone");
            }

            return networks.get(0).getId();
        } else {
            final Network defaultGuestNetwork = _networkService.getExclusiveGuestNetwork(zoneId);
            if (defaultGuestNetwork == null) {
                throw new InvalidParameterValueException("Unable to find a default guest network for account " + getAccountName() + " in domain ID=" + getDomainId());
            } else {
                return defaultGuestNetwork.getId();
            }
        }
    }

    private long getZoneId() {
        if (zoneId != null) {
            return zoneId;
        } else if (vpcId != null) {
            final Vpc vpc = _entityMgr.findById(Vpc.class, vpcId);
            if (vpc != null) {
                return vpc.getZoneId();
            }
        } else if (networkId != null) {
            final Network ntwk = _entityMgr.findById(Network.class, networkId);
            if (ntwk != null) {
                return ntwk.getDataCenterId();
            }
        }

        throw new InvalidParameterValueException("Unable to figure out zone to assign IP to."
                + " Please specify either zoneId, or networkId, or vpcId in the call");
    }

    public long getDomainId() {
        if (domainId != null) {
            return domainId;
        }
        return CallContext.current().getCallingAccount().getDomainId();
    }

    public String getAccountName() {
        if (accountName != null) {
            return accountName;
        }
        return CallContext.current().getCallingAccount().getAccountName();
    }

    public Long getVpcId() {
        return vpcId;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.IpAddress;
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.networkSyncObject;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public Long getSyncObjId() {
        return getNetworkId();
    }

    @Override
    public void create() throws ResourceAllocationException {
        try {
            IpAddress ip = null;

            ip = _networkService.allocateIP(_accountService.getAccount(getEntityOwnerId()), getZoneId(), getNetworkId(), getDisplayIp());

            if (ip != null) {
                setEntityId(ip.getId());
                setEntityUuid(ip.getUuid());
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to allocate IP address");
            }
        } catch (final ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        } catch (final InsufficientAddressCapacityException ex) {
            s_logger.info(ex.toString());
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
        }
    }

    @Deprecated
    public Boolean getDisplayIp() {
        return display;
    }

    @Override
    public void execute() throws ResourceUnavailableException, ResourceAllocationException, ConcurrentOperationException, InsufficientCapacityException {
        CallContext.current().setEventDetails("IP ID: " + getEntityId());

        IpAddress result = null;

        if (getVpcId() != null) {
            result = _vpcService.associateIPToVpc(getEntityId(), getVpcId());
        } else if (getNetworkId() != null) {
            result = _networkService.associateIPToNetwork(getEntityId(), getNetworkId());
        }

        if (result != null) {
            final IPAddressResponse ipResponse = _responseGenerator.createIPAddressResponse(ResponseView.Restricted, result);
            ipResponse.setResponseName(getCommandName());
            setResponseObject(ipResponse);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to assign IP address");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account caller = CallContext.current().getCallingAccount();
        if (accountName != null && domainId != null) {
            final Account account = _accountService.finalizeOwner(caller, accountName, domainId, projectId);
            return account.getId();
        } else if (projectId != null) {
            final Project project = _projectService.getProject(projectId);
            if (project != null) {
                if (project.getState() == Project.State.Active) {
                    return project.getProjectAccountId();
                } else {
                    throw new PermissionDeniedException("Can't add resources to the project with specified projectId in state=" + project.getState() +
                            " as it's no longer active");
                }
            } else {
                throw new InvalidParameterValueException("Unable to find project by ID");
            }
        } else if (networkId != null) {
            final Network network = _networkService.getNetwork(networkId);
            if (network == null) {
                throw new InvalidParameterValueException("Unable to find network by network id specified");
            }

            final NetworkOffering offering = _entityMgr.findById(NetworkOffering.class, network.getNetworkOfferingId());

            final DataCenter zone = _entityMgr.findById(DataCenter.class, network.getDataCenterId());
            if (zone.getNetworkType() == NetworkType.Basic && offering.getElasticIp() && offering.getElasticLb()) {
                // Since the basic zone network is owned by 'Root' domain, domain access checkers will fail for the
                // accounts in non-root domains while acquiring public IP. So add an exception for the 'Basic' zone
                // shared network with EIP/ELB service.
                return caller.getAccountId();
            }

            return network.getAccountId();
        } else if (vpcId != null) {
            final Vpc vpc = _entityMgr.findById(Vpc.class, getVpcId());
            if (vpc == null) {
                throw new InvalidParameterValueException("Can't find enabled VPC by ID specified");
            }
            return vpc.getAccountId();
        }

        return caller.getAccountId();
    }

    @Override
    public boolean isDisplay() {
        if (display == null) {
            return true;
        } else {
            return display;
        }
    }
}
