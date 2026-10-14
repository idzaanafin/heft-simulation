import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    private static final long DAG_SEED = 2026L;
    private static final int DEFAULT_TASK_COUNT = 100;
    private static final int DEFAULT_VM_COUNT = 80; // 80 VM (10 VM per host untuk 8 physical hosts) sesuai revisi dosen

    /*
     * Number of top-priority tasks
     * displayed in the result.
     */
    private static final int PRIORITY_PREVIEW_COUNT = 10;

    /*
     * Energy model
     */
    private static final double HOST_IDLE_POWER_WATT = 100.0;
    private static final double HOST_MAX_POWER_WATT = 250.0;

    /*
     * Storage bukan bagian dari objective HEFT.
     * Dibuat cukup besar agar tidak menjadi bottleneck.
     */
    private static final long HOST_STORAGE = 1_000_000L;

    /*
     * Ukuran image/storage setiap VM.
     * Harus jauh lebih kecil daripada HOST_STORAGE
     * supaya beberapa VM dapat ditempatkan pada host yang sama.
     */
    private static final long VM_STORAGE = 10_000L;

    private static void validateArguments(
        String workload,
        int taskCount,
        int vmCount
    ) {
        if (!workload.equals("synthetic")
            && !workload.equals("montage")
            && !workload.equals("cybershake")
            && !workload.equals("sipht")) {
            throw new IllegalArgumentException(
                "Workload harus synthetic, montage, cybershake, atau sipht."
            );
        }

        if (taskCount < 2 || vmCount < 1 || vmCount > 160) {
            throw new IllegalArgumentException(
                "Task minimal 2 dan jumlah VM harus berada pada rentang 1-160."
            );
        }
    }

    private static WorkflowDAG createWorkflow(
        String workload,
        int taskCount
    ) throws Exception {
        if (workload.equals("synthetic")) {
            return WorkflowDAG.generateSynthetic(taskCount, DAG_SEED);
        }

        Path datasetsDir = Path.of("src", "main", "resources", "datasets");
        String base = workload.toLowerCase();

        // 1. Cek kecocokan persis dengan taskCount, misal sipht_200.dax atau sipht_100.dax
        Path targetPath = datasetsDir.resolve(base + "_" + taskCount + ".dax");

        // 2. Jika tidak ada, cek file dasar workload.dax
        if (!Files.exists(targetPath)) {
            targetPath = datasetsDir.resolve(base + ".dax");
        }

        // 3. Fallback jika masih belum ada
        if (!Files.exists(targetPath)) {
            targetPath = datasetsDir.resolve(base + "_100.dax");
        }

        if (!Files.exists(targetPath)) {
            throw new IllegalArgumentException(
                "Berkas dataset DAX untuk workload '" + workload + "' tidak ditemukan di " + datasetsDir
            );
        }

        System.out.printf("Memuat dataset DAX: %s (%s, target tasks=%d)%n",
            targetPath.getFileName(), workload, taskCount);

        return WorkflowDatasetLoader.loadDax(targetPath);
    }

    public static void main(String[] args) throws Exception {

        String workload = args.length > 0 ? args[0].toLowerCase() : "synthetic";
        int taskCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TASK_COUNT;
        int vmCount = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_VM_COUNT;

        validateArguments(workload, taskCount, vmCount);

        System.out.println();
        System.out.println("=================================================");
        System.out.println("      HEFT CLOUD WORKFLOW SIMULATION");
        System.out.println("=================================================");

        System.out.println();
        System.out.println("Initialising...");

        Calendar calendar = Calendar.getInstance();

        CloudSim.init(
            1,
            calendar,
            false
        );

        /*
         * =====================================================
         * DATACENTER & HOST ALLOCATION POLICY
         * =====================================================
         * Tentukan alokasi merata VM per host (8 host fisik).
         * Untuk 80 VM -> tepat 10 VM per host.
         */

        int maxVmsPerHost = Math.max(1, (int) Math.ceil((double) vmCount / 8.0));

        List<Datacenter> datacenters =
            new ArrayList<>();

        datacenters.add(
            createDatacenter(
                "Datacenter_0",
                0,
                maxVmsPerHost
            )
        );

        datacenters.add(
            createDatacenter(
                "Datacenter_1",
                1,
                maxVmsPerHost
            )
        );

        printDatacenterConfiguration();

        /*
         * =====================================================
         * WORKFLOW DAG
         * =====================================================
         */

        WorkflowDAG dag = createWorkflow(workload, taskCount);

        /*
         * =====================================================
         * BROKER
         * =====================================================
         */

        WorkflowBroker broker =
            new WorkflowBroker(
                "HEFT_Broker",
                dag
            );

        int brokerId =
            broker.getId();

        /*
         * =====================================================
         * VM CONFIGURATION
         * =====================================================
         */

        List<Vm> vmList =
            createVmList(
                brokerId,
                vmCount
            );

        System.out.println();
        System.out.println("=== VM CONFIGURATION ===");

        for (Vm vm : vmList) {

            System.out.printf(
                "VM #%d | MIPS=%d | PEs=%d | RAM=%d MB | Storage=%d%n",
                vm.getId(),
                (int) vm.getMips(),
                vm.getNumberOfPes(),
                vm.getRam(),
                vm.getSize()
            );
        }

        /*
         * =====================================================
         * HEFT SCHEDULING
         * =====================================================
         */

        long schedulingStart =
            System.nanoTime();

        HEFTScheduler scheduler =
            new HEFTScheduler(
                dag,
                vmList
            );

        scheduler.schedule();

        long schedulingEnd =
            System.nanoTime();

        long schedulingRuntimeMs =
            (schedulingEnd - schedulingStart)
            / 1_000_000L;

        /*
         * =====================================================
         * WORKFLOW SUMMARY
         * =====================================================
         */

        dag.printSummary();

        /*
         * =====================================================
         * HEFT VALIDATION OUTPUT
         * =====================================================
         */

        printHeftValidation(
            scheduler,
            dag,
            vmList
        );

        /*
         * =====================================================
         * SUBMIT VM
         * =====================================================
         */

        broker.submitGuestList(
            vmList
        );

        /*
         * =====================================================
         * CREATE CLOUDLETS
         * =====================================================
         */

        List<Cloudlet> cloudletList =
            new ArrayList<>();

        UtilizationModel utilization =
            new UtilizationModelFull();

        for (WorkflowTask task :
            dag.getTasks()) {

            Cloudlet cloudlet =
                new Cloudlet(
                    task.getId(),
                    task.getInstructionLength(),
                    1,
                    300,
                    300,
                    utilization,
                    utilization,
                    utilization
                );

            cloudlet.setUserId(
                brokerId
            );

            /*
             * VM assignment sudah ditentukan oleh HEFT.
             */
            cloudlet.setGuestId(
                task.getAssignedVmId()
            );

            cloudletList.add(
                cloudlet
            );

            broker.registerCloudlet(
                task,
                cloudlet
            );
        }

        broker.submitCloudletList(
            cloudletList
        );

        /*
         * =====================================================
         * START CLOUDSIM
         * =====================================================
         */

        System.out.println();
        System.out.println("=== STARTING CLOUDSIM ===");

        CloudSim.startSimulation();

        CloudSim.stopSimulation();

        System.out.println();
        System.out.println("Simulation completed.");

        /*
         * =====================================================
         * ACTUAL CLOUDSIM RESULT
         * =====================================================
         */

        double actualMakespan =
            broker.getActualMakespan();

        int completedTasks =
            broker.getCompletedTaskCount();

        /*
         * =====================================================
         * CPU UTILIZATION
         * =====================================================
         */

        double cpuUtilization =
            calculateCpuUtilization(
                dag,
                vmList,
                actualMakespan
            );

        /*
         * =====================================================
         * RAM UTILIZATION
         * =====================================================
         */

        double ramUtilization =
            calculateRamUtilization(
                dag,
                vmList,
                actualMakespan
            );

        /*
         * =====================================================
         * ENERGY
         * =====================================================
         */

        double energy =
            calculateEnergy(
                dag,
                vmList
            );

        /*
         * =====================================================
         * RESULT
         * =====================================================
         */

        SimulationResult result =
            new SimulationResult(
                dag.size(),
                vmList.size(),
                actualMakespan,
                cpuUtilization,
                ramUtilization,
                energy,
                schedulingRuntimeMs
            );

        /*
         * =====================================================
         * PRINT RESULT
         * =====================================================
         */

        System.out.println();
        System.out.println(
            "=== CLOUDSIM WORKFLOW RESULT ==="
        );

        System.out.printf(
            "Completed Tasks : %d / %d%n",
            completedTasks,
            dag.size()
        );

        System.out.printf(
            "HEFT Makespan   : %.6f seconds%n",
            scheduler.getMakespan()
        );

        System.out.printf(
            "CloudSim Actual : %.6f seconds%n",
            actualMakespan
        );

        double makespanDifference =
            actualMakespan
            - scheduler.getMakespan();

        double makespanDifferencePercent =
            scheduler.getMakespan() > 0
                ? (
                    makespanDifference
                    / scheduler.getMakespan()
                ) * 100.0
                : 0.0;

        System.out.printf(
            "Makespan Diff    : %.6f seconds%n",
            makespanDifference
        );

        System.out.printf(
            "Makespan Diff %%  : %.6f%%%n",
            makespanDifferencePercent
        );

        System.out.printf(
            "Scheduling Time : %d ms%n",
            schedulingRuntimeMs
        );

        result.print();

        /*
         * =====================================================
         * BROKER STATUS
         * =====================================================
         */

        System.out.println();

        broker.printWorkflowStatus();

        /*
         * =====================================================
         * PHYSICAL HOST & VM ALLOCATION SUMMARY
         * =====================================================
         */

        printHostVmDistribution(datacenters, vmCount);

        /*
         * =====================================================
         * FINISH
         * =====================================================
         */

        System.out.println();
        System.out.println(
            "================================================="
        );
        System.out.println(
            "             SIMULATION FINISHED"
        );
        System.out.println(
            "================================================="
        );
    }

    /*
     * =========================================================
     * HEFT VALIDATION
     * =========================================================
     */

    private static void printHeftValidation(
        HEFTScheduler scheduler,
        WorkflowDAG dag,
        List<Vm> vms
    ) {

        System.out.println();
        System.out.println(
            "=== HEFT VALIDATION ==="
        );

        /*
         * -----------------------------------------------------
         * PRIORITY ORDER
         * -----------------------------------------------------
         */

        List<WorkflowTask> priority =
            scheduler.getPriorityOrder();

        System.out.println();
        System.out.println(
            "Top HEFT Priority Tasks:"
        );

        int count =
            Math.min(
                PRIORITY_PREVIEW_COUNT,
                priority.size()
            );

        for (int i = 0;
             i < count;
             i++) {

            WorkflowTask task =
                priority.get(i);

            System.out.printf(
                "%2d. Task #%d | rank_u=%.6f | VM=%d | START=%.6f | FINISH=%.6f%n",
                i + 1,
                task.getId(),
                task.getRankUpward(),
                task.getAssignedVmId(),
                task.getStartTime(),
                task.getFinishTime()
            );
        }

        /*
         * -----------------------------------------------------
         * HEFT SCHEDULE
         * -----------------------------------------------------
         */

        System.out.println();
        System.out.println(
            "HEFT Estimated Makespan : "
            + String.format(
                "%.6f seconds",
                scheduler.getMakespan()
            )
        );

        /*
         * -----------------------------------------------------
         * VM UTILIZATION
         * -----------------------------------------------------
         */

        System.out.println();
        System.out.println(
            "VM Task Distribution:"
        );

        for (Vm vm : vms) {

            int taskCount = 0;

            double busyTime = 0.0;

            for (WorkflowTask task :
                dag.getTasks()) {

                if (task.getAssignedVmId()
                    != vm.getId()) {

                    continue;
                }

                taskCount++;

                busyTime +=
                    task.getFinishTime()
                    - task.getStartTime();
            }

            System.out.printf(
                "VM #%d | MIPS=%d | Tasks=%d | Busy Time=%.6f s%n",
                vm.getId(),
                (int) vm.getMips(),
                taskCount,
                busyTime
            );
        }

        /*
         * -----------------------------------------------------
         * DEPENDENCY VALIDATION
         * -----------------------------------------------------
         */

        int invalidDependencies = 0;

        for (WorkflowTask task :
            dag.getTasks()) {

            for (WorkflowTask predecessor :
                task.getPredecessors()) {

                if (predecessor.getFinishTime()
                    > task.getStartTime()) {

                    /*
                     * This check intentionally does not
                     * include communication delay because
                     * communication may occur after the
                     * predecessor finishes.
                     *
                     * A successor starting before predecessor
                     * finish is always invalid.
                     */
                    invalidDependencies++;
                }
            }
        }

        System.out.println();
        System.out.printf(
            "Invalid Dependency Order : %d%n",
            invalidDependencies
        );

        /*
         * -----------------------------------------------------
         * ASSIGNMENT VALIDATION
         * -----------------------------------------------------
         */

        int unassignedTasks = 0;

        for (WorkflowTask task :
            dag.getTasks()) {

            if (task.getAssignedVmId() < 0) {
                unassignedTasks++;
            }
        }

        System.out.printf(
            "Unassigned Tasks          : %d%n",
            unassignedTasks
        );

        /*
         * -----------------------------------------------------
         * VM SCHEDULE DETAILS
         * -----------------------------------------------------
         *
         * Print only for smaller workloads.
         * For 100+ tasks the VM distribution above
         * is more useful than hundreds of lines.
         */
        if (dag.size() <= 20) {

            scheduler.printPriorityOrder();

            scheduler.printSchedule();

            scheduler.printVmSchedules();
        }
    }

    /*
     * =========================================================
     * CREATE DATACENTER
     * =========================================================
     */

    private static Datacenter createDatacenter(
        String name,
        int datacenterIndex,
        int maxVmsPerHost
    ) throws Exception {

        List<Host> hostList =
            new ArrayList<>();

        if (datacenterIndex == 0) {

            hostList.add(
                createHost(
                    0,
                    24,
                    3500,
                    65536,
                    100000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    1,
                    24,
                    3800,
                    65536,
                    100000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    2,
                    24,
                    4200,
                    65536,
                    120000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    3,
                    24,
                    4500,
                    65536,
                    120000,
                    HOST_STORAGE
                )
            );

        } else {

            hostList.add(
                createHost(
                    4,
                    24,
                    3600,
                    98304,
                    100000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    5,
                    24,
                    4000,
                    98304,
                    100000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    6,
                    24,
                    4400,
                    98304,
                    120000,
                    HOST_STORAGE
                )
            );

            hostList.add(
                createHost(
                    7,
                    24,
                    5000,
                    98304,
                    160000,
                    HOST_STORAGE
                )
            );
        }

        String architecture =
            "x86";

        String os =
            "Linux";

        String vmm =
            "Xen";

        double timeZone =
            7.0;

        double cost =
            0.10;

        double costPerMem =
            0.01;

        double costPerStorage =
            0.001;

        double costPerBw =
            0.01;

        DatacenterCharacteristics characteristics =
            new DatacenterCharacteristics(
                architecture,
                os,
                vmm,
                hostList,
                timeZone,
                cost,
                costPerMem,
                costPerStorage,
                costPerBw
            );

        return new Datacenter(
            name,
            characteristics,
            new VmAllocationPolicyUniform(
                hostList,
                maxVmsPerHost
            ),
            new ArrayList<Storage>(),
            0
        );
    }

    /*
     * =========================================================
     * CREATE HOST
     * =========================================================
     */

    private static Host createHost(
        int id,
        int pes,
        int mips,
        int ram,
        long bw,
        long storage
    ) {

        List<Pe> peList =
            new ArrayList<>();

        for (int i = 0;
             i < pes;
             i++) {

            peList.add(
                new Pe(
                    i,
                    new PeProvisionerSimple(
                        mips
                    )
                )
            );
        }

        return new Host(
            id,
            new RamProvisionerSimple(
                ram
            ),
            new BwProvisionerSimple(
                bw
            ),
            storage,
            peList,
            new org.cloudbus.cloudsim.VmSchedulerTimeShared(
                peList
            )
        );
    }

    /*
     * =========================================================
     * CREATE VM LIST
     * =========================================================
     */

    private static List<Vm> createVmList(
        int brokerId,
        int vmCount
    ) {

        List<Vm> vmList =
            new ArrayList<>();

        /*
         * Heterogeneous VM MIPS.
         */
        int[] mips = {
            1000,
            1200,
            1500,
            1800,
            2000,
            2200,
            2500,
            2800,
            3000,
            3500
        };

        /*
         * Heterogeneous number of PEs.
         */
        int[] pes = {
            1,
            1,
            1,
            1,
            2,
            2,
            2,
            2,
            2,
            4
        };

        /*
         * Heterogeneous RAM.
         */
        int[] ram = {
            2048,
            2048,
            2048,
            2048,
            4096,
            4096,
            4096,
            8192,
            8192,
            8192
        };

        for (int i = 0;
             i < vmCount;
             i++) {

            int profile = i % mips.length;

            Vm vm =
                new Vm(
                    i,
                    brokerId,
                    mips[profile],
                    pes[profile],
                    ram[profile],
                    10000,
                    VM_STORAGE,
                    "Xen",
                    new CloudletSchedulerSpaceShared()
                );

            vmList.add(
                vm
            );
        }

        return vmList;
    }

    /*
     * =========================================================
     * PRINT DATACENTER CONFIGURATION
     * =========================================================
     */

    private static void printDatacenterConfiguration() {

        System.out.println();
        System.out.println(
            "=== DATACENTER CONFIGURATION ==="
        );

        System.out.println(
            "Datacenter_0 (4 Hosts, 64 GB RAM/Host):"
        );

        printHost(
            0,
            24,
            84000,
            65536,
            100000
        );

        printHost(
            1,
            24,
            91200,
            65536,
            100000
        );

        printHost(
            2,
            24,
            100800,
            65536,
            120000
        );

        printHost(
            3,
            24,
            108000,
            65536,
            120000
        );

        System.out.println(
            "Datacenter_1 (4 Hosts, 96 GB RAM/Host):"
        );

        printHost(
            4,
            24,
            86400,
            98304,
            100000
        );

        printHost(
            5,
            24,
            96000,
            98304,
            100000
        );

        printHost(
            6,
            24,
            105600,
            98304,
            120000
        );

        printHost(
            7,
            24,
            120000,
            98304,
            160000
        );
    }

    private static void printHostVmDistribution(
        List<Datacenter> datacenters,
        int vmCount
    ) {
        System.out.println();
        System.out.println("=================================================");
        System.out.println("   PHYSICAL HOST & VM ALLOCATION SUMMARY");
        System.out.println("=================================================");
        int totalAllocated = 0;
        for (Datacenter dc : datacenters) {
            System.out.println(dc.getName() + ":");
            for (Host host : dc.<Host>getHostList()) {
                int count = host.getVmList().size();
                totalAllocated += count;
                List<Integer> vmIds = new ArrayList<>();
                for (Vm vm : host.<Vm>getVmList()) {
                    vmIds.add(vm.getId());
                }
                String idRange = vmIds.isEmpty() ? "None"
                    : (vmIds.size() == 1 ? "VM #" + vmIds.get(0)
                    : "VM #" + vmIds.get(0) + " to #" + vmIds.get(vmIds.size() - 1));
                System.out.printf(
                    "  Host #%d | PEs=%d | RAM=%d MB -> %d VMs (%s)%n",
                    host.getId(),
                    host.getNumberOfPes(),
                    host.getRam(),
                    count,
                    idRange
                );
            }
        }
        System.out.println("-------------------------------------------------");
        System.out.printf(
            "Total: 8 Physical Hosts, %d VMs Allocated (Target: %d VMs)%n",
            totalAllocated,
            vmCount
        );
        System.out.println("=================================================");
    }

    private static void printHost(
        int id,
        int pes,
        int totalMips,
        int ram,
        long bw
    ) {

        System.out.printf(
            "Host #%d | PE=%d | Total MIPS=%d | RAM=%d MB | BW=%d%n",
            id,
            pes,
            totalMips,
            ram,
            bw
        );
    }

    /*
     * =========================================================
     * CPU UTILIZATION
     * =========================================================
     */

    private static double calculateCpuUtilization(
        WorkflowDAG dag,
        List<Vm> vms,
        double makespan
    ) {

        if (makespan <= 0) {
            return 0.0;
        }

        double totalBusyTime =
            0.0;

        double totalCapacity =
            0.0;

        for (WorkflowTask task :
            dag.getTasks()) {

            Vm vm =
                findVm(
                    vms,
                    task.getAssignedVmId()
                );

            if (vm == null) {
                continue;
            }

            double executionTime =
                (double)
                task.getInstructionLength()
                / vm.getMips();

            totalBusyTime +=
                executionTime
                * vm.getNumberOfPes();
        }

        for (Vm vm :
            vms) {

            totalCapacity +=
                makespan
                * vm.getNumberOfPes();
        }

        if (totalCapacity <= 0) {
            return 0.0;
        }

        return Math.min(
            100.0,
            (
                totalBusyTime
                / totalCapacity
            ) * 100.0
        );
    }

    /*
     * =========================================================
     * RAM UTILIZATION
     * =========================================================
     */

    private static double calculateRamUtilization(
        WorkflowDAG dag,
        List<Vm> vms,
        double makespan
    ) {

        if (makespan <= 0) {
            return 0.0;
        }

        double taskRamTime =
            0.0;

        double totalRamTime =
            0.0;

        for (WorkflowTask task :
            dag.getTasks()) {

            Vm vm =
                findVm(
                    vms,
                    task.getAssignedVmId()
                );

            if (vm == null) {
                continue;
            }

            double executionTime =
                (double)
                task.getInstructionLength()
                / vm.getMips();

            taskRamTime +=
                vm.getRam()
                * executionTime;
        }

        for (Vm vm :
            vms) {

            totalRamTime +=
                vm.getRam()
                * makespan;
        }

        if (totalRamTime <= 0) {
            return 0.0;
        }

        return Math.min(
            100.0,
            (
                taskRamTime
                / totalRamTime
            ) * 100.0
        );
    }

    /*
     * =========================================================
     * ENERGY
     * =========================================================
     */

    private static double calculateEnergy(
        WorkflowDAG dag,
        List<Vm> vms
    ) {

        double totalEnergy =
            0.0;

        for (WorkflowTask task :
            dag.getTasks()) {

            Vm vm =
                findVm(
                    vms,
                    task.getAssignedVmId()
                );

            if (vm == null) {
                continue;
            }

            double executionTime =
                (double)
                task.getInstructionLength()
                / vm.getMips();

            /*
             * Analytical full-load assumption.
             */
            double utilization =
                1.0;

            double power =
                HOST_IDLE_POWER_WATT
                + utilization
                * (
                    HOST_MAX_POWER_WATT
                    - HOST_IDLE_POWER_WATT
                );

            totalEnergy +=
                power
                * executionTime;
        }

        return totalEnergy;
    }

    /*
     * =========================================================
     * FIND VM
     * =========================================================
     */

    private static Vm findVm(
        List<Vm> vms,
        int vmId
    ) {

        for (Vm vm :
            vms) {

            if (vm.getId() == vmId) {
                return vm;
            }
        }

        return null;
    }
}