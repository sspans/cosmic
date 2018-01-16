package com.cloud.api.command.user.affinitygroup;

import com.cloud.affinity.AffinityGroup;
import com.cloud.affinity.AffinityGroupResponse;
import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createAffinityGroup", group = "Affinity", responseObject = AffinityGroupResponse.class, description = "Creates an affinity/anti-affinity group", entityType = {AffinityGroup.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateAffinityGroupCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateAffinityGroupCmd.class.getName());

    private static final String s_name = "createaffinitygroupresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an account for the affinity group. Must be used with domainId.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            description = "domainId of the account owning the affinity group",
            entityType = DomainResponse.class)
    private Long domainId;

    @Parameter(name = ApiConstants.PROJECT_ID,
            type = CommandType.UUID,
            entityType = ProjectResponse.class,
            description = "create affinity group for project")
    private Long projectId;

    @Parameter(name = ApiConstants.DESCRIPTION, type = CommandType.STRING, description = "optional description of the affinity group")
    private String description;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "name of the affinity group")
    private String affinityGroupName;

    @Parameter(name = ApiConstants.TYPE,
            type = CommandType.STRING,
            required = true,
            description = "Type of the affinity group from the available affinity/anti-affinity group types")
    private String affinityGroupType;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public String getAccountName() {
        return accountName;
    }

    public String getDescription() {
        return description;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getAffinityGroupName() {
        return affinityGroupName;
    }

    public String getAffinityGroupType() {
        return affinityGroupType;
    }

    public Long getProjectId() {
        return projectId;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public void execute() {
        final AffinityGroup group = _affinityGroupService.getAffinityGroup(getEntityId());
        if (group != null) {
            final AffinityGroupResponse response = _responseGenerator.createAffinityGroupResponse(group);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create affinity group:" + affinityGroupName);
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account caller = CallContext.current().getCallingAccount();

        //For domain wide affinity groups (if the affinity group processor type allows it)
        if (projectId == null && domainId != null && accountName == null && _accountService.isRootAdmin(caller.getId())) {
            return Account.ACCOUNT_ID_SYSTEM;
        }
        final Account owner = _accountService.finalizeOwner(caller, accountName, domainId, projectId);
        if (owner == null) {
            return caller.getAccountId();
        }
        return owner.getAccountId();
    }

    @Override
    public void create() throws ResourceAllocationException {
        final AffinityGroup result = _affinityGroupService.createAffinityGroup(this);
        if (result != null) {
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create affinity group entity" + affinityGroupName);
        }
    }

    @Override
    public String getCreateEventType() {
        return EventTypes.EVENT_AFFINITY_GROUP_CREATE;
    }

    @Override
    public String getCreateEventDescription() {
        return "creating Affinity Group";
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_AFFINITY_GROUP_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating Affinity Group";
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.AffinityGroup;
    }
}
