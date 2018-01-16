package com.cloud.api.command.user.address;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseAsyncCustomIdCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.AccountResponse;
import com.cloud.api.response.IPAddressResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.IpAddress;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateIpAddress", group = "Public IP Address", description = "Updates an IP address", responseObject = IPAddressResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, entityType = {IpAddress.class})
public class UpdateIPAddrCmd extends BaseAsyncCustomIdCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateIPAddrCmd.class.getName());
    private static final String s_name = "updateipaddressresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = IPAddressResponse.class, required = true, description = "the ID of the public IP address"
            + " to update")
    private Long id;
    // unexposed parameter needed for events logging
    @Parameter(name = ApiConstants.ACCOUNT_ID, type = CommandType.UUID, entityType = AccountResponse.class, expose = false)
    private Long ownerId;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "an optional field, whether to the display the IP to the end user or not", since = "4" +
            ".4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getEventType() {
        return EventTypes.EVENT_NET_IP_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return ("Updating IP address with ID=" + id);
    }

    @Override
    public void checkUuid() {
        if (getCustomId() != null) {
            _uuidMgr.checkUuid(getCustomId(), IpAddress.class);
        }
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException,
            NetworkRuleConflictException {

        final IpAddress result = _networkService.updateIP(getId(), getCustomId(), getDisplayIp());
        if (result != null) {
            final IPAddressResponse ipResponse = _responseGenerator.createIPAddressResponse(ResponseView.Restricted, result);
            ipResponse.setResponseName(getCommandName());
            setResponseObject(ipResponse);
        }
    }

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        if (ownerId == null) {
            final IpAddress ip = getIpAddress(id);
            if (ip == null) {
                throw new InvalidParameterValueException("Unable to find IP address by ID=" + id);
            }
            ownerId = ip.getAccountId();
        }

        if (ownerId == null) {
            return Account.ACCOUNT_ID_SYSTEM;
        }
        return ownerId;
    }

    private IpAddress getIpAddress(final long id) {
        final IpAddress ip = _entityMgr.findById(IpAddress.class, id);

        if (ip == null) {
            throw new InvalidParameterValueException("Unable to find IP address by ID=" + id);
        } else {
            return ip;
        }
    }

    public Long getId() {
        return id;
    }

    public Boolean getDisplayIp() {
        return display;
    }
}
