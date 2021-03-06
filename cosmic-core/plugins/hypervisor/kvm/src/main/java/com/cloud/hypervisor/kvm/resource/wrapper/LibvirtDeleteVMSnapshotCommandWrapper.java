package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.DeleteVMSnapshotAnswer;
import com.cloud.agent.api.DeleteVMSnapshotCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.storage.KvmPhysicalDisk;
import com.cloud.hypervisor.kvm.storage.KvmStoragePoolManager;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.Volume;
import com.cloud.storage.to.PrimaryDataStoreTO;
import com.cloud.storage.to.VolumeObjectTO;
import com.cloud.utils.script.Script;

import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainSnapshot;
import org.libvirt.LibvirtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles =  DeleteVMSnapshotCommand.class)
public final class LibvirtDeleteVMSnapshotCommandWrapper extends CommandWrapper<DeleteVMSnapshotCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = LoggerFactory.getLogger(LibvirtDeleteVMSnapshotCommandWrapper.class);

    @Override
    public Answer execute(final DeleteVMSnapshotCommand cmd, final LibvirtComputingResource libvirtComputingResource) {
        final String vmName = cmd.getVmName();

        final KvmStoragePoolManager storagePoolMgr = libvirtComputingResource.getStoragePoolMgr();
        Domain dm = null;
        DomainSnapshot snapshot = null;
        try {
            final LibvirtUtilitiesHelper libvirtUtilitiesHelper = libvirtComputingResource.getLibvirtUtilitiesHelper();
            final Connect conn = libvirtUtilitiesHelper.getConnection();
            dm = libvirtComputingResource.getDomain(conn, vmName);

            snapshot = dm.snapshotLookupByName(cmd.getTarget().getSnapshotName());

            snapshot.delete(0); // only remove this snapshot, not children

            return new DeleteVMSnapshotAnswer(cmd, cmd.getVolumeTOs());
        } catch (final LibvirtException e) {
            final String msg = " Delete VM snapshot failed due to " + e.toString();

            if (dm == null) {
                s_logger.debug("Can not find running vm: " + vmName + ", now we are trying to delete the vm snapshot using qemu-img if the format of root volume is QCOW2");
                VolumeObjectTO rootVolume = null;
                for (VolumeObjectTO volume: cmd.getVolumeTOs()) {
                    if (volume.getVolumeType() == Volume.Type.ROOT) {
                        rootVolume = volume;
                        break;
                    }
                }
                if (rootVolume != null && ImageFormat.QCOW2.equals(rootVolume.getFormat())) {
                    final PrimaryDataStoreTO primaryStore = (PrimaryDataStoreTO) rootVolume.getDataStore();
                    final KvmPhysicalDisk rootDisk = storagePoolMgr.getPhysicalDisk(primaryStore.getPoolType(), primaryStore.getUuid(), rootVolume.getPath());
                    final String command = "qemu-img snapshot -l " + rootDisk.getPath() + " | tail -n +3 | awk -F ' ' '{print $2}' | grep ^" + cmd.getTarget().getSnapshotName() + "$";
                    final String qemu_img_snapshot = Script.runSimpleBashScript(command);
                    if (qemu_img_snapshot == null) {
                        s_logger.info("Cannot find snapshot " + cmd.getTarget().getSnapshotName() + " in file " + rootDisk.getPath() + ", return true");
                        return new DeleteVMSnapshotAnswer(cmd, cmd.getVolumeTOs());
                    }
                    int result = Script.runSimpleBashScriptForExitValue("qemu-img snapshot -d " + cmd.getTarget().getSnapshotName() + " " + rootDisk.getPath());
                    if (result != 0) {
                        return new DeleteVMSnapshotAnswer(cmd, false, "Delete VM Snapshot Failed due to can not remove snapshot from image file " +
                                rootDisk.getPath()  + " : " + result);
                    } else {
                        return new DeleteVMSnapshotAnswer(cmd, cmd.getVolumeTOs());
                    }
                }
            } else if (snapshot == null) {
                s_logger.debug("Can not find vm snapshot " + cmd.getTarget().getSnapshotName() + " on vm: " + vmName + ", return true");
                return new DeleteVMSnapshotAnswer(cmd, cmd.getVolumeTOs());
            }

            s_logger.warn(msg, e);
            return new DeleteVMSnapshotAnswer(cmd, false, msg);
        } finally {
            if (dm != null) {
                try {
                    dm.free();
                } catch (final LibvirtException l) {
                    s_logger.trace("Ignoring libvirt error.", l);
                }
            }
        }
    }
}