package com.cloud.api.command.user.vpn;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.Site2SiteVpnGatewayResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.Site2SiteVpnGateway;
import com.cloud.network.vpc.Vpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createVpnGateway", group = "VPN", description = "Creates site to site vpn local gateway", responseObject = Site2SiteVpnGatewayResponse.class, entityType =
        {Site2SiteVpnGateway.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateVpnGatewayCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateVpnGatewayCmd.class.getName());

    private static final String s_name = "createvpngatewayresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.VPC_ID,
            type = CommandType.UUID,
            entityType = VpcResponse.class,
            required = true,
            description = "public ip address id of the vpn gateway")
    private Long vpcId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the vpn to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Deprecated
    public Boolean getDisplay() {
        return display;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_S2S_VPN_GATEWAY_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Create site-to-site VPN gateway for account " + getEntityOwnerId();
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.vpcSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        return getVpcId();
    }

    public Long getVpcId() {
        return vpcId;
    }

    @Override
    public void execute() {
        CallContext.current().setEventDetails("VPN gateway Id: " + getEntityId());
        final Site2SiteVpnGateway result = _s2sVpnService.getVpnGateway(getEntityId());
        if (result != null) {
            final Site2SiteVpnGatewayResponse response = _responseGenerator.createSite2SiteVpnGatewayResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create VPN gateway");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Vpc vpc = _entityMgr.findById(Vpc.class, vpcId);
        return vpc.getAccountId();
    }

    @Override
    public boolean isDisplay() {
        if (display != null) {
            return display;
        } else {
            return true;
        }
    }

    @Override
    public void create() throws ResourceAllocationException {
        final Site2SiteVpnGateway result = _s2sVpnService.createVpnGateway(this);
        if (result != null) {
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create VPN gateway");
        }
    }
}
