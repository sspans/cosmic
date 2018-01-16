package com.cloud.api.command.admin.region;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.RegionResponse;
import com.cloud.region.Region;
import com.cloud.region.RegionService;
import com.cloud.user.Account;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "updateRegion", group = "Region", description = "Updates a region", responseObject = RegionResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class UpdateRegionCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(UpdateRegionCmd.class.getName());
    private static final String s_name = "updateregionresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Inject
    RegionService _regionService;
    @Parameter(name = ApiConstants.ID, type = CommandType.INTEGER, required = true, description = "Id of region to update")
    private Integer id;
    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "updates region with this name")
    private String regionName;
    @Parameter(name = ApiConstants.END_POINT, type = CommandType.STRING, description = "updates region with this end point")
    private String endPoint;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final Region region = _regionService.updateRegion(getId(), getRegionName(), getEndPoint());
        if (region != null) {
            final RegionResponse response = _responseGenerator.createRegionResponse(region);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update Region");
        }
    }

    public Integer getId() {
        return id;
    }

    public String getRegionName() {
        return regionName;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getEndPoint() {
        return endPoint;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
