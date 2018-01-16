package com.cloud.api.command.user.iso;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.GuestOSResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.TemplateResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.context.CallContext;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.template.VirtualMachineTemplate;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "registerIso", group = "ISO", responseObject = TemplateResponse.class, description = "Registers an existing ISO into the CloudStack Cloud.", responseView = ResponseView
        .Restricted,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class RegisterIsoCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(RegisterIsoCmd.class.getName());

    private static final String s_name = "registerisoresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class,
            required = true, description = "the ID of the zone you wish to register the ISO to.")
    protected Long zoneId;
    @Parameter(name = ApiConstants.IS_DYNAMICALLY_SCALABLE,
            type = CommandType.BOOLEAN,
            description = "true if ISO contains XS tools inorder to support dynamic scaling of VM CPU/memory")
    protected Boolean isDynamicallyScalable;
    @Parameter(name = ApiConstants.BOOTABLE, type = CommandType.BOOLEAN, description = "true if this ISO is bootable. If not passed explicitly its assumed to be true")
    private Boolean bootable;
    @Parameter(name = ApiConstants.DISPLAY_TEXT,
            type = CommandType.STRING,
            required = true,
            description = "the display text of the ISO. This is usually used for display purposes.",
            length = 4096)
    private String displayText;
    @Parameter(name = ApiConstants.IS_FEATURED, type = CommandType.BOOLEAN, description = "true if you want this ISO to be featured")
    private Boolean featured;
    @Parameter(name = ApiConstants.IS_PUBLIC,
            type = CommandType.BOOLEAN,
            description = "true if you want to register the ISO to be publicly available to all users, false otherwise.")
    private Boolean publicIso;
    @Parameter(name = ApiConstants.IS_EXTRACTABLE, type = CommandType.BOOLEAN, description = "true if the ISO or its derivatives are extractable; default is false")
    private Boolean extractable;
    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the ISO")
    private String isoName;
    @Parameter(name = ApiConstants.OS_TYPE_ID,
            type = CommandType.UUID,
            entityType = GuestOSResponse.class,
            description = "the ID of the OS type that best represents the OS of this ISO. If the ISO is bootable this parameter needs to be passed")
    private Long osTypeId;
    @Parameter(name = ApiConstants.URL, type = CommandType.STRING, required = true, length = 2048, description = "the URL to where the ISO is currently being hosted")
    private String url;
    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "an optional domainId. If the account parameter is used, domainId must also be used.")
    private Long domainId;
    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional account name. Must be used with domainId.")
    private String accountName;
    @Parameter(name = ApiConstants.CHECKSUM, type = CommandType.STRING, description = "the MD5 checksum value of this ISO")
    private String checksum;
    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "Register ISO for the project")
    private Long projectId;
    @Parameter(name = ApiConstants.IMAGE_STORE_UUID, type = CommandType.STRING, description = "Image store UUID")
    private String imageStoreUuid;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Boolean isBootable() {
        return bootable;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Boolean isFeatured() {
        return featured;
    }

    public Boolean isPublic() {
        return publicIso;
    }

    public Boolean isExtractable() {
        return extractable;
    }

    public String getIsoName() {
        return isoName;
    }

    public Long getOsTypeId() {
        return osTypeId;
    }

    public String getUrl() {
        return url;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getImageStoreUuid() {
        return imageStoreUuid;
    }

    public Boolean isDynamicallyScalable() {
        return isDynamicallyScalable == null ? Boolean.FALSE : isDynamicallyScalable;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() throws ResourceAllocationException {
        final VirtualMachineTemplate template = _templateService.registerIso(this);
        if (template != null) {
            final ListResponse<TemplateResponse> response = new ListResponse<>();
            final List<TemplateResponse> templateResponses = _responseGenerator.createIsoResponses(ResponseView.Restricted, template, zoneId, false);
            response.setResponses(templateResponses);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to register ISO");
        }
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
