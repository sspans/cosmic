package com.cloud.api.command.user.vpc;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.VpcOfferingResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.vpc.Vpc;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createVPC", group = "VPC", description = "Creates a VPC", responseObject = VpcResponse.class, responseView = ResponseView.Restricted, entityType = {Vpc.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateVPCCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateVPCCmd.class.getName());
    private static final String s_name = "createvpcresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "the account associated with the VPC. " +
            "Must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
            description = "the domain ID associated with the VPC. " +
                    "If used with the account parameter returns the VPC associated with the account for the specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class,
            description = "create VPC for the project")
    private Long projectId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class,
            required = true, description = "the ID of the availability zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the VPC")
    private String vpcName;

    @Parameter(name = ApiConstants.DISPLAY_TEXT, type = CommandType.STRING, required = true, description = "the display text of " +
            "the VPC")
    private String displayText;

    @Parameter(name = ApiConstants.CIDR, type = CommandType.STRING, required = true, description = "the cidr of the VPC. All VPC " +
            "guest networks' cidrs should be within this CIDR")
    private String cidr;

    @Parameter(name = ApiConstants.VPC_OFF_ID, type = CommandType.UUID, entityType = VpcOfferingResponse.class,
            required = true, description = "the ID of the VPC offering")
    private Long vpcOffering;

    @Parameter(name = ApiConstants.NETWORK_DOMAIN, type = CommandType.STRING,
            description = "VPC network domain. All networks inside the VPC will belong to this domain")
    private String networkDomain;

    @Parameter(name = ApiConstants.START, type = CommandType.BOOLEAN,
            description = "If set to false, the VPC won't start (VPC VR will not get allocated) until its first network gets implemented. " +
                    "True by default.", since = "4.3")
    private Boolean start;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the vpc to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    @Parameter(name = ApiConstants.SOURCE_NAT_LIST, type = CommandType.STRING,
            description = "Source NAT CIDR list for used to allow other CIDRs to be source NATted by the VPC over the public interface")
    private String sourceNatList;

    @Parameter(name = ApiConstants.SYSLOG_SERVER_LIST, type = CommandType.STRING,
            description = "Comma separated list of IP addresses to configure as syslog servers on the VPC to forward IP tables logging")
    private String syslogServerList;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    @Override
    public void create() throws ResourceAllocationException {
        final Vpc vpc = _vpcService.createVpc(getZoneId(), getVpcOffering(), getEntityOwnerId(), getVpcName(), getDisplayText(), getCidr(), getNetworkDomain(), getDisplayVpc(), getSourceNatList(), getSyslogServerList());
        if (vpc != null) {
            setEntityId(vpc.getId());
            setEntityUuid(vpc.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create a VPC");
        }
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getVpcOffering() {
        return vpcOffering;
    }

    public String getVpcName() {
        return vpcName;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getCidr() {
        return cidr;
    }

    public String getNetworkDomain() {
        return networkDomain;
    }

    public Boolean getDisplayVpc() {
        return display;
    }

    public String getSourceNatList() {
        if (StringUtils.isEmpty(sourceNatList)) {
            return sourceNatList;
        }
        return sourceNatList.replaceAll("\\s", "");
    }

    public String getSyslogServerList() {
        if (StringUtils.isEmpty(syslogServerList)) {
            return syslogServerList;
        }
        return syslogServerList.replaceAll("\\s", "");
    }

    @Override
    public void execute() {
        Vpc vpc = null;
        try {
            if (isStart()) {
                _vpcService.startVpc(getEntityId(), true);
            } else {
                s_logger.debug("Not starting VPC as " + ApiConstants.START + "=false was passed to the API");
            }
            vpc = _entityMgr.findById(Vpc.class, getEntityId());
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

        if (vpc != null) {
            final VpcResponse response = _responseGenerator.createVpcResponse(ResponseView.Restricted, vpc);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create VPC");
        }
    }

    public boolean isStart() {
        if (start != null) {
            return start;
        }
        return true;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Long accountId = _accountService.finalyzeAccountId(accountName, domainId, projectId, true);
        if (accountId == null) {
            return CallContext.current().getCallingAccount().getId();
        }

        return accountId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VPC_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating VPC. Id: " + getEntityId();
    }
}
