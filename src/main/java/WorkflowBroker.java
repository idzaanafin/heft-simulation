import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkflowBroker extends DatacenterBroker {

    private final WorkflowDAG dag;

    private final Map<Integer, Cloudlet> taskToCloudlet;
    private final Map<Integer, WorkflowTask> cloudletToTask;

    private final Set<Integer> completedTasks;
    private final Set<Integer> submittedTasks;

    private double actualMakespan = 0.0;
    private int completedTaskCount = 0;

    public WorkflowBroker(
        String name,
        WorkflowDAG dag
    ) throws Exception {

        super(name);

        this.dag = dag;

        this.taskToCloudlet = new HashMap<>();
        this.cloudletToTask = new HashMap<>();

        this.completedTasks = new HashSet<>();
        this.submittedTasks = new HashSet<>();
    }

    /**
     * Menghubungkan WorkflowTask dengan Cloudlet CloudSim.
     */
    public void registerCloudlet(
        WorkflowTask task,
        Cloudlet cloudlet
    ) {

        taskToCloudlet.put(task.getId(), cloudlet);
        cloudletToTask.put(cloudlet.getCloudletId(), task);
    }

    /**
     * Dipanggil CloudSim setelah VM berhasil dibuat.
     *
     * Berbeda dengan DatacenterBroker default yang langsung
     * mengirim seluruh cloudlet, broker ini hanya mengirim
     * entry tasks terlebih dahulu.
     */
    @Override
    protected void submitCloudlets() {

        for (WorkflowTask task : dag.getEntryTasks()) {

            Cloudlet cloudlet =
                taskToCloudlet.get(task.getId());

            if (cloudlet == null) {
                continue;
            }

            submitReadyCloudlet(cloudlet);
        }
    }

    /**
     * Mengirim cloudlet ke datacenter sesuai VM hasil HEFT.
     */
    private void submitReadyCloudlet(Cloudlet cloudlet) {

        WorkflowTask task =
            cloudletToTask.get(cloudlet.getCloudletId());

        if (task == null) {
            return;
        }

        int taskId = task.getId();

        if (submittedTasks.contains(taskId)) {
            return;
        }

        int vmId = task.getAssignedVmId();

        if (vmId < 0) {
            throw new IllegalStateException(
                "Task #" + taskId + " belum memiliki VM."
            );
        }

        Integer datacenterId =
            getVmsToDatacentersMap().get(vmId);

        if (datacenterId == null) {

            throw new IllegalStateException(
                "VM #" + vmId
                + " belum terdaftar pada Datacenter."
            );
        }

        /*
         * Pastikan cloudlet menggunakan VM yang dipilih HEFT.
         */
        cloudlet.setGuestId(vmId);

        submittedTasks.add(taskId);

        double communicationDelay = calculateCommunicationDelay(task);

        send(
            datacenterId,
            communicationDelay,
            CloudActionTags.CLOUDLET_SUBMIT,
            cloudlet
        );
    }

    private double calculateCommunicationDelay(WorkflowTask task) {
        double delay = 0.0;
        Vm destinationVm = findVm(task.getAssignedVmId());

        for (WorkflowTask predecessor : task.getPredecessors()) {
            Vm sourceVm = findVm(predecessor.getAssignedVmId());
            if (sourceVm.getId() != destinationVm.getId()) {
                delay = Math.max(
                    delay,
                    predecessor.getCommunicationCostTo(task)
                        / HEFTScheduler.NETWORK_BANDWIDTH_MBPS
                );
            }
        }

        return delay;
    }

    private Vm findVm(int vmId) {
        for (org.cloudbus.cloudsim.core.GuestEntity guest : getGuestList()) {
            if (guest instanceof Vm vm && vm.getId() == vmId) {
                return vm;
            }
        }
        throw new IllegalStateException("VM #" + vmId + " tidak ditemukan.");
    }

    /**
     * Menangani cloudlet yang selesai.
     */
    @Override
    protected void processCloudletReturn(SimEvent ev) {

        Cloudlet cloudlet =
            (Cloudlet) ev.getData();

        WorkflowTask task =
            cloudletToTask.get(cloudlet.getCloudletId());

        if (task == null) {
            return;
        }

        int taskId = task.getId();

        completedTasks.add(taskId);
        completedTaskCount++;

        /*
         * Catat waktu aktual berdasarkan CloudSim.
         */
        double finishTime =
            cloudlet.getFinishTime();

        if (finishTime > actualMakespan) {
            actualMakespan = finishTime;
        }

        /*
         * Simpan waktu aktual ke WorkflowTask.
         */
        task.setStartTime(
            cloudlet.getExecStartTime()
        );

        task.setFinishTime(
            cloudlet.getFinishTime()
        );

        /*
         * Periksa seluruh successor.
         */
        for (WorkflowTask successor : task.getSuccessors()) {

            if (submittedTasks.contains(successor.getId())) {
                continue;
            }

            if (allPredecessorsCompleted(successor)) {

                Cloudlet successorCloudlet =
                    taskToCloudlet.get(successor.getId());

                if (successorCloudlet != null) {

                    submitReadyCloudlet(
                        successorCloudlet
                    );
                }
            }
        }

        /*
         * Kalau semua task selesai, catat makespan.
         */
        if (completedTaskCount == dag.size()) {

            actualMakespan =
                Math.max(
                    actualMakespan,
                    cloudlet.getFinishTime()
                );
        }
    }

    /**
     * Mengecek apakah seluruh predecessor sebuah task
     * sudah selesai dieksekusi.
     */
    private boolean allPredecessorsCompleted(
        WorkflowTask task
    ) {

        for (WorkflowTask predecessor :
            task.getPredecessors()) {

            if (!completedTasks.contains(
                predecessor.getId()
            )) {
                return false;
            }
        }

        return true;
    }

    /**
     * Mengembalikan makespan aktual dari CloudSim.
     */
    public double getActualMakespan() {
        return actualMakespan;
    }

    /**
     * Mengembalikan jumlah task yang selesai.
     */
    public int getCompletedTaskCount() {
        return completedTaskCount;
    }

    /**
     * Mengembalikan cloudlet berdasarkan task ID.
     */
    public Cloudlet getCloudletForTask(int taskId) {
        return taskToCloudlet.get(taskId);
    }

    /**
     * Mengembalikan task berdasarkan cloudlet ID.
     */
    public WorkflowTask getTaskForCloudlet(int cloudletId) {
        return cloudletToTask.get(cloudletId);
    }

    /**
     * Print status workflow.
     */
    public void printWorkflowStatus() {

        System.out.println();
        System.out.println("=== WORKFLOW BROKER STATUS ===");

        System.out.printf(
            "Submitted Tasks : %d / %d%n",
            submittedTasks.size(),
            dag.size()
        );

        System.out.printf(
            "Completed Tasks : %d / %d%n",
            completedTaskCount,
            dag.size()
        );

        System.out.printf(
            "Actual Makespan : %.6f seconds%n",
            actualMakespan
        );
    }
}