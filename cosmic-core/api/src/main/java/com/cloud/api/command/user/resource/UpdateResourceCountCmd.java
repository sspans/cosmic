package com.cloud.api.command.user.resource;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.ResourceCountResponse;
import com.cloud.configuration.ResourceCount;
import com.cloud.context.CallContext;
import com.cloud.user.Account;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateResourceCount", group = "Limit", description = "Recalculate and update resource count for an account or domain.", responseObject = ResourceCountResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateResourceCountCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateResourceCountCmd.class.getName());

    private static final String s_name = "updateresourcecountresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT,
            type = CommandType.STRING,
            description = "Update resource count for a specified account. Must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            required = true,
            description = "If account parameter specified then updates resource counts for a specified account in this domain else update resource counts for all accounts & " +
                    "child domains in specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.RESOURCE_TYPE,
            type = CommandType.INTEGER,
            description = "Type of resource to update. If specifies valid values are 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 and 11. If not specified will update all resource counts"
                    + "0 - Instance. Number of instances a user can create. "
                    + "1 - IP. Number of public IP addresses a user can own. "
                    + "2 - Volume. Number of disk volumes a user can create. "
                    + "3 - Snapshot. Number of snapshots a user can create. "
                    + "4 - Template. Number of templates that a user can register/create. "
                    + "5 - Project. Number of projects that a user can create. "
                    + "6 - Network. Number of guest network a user can create. "
                    + "7 - VPC. Number of VPC a user can create. "
                    + "8 - CPU. Total number of CPU cores a user can use. "
                    + "9 - Memory. Total Memory (in MB) a user can use. "
                    + "10 - PrimaryStorage. Total primary storage space (in GiB) a user can use. "
                    + "11 - SecondaryStorage. Total secondary storage space (in GiB) a user can use. ")
    private Integer resourceType;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Update resource limits for project")
    private Long projectId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
    }

    @Override
    public void execute() {
        final List<? extends ResourceCount> result =
                _resourceLimitService.recalculateResourceCount(_accountService.finalyzeAccountId(accountName, domainId, projectId, true), getDomainId(), getResourceType());

        if ((result != null) && (result.size() > 0)) {
            final ListResponse<ResourceCountResponse> response = new ListResponse<>();
            final List<ResourceCountResponse> countResponses = new ArrayList<>();

            for (final ResourceCount count : result) {
                final ResourceCountResponse resourceCountResponse = _responseGenerator.createResourceCountResponse(count);
                resourceCountResponse.setObjectName("resourcecount");
                countResponses.add(resourceCountResponse);
            }

            response.setResponses(countResponses);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to recalculate resource counts");
        }
    }

    public Long getDomainId() {
        return domainId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Integer getResourceType() {
        return resourceType;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account account = CallContext.current().getCallingAccount();
        if ((account == null) || _accountService.isAdmin(account.getId())) {
            if ((domainId != null) && (accountName != null)) {
                final Account userAccount = _responseGenerator.findAccountByNameDomain(accountName, domainId);
                if (userAccount != null) {
                    return userAccount.getId();
                }
            }
        }

        if (account != null) {
            return account.getId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }
}
