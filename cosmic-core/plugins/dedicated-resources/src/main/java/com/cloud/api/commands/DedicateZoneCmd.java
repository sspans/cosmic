package com.cloud.api.commands;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DedicateZoneResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.dc.DedicatedResources;
import com.cloud.dedicated.DedicatedService;
import com.cloud.event.EventTypes;
import com.cloud.user.Account;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "dedicateZone", group = "Affinity", description = "Dedicates a zones.", responseObject = DedicateZoneResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DedicateZoneCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(DedicateZoneCmd.class.getName());

    private static final String s_name = "dedicatezoneresponse";
    @Inject
    public DedicatedService dedicatedService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, required = true, description = "the ID of the zone")
    private Long zoneId;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            required = true,
            description = "the ID of the containing domain")
    private Long domainId;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "the name of the account which needs dedication. Must be used with domainId.")
    private String accountName;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final List<? extends DedicatedResources> result = dedicatedService.dedicateZone(getZoneId(), getDomainId(), getAccountName());
        final ListResponse<DedicateZoneResponse> response = new ListResponse<>();
        final List<DedicateZoneResponse> zoneResponseList = new ArrayList<>();
        if (result != null) {
            for (final DedicatedResources resource : result) {
                final DedicateZoneResponse zoneresponse = dedicatedService.createDedicateZoneResponse(resource);
                zoneResponseList.add(zoneresponse);
            }
            response.setResponses(zoneResponseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to dedicate zone");
        }
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getDomainId() {
        return domainId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
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
    public String getEventType() {
        return EventTypes.EVENT_DEDICATE_RESOURCE;
    }

    @Override
    public String getEventDescription() {
        return "dedicating a zone";
    }
}
