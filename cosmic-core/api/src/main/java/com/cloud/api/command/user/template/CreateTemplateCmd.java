package com.cloud.api.command.user.template;

import com.cloud.acl.SecurityChecker;
import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.GuestOSResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.api.response.SnapshotResponse;
import com.cloud.api.response.TemplateResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.api.response.VolumeResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.projects.Project;
import com.cloud.storage.Snapshot;
import com.cloud.storage.Volume;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createTemplate", group = "Template", responseObject = TemplateResponse.class, description = "Creates a template of a virtual machine. " + "The virtual machine must be in a " +
        "STOPPED state. "
        + "A template created from this command is automatically designated as a private template visible to the account that created it.", responseView = ResponseView.Restricted,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateTemplateCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateTemplateCmd.class.getName());
    private static final String s_name = "createtemplateresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////
    @Parameter(name = ApiConstants.SNAPSHOT_ID,
            type = CommandType.UUID,
            entityType = SnapshotResponse.class,
            description = "the ID of the snapshot the template is being created from. Either this parameter, or volumeId has to be passed in")
    protected Long snapshotId;
    @Parameter(name = ApiConstants.VOLUME_ID,
            type = CommandType.UUID,
            entityType = VolumeResponse.class,
            description = "the ID of the disk volume the template is being created from. Either this parameter, or snapshotId has to be passed in")
    protected Long volumeId;
    @Deprecated
    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID, type = CommandType.UUID, entityType = UserVmResponse.class,
            description = "DEPRECATED SINCE 5.1.0: Optional, VM ID. If this presents, it is going to create a baremetal template for VM this ID refers to. This is only for VM " +
                    "whose hypervisor type is BareMetal")
    protected Long vmId;
    @Parameter(name = ApiConstants.DETAILS, type = CommandType.MAP, description = "Template details in key/value pairs using format details[i].keyname=keyvalue. Example: " +
            "details[0].hypervisortoolsversion=xenserver61")
    protected Map details;
    @Parameter(name = ApiConstants.IS_DYNAMICALLY_SCALABLE,
            type = CommandType.BOOLEAN,
            description = "true if template contains XS tools inorder to support dynamic scaling of VM cpu/memory")
    protected Boolean isDynamicallyScalable;
    @Parameter(name = ApiConstants.BITS, type = CommandType.INTEGER, description = "32 or 64 bit")
    private Integer bits;
    @Parameter(name = ApiConstants.DISPLAY_TEXT,
            type = CommandType.STRING,
            required = true,
            description = "the display text of the template. This is usually used for display purposes.",
            length = 4096)
    private String displayText;
    @Parameter(name = ApiConstants.IS_FEATURED, type = CommandType.BOOLEAN, description = "true if this template is a featured template, false otherwise")
    private Boolean featured;
    @Parameter(name = ApiConstants.IS_PUBLIC, type = CommandType.BOOLEAN, description = "true if this template is a public template, false otherwise")
    private Boolean publicTemplate;
    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "the name of the template")
    private String templateName;
    @Parameter(name = ApiConstants.OS_TYPE_ID,
            type = CommandType.UUID,
            entityType = GuestOSResponse.class,
            required = true,
            description = "the ID of the OS Type that best represents the OS of this template.")
    private Long osTypeId;
    @Parameter(name = ApiConstants.PASSWORD_ENABLED,
            type = CommandType.BOOLEAN,
            description = "true if the template supports the password reset feature; default is false")
    private Boolean passwordEnabled;
    @Parameter(name = ApiConstants.REQUIRES_HVM, type = CommandType.BOOLEAN, description = "true if the template requres HVM, false otherwise")
    private Boolean requiresHvm;
    @Deprecated
    @Parameter(name = ApiConstants.URL,
            type = CommandType.STRING,
            description = "DEPRECATED SINCE 5.1.0: Optional, only for baremetal hypervisor. The directory name where template stored on CIFS server")
    private String url;
    @Parameter(name = ApiConstants.TEMPLATE_TAG, type = CommandType.STRING, description = "the tag for this template.")
    private String templateTag;
    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "create template for the project")
    private Long projectId;
    @Parameter(name = ApiConstants.HYPERVISOR, type = CommandType.STRING, description = "the target hypervisor for the template")
    private String hypervisor;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public static String getResultObjectName() {
        return "template";
    }

    public Integer getBits() {
        return bits;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Boolean isFeatured() {
        return featured;
    }

    public Boolean isPublic() {
        return publicTemplate;
    }

    public Long getOsTypeId() {
        return osTypeId;
    }

    public Boolean isPasswordEnabled() {
        return passwordEnabled;
    }

    public Boolean getRequiresHvm() {
        return requiresHvm;
    }

    @Deprecated
    public Long getVmId() {
        return vmId;
    }

    @Deprecated
    public String getUrl() {
        return url;
    }

    public String getTemplateTag() {
        return templateTag;
    }

    public String getHypervisor() {
        return hypervisor;
    }

    public Map getDetails() {
        if (details == null || details.isEmpty()) {
            return null;
        }

        final Collection paramsCollection = details.values();
        final Map params = (Map) paramsCollection.toArray()[0];
        return params;
    }

    public boolean isDynamicallyScalable() {
        return isDynamicallyScalable == null ? false : isDynamicallyScalable;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_TEMPLATE_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating template: " + getTemplateName();
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    public String getTemplateName() {
        return templateName;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.Template;
    }

    @Override
    public void create() throws ResourceAllocationException {
        VirtualMachineTemplate template = null;
        //TemplateOwner should be the caller https://issues.citrite.net/browse/CS-17530
        template = _templateService.createPrivateTemplateRecord(this, _accountService.getAccount(getEntityOwnerId()));
        if (template != null) {
            setEntityId(template.getId());
            setEntityUuid(template.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create a template");
        }
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    @Override
    public void execute() {
        CallContext.current().setEventDetails(
                "Template Id: " + getEntityId() + (getSnapshotId() == null ? " from volume Id: " + getVolumeId() : " from snapshot Id: " + getSnapshotId()));
        VirtualMachineTemplate template = null;
        template = _templateService.createPrivateTemplate(this);

        if (template != null) {
            final List<TemplateResponse> templateResponses = _responseGenerator.createTemplateResponses(ResponseView.Restricted, template.getId(), snapshotId, volumeId, false);
            TemplateResponse response = new TemplateResponse();
            if (templateResponses != null && !templateResponses.isEmpty()) {
                response = templateResponses.get(0);
            }
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create private template");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Long volumeId = getVolumeId();
        final Long snapshotId = getSnapshotId();
        final Account callingAccount = CallContext.current().getCallingAccount();
        if (volumeId != null) {
            final Volume volume = _entityMgr.findById(Volume.class, volumeId);
            if (volume != null) {
                _accountService.checkAccess(callingAccount, SecurityChecker.AccessType.UseEntry, false, volume);
            } else {
                throw new InvalidParameterValueException("Unable to find volume by id=" + volumeId);
            }
        } else {
            final Snapshot snapshot = _entityMgr.findById(Snapshot.class, snapshotId);
            if (snapshot != null) {
                _accountService.checkAccess(callingAccount, SecurityChecker.AccessType.UseEntry, false, snapshot);
            } else {
                throw new InvalidParameterValueException("Unable to find snapshot by id=" + snapshotId);
            }
        }

        if (projectId != null) {
            final Project project = _projectService.getProject(projectId);
            if (project != null) {
                if (project.getState() == Project.State.Active) {
                    final Account projectAccount = _accountService.getAccount(project.getProjectAccountId());
                    _accountService.checkAccess(callingAccount, SecurityChecker.AccessType.UseEntry, false, projectAccount);
                    return project.getProjectAccountId();
                } else {
                    final PermissionDeniedException ex =
                            new PermissionDeniedException("Can't add resources to the project with specified projectId in state=" + project.getState() +
                                    " as it's no longer active");
                    ex.addProxyObject(project.getUuid(), "projectId");
                    throw ex;
                }
            } else {
                throw new InvalidParameterValueException("Unable to find project by id");
            }
        }

        return callingAccount.getId();
    }
}
