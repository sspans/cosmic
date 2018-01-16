package com.cloud.api.command.admin.usage;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.PhysicalNetworkResponse;
import com.cloud.api.response.TrafficTypeResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.network.PhysicalNetworkTrafficType;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "addTrafficType", group = "Usage", description = "Adds traffic type to a physical network", responseObject = TrafficTypeResponse.class, since = "3.0.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class AddTrafficTypeCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(AddTrafficTypeCmd.class.getName());

    private static final String s_name = "addtraffictyperesponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            required = true,
            description = "the Physical Network ID")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.TRAFFIC_TYPE, type = CommandType.STRING, required = true, description = "the trafficType to be added to the physical network")
    private String trafficType;

    @Parameter(name = ApiConstants.XENSERVER_NETWORK_LABEL,
            type = CommandType.STRING,
            description = "The network name label of the physical device dedicated to this traffic on a XenServer host")
    private String xenLabel;

    @Parameter(name = ApiConstants.KVM_NETWORK_LABEL,
            type = CommandType.STRING,
            description = "The network name label of the physical device dedicated to this traffic on a KVM host")
    private String kvmLabel;

    @Parameter(name = ApiConstants.VLAN, type = CommandType.STRING, description = "The VLAN id to be used for Management traffic by the host")
    private String vlan;

    @Parameter(name = ApiConstants.ISOLATION_METHOD, type = CommandType.STRING, description = "Used if physical network has multiple isolation types and traffic type is public."
            + " Choose which isolation method. Valid options currently 'vlan' or 'vxlan', defaults to 'vlan'.")
    private String isolationMethod;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        CallContext.current().setEventDetails("TrafficType Id: " + getEntityId());
        final PhysicalNetworkTrafficType result = _networkService.getPhysicalNetworkTrafficType(getEntityId());
        if (result != null) {
            final TrafficTypeResponse response = _responseGenerator.createTrafficTypeResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to add traffic type to physical network");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void create() throws ResourceAllocationException {
        final PhysicalNetworkTrafficType result =
                _networkService.addTrafficTypeToPhysicalNetwork(getPhysicalNetworkId(), getTrafficType(), getIsolationMethod(), getXenLabel(), getKvmLabel(),
                        getVlan());
        if (result != null) {
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to add traffic type to physical network");
        }
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public String getTrafficType() {
        return trafficType;
    }

    public String getIsolationMethod() {
        if (isolationMethod != null && !isolationMethod.isEmpty()) {
            return isolationMethod;
        } else {
            return "vlan";
        }
    }

    public String getXenLabel() {
        return xenLabel;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getKvmLabel() {
        return kvmLabel;
    }

    public String getVlan() {
        return vlan;
    }

    public void setVlan(final String vlan) {
        this.vlan = vlan;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_TRAFFIC_TYPE_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Adding physical network traffic type: " + getEntityId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.TrafficType;
    }
}
