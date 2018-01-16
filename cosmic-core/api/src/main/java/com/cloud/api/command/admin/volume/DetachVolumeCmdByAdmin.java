package com.cloud.api.command.admin.volume;

import com.cloud.api.APICommand;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.user.volume.DetachVolumeCmd;
import com.cloud.api.response.VolumeResponse;
import com.cloud.context.CallContext;
import com.cloud.storage.Volume;
import com.cloud.vm.VirtualMachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "detachVolume", group = "Volume", description = "Detaches a disk volume from a virtual machine.", responseObject = VolumeResponse.class, responseView = ResponseView.Full,
        entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class DetachVolumeCmdByAdmin extends DetachVolumeCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(DetachVolumeCmdByAdmin.class.getName());

    @Override
    public void execute() {
        CallContext.current().setEventDetails("Volume Id: " + getId() + " VmId: " + getVirtualMachineId());
        final Volume result = _volumeService.detachVolumeFromVM(this);
        if (result != null) {
            final VolumeResponse response = _responseGenerator.createVolumeResponse(ResponseView.Full, result);
            response.setResponseName("volume");
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to detach volume");
        }
    }
}
