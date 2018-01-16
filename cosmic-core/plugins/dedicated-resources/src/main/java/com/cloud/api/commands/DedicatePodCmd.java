package com.cloud.api.commands;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DedicatePodResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.PodResponse;
import com.cloud.dc.DedicatedResources;
import com.cloud.dedicated.DedicatedService;
import com.cloud.event.EventTypes;
import com.cloud.user.Account;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "dedicatePod", group = "Affinity", description = "Dedicates a Pod.", responseObject = DedicatePodResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DedicatePodCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(DedicatePodCmd.class.getName());

    private static final String s_name = "dedicatepodresponse";
    @Inject
    public DedicatedService dedicatedService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.POD_ID, type = CommandType.UUID, entityType = PodResponse.class, required = true, description = "the ID of the Pod")
    private Long podId;

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
        final List<? extends DedicatedResources> result = dedicatedService.dedicatePod(getPodId(), getDomainId(), getAccountName());
        final ListResponse<DedicatePodResponse> response = new ListResponse<>();
        final List<DedicatePodResponse> podResponseList = new ArrayList<>();
        if (result != null) {
            for (final DedicatedResources resource : result) {
                final DedicatePodResponse podresponse = dedicatedService.createDedicatePodResponse(resource);
                podResponseList.add(podresponse);
            }
            response.setResponses(podResponseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to dedicate pod");
        }
    }

    public Long getPodId() {
        return podId;
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
        return "dedicating a pod";
    }
}
