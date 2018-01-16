package com.cloud.api.command.user.resource;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.ResourceLimitResponse;
import com.cloud.configuration.ResourceLimit;
import com.cloud.context.CallContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateResourceLimit", group = "Limit", description = "Updates resource limits for an account or domain.", responseObject = ResourceLimitResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateResourceLimitCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateResourceLimitCmd.class.getName());

    private static final String s_name = "updateresourcelimitresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "Update resource for a specified account. Must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "Update resource limits for all accounts in specified domain. If used with the account parameter, updates resource limits for a specified account in " +
                    "specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Update resource limits for project")
    private Long projectId;

    @Parameter(name = ApiConstants.MAX, type = CommandType.LONG, description = "  Maximum resource limit.")
    private Long max;

    @Parameter(name = ApiConstants.RESOURCE_TYPE,
            type = CommandType.INTEGER,
            required = true,
            description = "Type of resource to update. Values are 0, 1, 2, 3, 4, 6, 7, 8, 9, 10 and 11. "
                    + "0 - Instance. Number of instances a user can create. "
                    + "1 - IP. Number of public IP addresses a user can own. "
                    + "2 - Volume. Number of disk volumes a user can create. "
                    + "3 - Snapshot. Number of snapshots a user can create. "
                    + "4 - Template. Number of templates that a user can register/create. "
                    + "6 - Network. Number of guest network a user can create. "
                    + "7 - VPC. Number of VPC a user can create. "
                    + "8 - CPU. Total number of CPU cores a user can use. "
                    + "9 - Memory. Total Memory (in MB) a user can use. "
                    + "10 - PrimaryStorage. Total primary storage space (in GiB) a user can use. "
                    + "11 - SecondaryStorage. Total secondary storage space (in GiB) a user can use. ")
    private Integer resourceType;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getMax() {
        return max;
    }

    public Integer getResourceType() {
        return resourceType;
    }

    @Override
    public void execute() {
        final ResourceLimit result = _resourceLimitService.updateResourceLimit(_accountService.finalyzeAccountId(accountName, domainId, projectId, true), getDomainId(),
                resourceType,
                max);
        if (result != null || (result == null && max != null && max.longValue() == -1L)) {
            final ResourceLimitResponse response = _responseGenerator.createResourceLimitResponse(result);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update resource limit");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Long getDomainId() {
        return domainId;
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
}
