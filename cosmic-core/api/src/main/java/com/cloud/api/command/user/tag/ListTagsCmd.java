package com.cloud.api.command.user.tag;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListProjectAndAccountResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ResourceTagResponse;
import com.cloud.server.ResourceTag;

@APICommand(name = "listTags", group = "Resource tags", description = "List resource tag(s)", responseObject = ResourceTagResponse.class, since = "4.0.0", entityType = {ResourceTag.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListTagsCmd extends BaseListProjectAndAccountResourcesCmd {
    private static final String s_name = "listtagsresponse";

    @Parameter(name = ApiConstants.RESOURCE_TYPE, type = CommandType.STRING, description = "list by resource type")
    private String resourceType;

    @Parameter(name = ApiConstants.RESOURCE_ID, type = CommandType.STRING, description = "list by resource id")
    private String resourceId;

    @Parameter(name = ApiConstants.KEY, type = CommandType.STRING, description = "list by key")
    private String key;

    @Parameter(name = ApiConstants.VALUE, type = CommandType.STRING, description = "list by value")
    private String value;

    @Parameter(name = ApiConstants.CUSTOMER, type = CommandType.STRING, description = "list by customer name")
    private String customer;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {

        final ListResponse<ResourceTagResponse> response = _queryService.listTags(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getCustomer() {
        return customer;
    }
}
